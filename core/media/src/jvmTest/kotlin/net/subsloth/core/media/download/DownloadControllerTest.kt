package net.subsloth.core.media.download

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.subsloth.core.domain.port.ConnectivityPort
import net.subsloth.core.domain.port.DownloadCommandOutcome
import net.subsloth.core.domain.port.StoragePort
import net.subsloth.core.domain.port.SubtitleEnqueueOutcome
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Media
import net.subsloth.database.dao.DownloadedMediaDao
import net.subsloth.database.dao.DownloadedSubtitleDao
import net.subsloth.database.dao.OfflineDisplayMetadataDao
import net.subsloth.database.entity.DownloadedMediaEntity
import net.subsloth.database.entity.DownloadedSubtitleEntity
import net.subsloth.database.entity.OfflineDisplayMetadataEntity
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

private val movieId = Media.MediaId.Movie(MovieId(1))

private class ControllerMediaDao : DownloadedMediaDao {
    private val state = MutableStateFlow<List<DownloadedMediaEntity>>(emptyList())
    private var nextId = 1L

    override fun getAll(): Flow<List<DownloadedMediaEntity>> = state

    override suspend fun getById(id: Long): DownloadedMediaEntity? = state.value.firstOrNull { it.id == id }

    override fun getCompleted(): Flow<List<DownloadedMediaEntity>> =
        MutableStateFlow(state.value.filter { it.status == "completed" })

    override suspend fun getByContent(contentId: String, mediaType: String): DownloadedMediaEntity? =
        state.value.firstOrNull { it.contentId == contentId && it.mediaType == mediaType }

    override suspend fun upsert(entity: DownloadedMediaEntity) {
        val id = if (entity.id == 0L) nextId++ else entity.id
        state.value = state.value.filterNot { it.id == id } + entity.copy(id = id)
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

    fun entity(id: Long): DownloadedMediaEntity? = state.value.firstOrNull { it.id == id }
}

private class ControllerSubtitleDao : DownloadedSubtitleDao {
    val rows = mutableListOf<DownloadedSubtitleEntity>()

    override fun getForDownload(downloadId: Long): Flow<List<DownloadedSubtitleEntity>> =
        MutableStateFlow(rows.filter { it.downloadId == downloadId })

    override suspend fun upsert(entity: DownloadedSubtitleEntity) {
        rows += entity
    }

    override suspend fun delete(entity: DownloadedSubtitleEntity) {
        rows.remove(entity)
    }

    override suspend fun deleteForDownload(downloadId: Long) {
        rows.removeAll { it.downloadId == downloadId }
    }
}

private class ControllerMetadataDao : OfflineDisplayMetadataDao {
    val rows = mutableListOf<OfflineDisplayMetadataEntity>()

    override fun getAll(): Flow<List<OfflineDisplayMetadataEntity>> = MutableStateFlow(rows)

    override suspend fun getByContentId(contentId: String): OfflineDisplayMetadataEntity? =
        rows.firstOrNull { it.contentId == contentId }

    override suspend fun upsert(entity: OfflineDisplayMetadataEntity) {
        rows.removeAll { it.contentId == entity.contentId }
        rows += entity
    }

    override suspend fun delete(entity: OfflineDisplayMetadataEntity) {
        rows.remove(entity)
    }

    override suspend fun deleteAll() {
        rows.clear()
    }
}

private class ControllerFileStore : DownloadFileStore {
    val deleted = mutableListOf<OfflineRelativePath>()

    override fun deleteMedia(localPath: OfflineRelativePath): Boolean {
        deleted += localPath
        return true
    }
}

private class ControllerStorage(var available: Long = 10L * 1024 * 1024 * 1024, var reserve: Long = 0L) : StoragePort {
    override fun availableBytes(): Long = available

    override fun totalBytes(): Long = 20L * 1024 * 1024 * 1024

    override fun reserveBytes(): Long = reserve
}

private class ControllerConnectivity(var metered: Boolean = false) : ConnectivityPort {
    override fun isOnline(): Boolean = true

