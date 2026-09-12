package net.subsloth.core.media.download

import app.cash.turbine.test
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeString
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import net.subsloth.core.domain.port.ConnectivityPort
import net.subsloth.core.model.download.DownloadFailureReason
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.database.dao.DownloadedMediaDao
import net.subsloth.database.dao.DownloadedSubtitleDao
import net.subsloth.database.entity.DownloadedMediaEntity
import net.subsloth.database.entity.DownloadedSubtitleEntity
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readBytes

private class FakeDownloadedMediaDao : DownloadedMediaDao {
    private val state = MutableStateFlow<List<DownloadedMediaEntity>>(emptyList())

    override fun getAll(): Flow<List<DownloadedMediaEntity>> = state

    override suspend fun getById(id: Long): DownloadedMediaEntity? = state.value.firstOrNull { it.id == id }

    override fun getCompleted(): Flow<List<DownloadedMediaEntity>> =
        MutableStateFlow(state.value.filter { it.status == "completed" })

    override suspend fun getByContent(contentId: String, mediaType: String): DownloadedMediaEntity? =
        state.value.firstOrNull { it.contentId == contentId && it.mediaType == mediaType }

    override suspend fun upsert(entity: DownloadedMediaEntity) {
        state.value = state.value.filterNot { it.id == entity.id } + entity
    }

    override suspend fun delete(entity: DownloadedMediaEntity) {
        state.value = state.value.filterNot { it.id == entity.id }
    }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }

    override suspend fun countCompleted(): Int = state.value.count { it.status == "completed" }

    fun set(entities: List<DownloadedMediaEntity>) {
        state.value = entities
    }
}

private class FakeDownloadedSubtitleDao : DownloadedSubtitleDao {
    val rows = mutableListOf<DownloadedSubtitleEntity>()

    override fun getForDownload(downloadId: Long): Flow<List<DownloadedSubtitleEntity>> =
        MutableStateFlow(rows.filter { it.downloadId == downloadId })

    override fun getPending(): Flow<List<DownloadedSubtitleEntity>> =
        MutableStateFlow(rows.filter { it.localFilePath.isBlank() })

    override suspend fun upsert(entity: DownloadedSubtitleEntity) {
        rows.removeAll { it.id == entity.id }
        rows += entity
    }

    override suspend fun delete(entity: DownloadedSubtitleEntity) {
        rows.remove(entity)
    }

    override suspend fun deleteForDownload(downloadId: Long) {
        rows.removeAll { it.downloadId == downloadId }
    }
}

private class FakeConnectivity(var metered: Boolean = false) : ConnectivityPort {
    override fun isOnline(): Boolean = true
    override fun isMetered(): Boolean = metered
}

private fun entity(id: Long = 1, status: String = "queued") = DownloadedMediaEntity(
    id = id,
    contentId = "1",
    mediaType = "movie",
    localFilePath = "",
    sizeBytes = 0,
    status = status,
    selectedQuality = "720p",
    downloadedAtEpochSeconds = null,
)

private typealias MockRoute = suspend MockRequestHandleScope.(String) -> HttpResponseData

class DownloadTransferCoordinatorTest {

    private val tempDir = Files.createTempDirectory("coordinator-test")
    private val body = "download-payload-123456"

    private fun coordinator(
        dao: FakeDownloadedMediaDao,
        subtitleDao: FakeDownloadedSubtitleDao = FakeDownloadedSubtitleDao(),
        resolveSubtitles: suspend (Media.MediaId) -> List<Subtitle> = { emptyList() },
        connectivity: FakeConnectivity = FakeConnectivity(),
        resolver: suspend (Media.MediaId, String?) -> DownloadTarget? = { _, _ ->
            DownloadTarget("https://cdn.example.com/file.mp4", "mp4")
        },
        engineHandler: MockRoute = { _ ->
            respond(content = ByteReadChannel(body), status = HttpStatusCode.OK)
        },
    ): DownloadTransferCoordinator = DownloadTransferCoordinator(
        downloadedMediaDao = dao,
        downloadedSubtitleDao = subtitleDao,
        store = DesktopDownloadStore(tempDir.toFile()),
        transferer = DownloadTransferer(
            client = HttpClient(MockEngine { engineHandler(it.url.toString()) }),
            store = DesktopDownloadStore(tempDir.toFile()),
        ),
        connectivityChecker = connectivity,
        clock = kotlin.time.Clock.System,
        resolveDownloadUrl = resolver,
        resolveSubtitles = resolveSubtitles,
    )

    @Test
    fun `queued download transfers bytes and completes`() = runTest {
        val dao = FakeDownloadedMediaDao().apply { set(listOf(entity())) }
        val coordinator = coordinator(dao)

        coordinator.processQueued()

        val stored = requireNotNull(dao.getById(1))
        assertThat(stored.status).isEqualTo("completed")
        assertThat(stored.sizeBytes).isEqualTo(body.length.toLong())
        assertThat(stored.localFilePath).isNotEmpty()
        assertThat(stored.downloadedAtEpochSeconds).isNotNull()
        val finalFile = tempDir.resolve(stored.localFilePath)
        assertThat(finalFile.exists()).isTrue()
        assertThat(String(finalFile.readBytes())).isEqualTo(body)
    }

    @Test
    fun `subtitle bytes transfer after the media completes`() = runTest {
        val dao = FakeDownloadedMediaDao().apply { set(listOf(entity())) }
        val subtitleDao = FakeDownloadedSubtitleDao().apply {
            rows += DownloadedSubtitleEntity(
                id = 1,
                downloadId = 1,
                language = "en",
                source = null,
                format = null,
                localFilePath = "",
            )
        }
        val coordinator = coordinator(
            dao,
            subtitleDao = subtitleDao,
            resolveSubtitles = {
                listOf(
                    Subtitle(
                        language = LanguageCode("en"),
                        languageDisplayName = "English",
                        url = null,
                        downloadUrl = "https://cdn.example.com/en.srt",
                        format = SubtitleFormat.SRT,
                    ),
                )
            },
        )

        coordinator.processQueued()

        val row = requireNotNull(subtitleDao.rows.firstOrNull())
        assertThat(row.localFilePath).isNotEmpty()
        assertThat(row.format).isEqualTo("SRT")
        assertThat(tempDir.resolve(row.localFilePath).exists()).isTrue()
    }

    @Test
    fun `subtitle queued after the media completed still transfers`() = runTest {
        val dao = FakeDownloadedMediaDao().apply {
            set(listOf(entity(status = "completed").copy(localFilePath = "1/abc.mp4")))
        }
        val subtitleDao = FakeDownloadedSubtitleDao().apply {
            rows += DownloadedSubtitleEntity(
                id = 1,
                downloadId = 1,
                language = "en",
                source = null,
                format = null,
                localFilePath = "",
            )
        }
        val coordinator = coordinator(
            dao,
            subtitleDao = subtitleDao,
            resolveSubtitles = {
                listOf(
                    Subtitle(
                        language = LanguageCode("en"),
                        languageDisplayName = "English",
                        url = null,
                        downloadUrl = "https://cdn.example.com/en.srt",
                        format = SubtitleFormat.SRT,
                    ),
                )
            },
        )

        val processed = coordinator.transferPendingSubtitles()

        assertThat(processed).isEqualTo(1)
        assertThat(requireNotNull(subtitleDao.rows.firstOrNull()).localFilePath).isNotEmpty()
    }

    @Test
    fun `metered network defers the download as paused`() = runTest {
        val dao = FakeDownloadedMediaDao().apply { set(listOf(entity())) }
        val coordinator = coordinator(dao, connectivity = FakeConnectivity(metered = true))

        coordinator.processQueued()

        assertThat(requireNotNull(dao.getById(1)).status).isEqualTo("paused")
    }

    @Test
    fun `unresolvable download url fails the item`() = runTest {
        val dao = FakeDownloadedMediaDao().apply { set(listOf(entity())) }
        val coordinator = coordinator(dao, resolver = { _, _ -> null })

        coordinator.processQueued()

        assertThat(requireNotNull(dao.getById(1)).status).isEqualTo("failed")
    }

    @Test
    fun `unsuccessful http response fails the item`() = runTest {
        val dao = FakeDownloadedMediaDao().apply { set(listOf(entity())) }
        val coordinator = coordinator(
            dao,
            engineHandler = { _ -> respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound) },
        )

        coordinator.processQueued()

        assertThat(requireNotNull(dao.getById(1)).status).isEqualTo("failed")
    }

    @Test
    fun `pausing mid-transfer aborts without overwriting the paused status`() = runBlocking {
        val dao = FakeDownloadedMediaDao().apply { set(listOf(entity())) }
        val channel = ByteChannel(autoFlush = true)
        val feeder = launch {
            channel.writeString(body)
            channel.flush()
            channel.writeString("more")
            channel.flush()
            channel.close()
        }
        val coordinator = coordinator(
            dao,
            engineHandler = { _ -> respond(content = channel, status = HttpStatusCode.OK) },
        )

        val processing = async { coordinator.processQueued() }
        // Flip the status mid-stream (before or between chunk reads — any
        // interleaving aborts: every progress callback re-checks the
        // status and aborts once it is no longer active).
        dao.set(listOf(entity(status = "paused")))

        withTimeout(10_000) { processing.await() }

        assertThat(requireNotNull(dao.getById(1)).status).isEqualTo("paused")
        assertThat(tempDir.resolve("1").toFile().listFiles()?.any { it.name.endsWith(".part") } ?: false)
            .isFalse()
        feeder.join()
    }

    @Test
    fun `unknown media type fails the item`() = runTest {
        val dao = FakeDownloadedMediaDao().apply { set(listOf(entity().copy(mediaType = "trailer"))) }
        val coordinator = coordinator(dao)

        coordinator.processQueued()

        assertThat(requireNotNull(dao.getById(1)).status).isEqualTo("failed")
    }

    @Test
    fun `non-numeric content id fails the item`() = runTest {
        val dao = FakeDownloadedMediaDao().apply { set(listOf(entity().copy(contentId = "not-a-number"))) }
        val coordinator = coordinator(dao)

        coordinator.processQueued()

        assertThat(requireNotNull(dao.getById(1)).status).isEqualTo("failed")
    }

    @Test
    fun `failure events carry the download failure reason`() = runTest {
        val dao = FakeDownloadedMediaDao().apply { set(listOf(entity())) }
        val coordinator = coordinator(dao, resolver = { _, _ -> null })

        coordinator.events.test {
            coordinator.processQueued()
            val event = awaitItem()
            assertThat(event).isInstanceOf(TransferEvent.Failed::class.java)
            assertThat((event as TransferEvent.Failed).reason).isEqualTo(DownloadFailureReason.DownloadFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