    override fun isMetered(): Boolean = metered
}

private class Fixtures(
    val controller: DownloadController,
    val dao: ControllerMediaDao,
    val subtitles: ControllerSubtitleDao,
    val metadata: ControllerMetadataDao,
    val store: ControllerFileStore,
)

private fun entity(
    id: Long = 1,
    contentId: String = "1",
    mediaType: String = "movie",
    status: String = "queued",
    localFilePath: String = "",
    sizeBytes: Long = 0,
    quality: String? = "720p",
) = DownloadedMediaEntity(
    id = id,
    contentId = contentId,
    mediaType = mediaType,
    localFilePath = localFilePath,
    sizeBytes = sizeBytes,
    status = status,
    selectedQuality = quality,
    downloadedAtEpochSeconds = null,
)

private fun metadata(contentId: String) = OfflineDisplayMetadataEntity(
    contentId = contentId,
    title = "Title",
    posterCacheKey = null,
    backdropCacheKey = null,
    episodeTitle = null,
    seasonNumber = null,
    episodeNumber = null,
    effectiveQuality = null,
    subtitleLanguages = null,
    durationSeconds = null,
    localProgressSeconds = null,
)

private fun fixtures(
    metered: Boolean = false,
    storageAvailable: Long = 10L * 1024 * 1024 * 1024,
    storageReserve: Long = 0L,
): Fixtures {
    val dao = ControllerMediaDao()
    val subtitles = ControllerSubtitleDao()
    val metadata = ControllerMetadataDao()
    val store = ControllerFileStore()
    val storage = ControllerStorage(available = storageAvailable, reserve = storageReserve)
    val connectivity = ControllerConnectivity(metered = metered)
    return Fixtures(
        controller = DownloadController(
            storageManager = store,
            storageProvider = storage,
            connectivityChecker = connectivity,
            downloadedMediaDao = dao,
            downloadedSubtitleDao = subtitles,
            offlineDisplayMetadataDao = metadata,
        ),
        dao = dao,
        subtitles = subtitles,
        metadata = metadata,
        store = store,
    )
}

class DownloadControllerTest {
    @Test
    fun `enqueue persists a queued download`() = runTest {
        val fixtures = fixtures()

        val result = fixtures.controller.enqueue(movieId, Resolution.HD_720)

        assertThat(result.getOrNull()).isEqualTo(EnqueueOutcome.Queued)
        val row = fixtures.dao.entity(1)
        assertThat(row).isNotNull()
        assertThat(row?.status).isEqualTo("queued")
        assertThat(row?.selectedQuality).isEqualTo("720p")
        assertThat(fixtures.controller.listDownloads().getOrNull()?.single())
            .isInstanceOf(DownloadState.Queued::class.java)
    }

    @Test
    fun `enqueue rejects an already queued or active download`() = runTest {
        val fixtures = fixtures()
        fixtures.dao.set(listOf(entity(status = "queued")))

        val result = fixtures.controller.enqueue(movieId, Resolution.HD_720)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("already active or queued")
    }

    @Test
    fun `enqueue reports already available at higher quality`() = runTest {
        val fixtures = fixtures()
        fixtures.dao.set(listOf(entity(status = "completed", quality = "1080p", localFilePath = "videos/1.mp4")))

        val result = fixtures.controller.enqueue(movieId, Resolution.HD_720)

        assertThat(result.getOrNull()).isEqualTo(EnqueueOutcome.AlreadyAvailableHigherQuality)
    }

    @Test
    fun `enqueue fails on metered network`() = runTest {
        val fixtures = fixtures(metered = true)

        val result = fixtures.controller.enqueue(movieId, Resolution.HD_720)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Wi-Fi")
    }

    @Test
    fun `enqueue fails when storage is insufficient`() = runTest {
        val fixtures = fixtures(storageAvailable = 100L, storageReserve = 50L)

        val result = fixtures.controller.enqueue(movieId, Resolution.HD_720, requiredBytes = 100L)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Insufficient storage")
    }

    @Test
    fun `pause and resume persist status transitions`() = runTest {
        val fixtures = fixtures()
        fixtures.dao.set(listOf(entity(id = 7, status = "queued")))
        val localId = LocalMediaIdentifier("1/7")

        assertThat(fixtures.controller.pause(localId).getOrNull()).isEqualTo(DownloadCommandOutcome.Applied)
        assertThat(fixtures.dao.entity(7)?.status).isEqualTo("paused")

        assertThat(fixtures.controller.resume(localId).getOrNull()).isEqualTo(DownloadCommandOutcome.Applied)
        assertThat(fixtures.dao.entity(7)?.status).isEqualTo("queued")
    }

    @Test
    fun `cancel marks removed and deletes the local file`() = runTest {
        val fixtures = fixtures()
        fixtures.dao.set(listOf(entity(id = 3, status = "downloading", localFilePath = "videos/3.mp4")))

        val result = fixtures.controller.cancel(LocalMediaIdentifier("1/3"))

        assertThat(result.getOrNull()).isEqualTo(DownloadCommandOutcome.Applied)
        assertThat(fixtures.dao.entity(3)?.status).isEqualTo("removed")
        assertThat(fixtures.store.deleted).containsExactly(OfflineRelativePath.safe("videos/3.mp4"))
    }

    @Test
    fun `remove deletes the row and only deletes metadata when no rows remain`() = runTest {
        val fixtures = fixtures()
        fixtures.dao.set(
            listOf(
                entity(id = 1, contentId = "1"),
                entity(id = 2, contentId = "1"),
            ),
        )
        fixtures.metadata.upsert(metadata(contentId = "1"))

        fixtures.controller.remove(LocalMediaIdentifier("1/1"))

        assertThat(fixtures.dao.entity(1)).isNull()
        assertThat(fixtures.metadata.rows.map { it.contentId }).containsExactly("1")

        fixtures.controller.remove(LocalMediaIdentifier("1/2"))

        assertThat(fixtures.dao.entity(2)).isNull()
        assertThat(fixtures.metadata.rows).isEmpty()
    }

    @Test
    fun `listOfflineAssets maps completed rows`() = runTest {
        val fixtures = fixtures()
        fixtures.dao.set(
            listOf(
                entity(id = 1, status = "completed", localFilePath = "videos/1.mp4", sizeBytes = 42L),
                entity(id = 2, status = "queued"),
            ),
        )

        val assets = fixtures.controller.listOfflineAssets().getOrNull()

        assertThat(assets).hasSize(1)
        assertThat(assets?.single()?.videoRelativePath).isEqualTo(OfflineRelativePath.safe("videos/1.mp4"))
    }

    @Test
    fun `enqueueSubtitle queues once and reports already available`() = runTest {
        val fixtures = fixtures()
        fixtures.dao.set(listOf(entity(id = 5)))
        val localId = LocalMediaIdentifier("1/5")

        val first = fixtures.controller.enqueueSubtitle(localId, LanguageCode("en"))
        val second = fixtures.controller.enqueueSubtitle(localId, LanguageCode("en"))

        assertThat(first.getOrNull()).isEqualTo(SubtitleEnqueueOutcome.Queued)
        assertThat(second.getOrNull()).isEqualTo(SubtitleEnqueueOutcome.AlreadyAvailable)
        assertThat(fixtures.subtitles.rows).hasSize(1)
    }

    @Test
    fun `enqueueSubtitle fails for a malformed local id`() = runTest {
        val fixtures = fixtures()

        val result = fixtures.controller.enqueueSubtitle(LocalMediaIdentifier("bogus"), LanguageCode("en"))

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `enqueueSubtitle fails for an unknown download id`() = runTest {
        val fixtures = fixtures()

        val result = fixtures.controller.enqueueSubtitle(LocalMediaIdentifier("1/99"), LanguageCode("en"))

        assertThat(result.isFailure).isTrue()
        assertThat(fixtures.subtitles.rows).isEmpty()
    }
}
