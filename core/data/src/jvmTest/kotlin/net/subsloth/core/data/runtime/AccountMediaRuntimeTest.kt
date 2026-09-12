package net.subsloth.core.data.runtime

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import net.subsloth.core.domain.port.Credentials
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.client.ClientFactory
import net.subsloth.database.dao.AccountPlaybackProgressDao
import net.subsloth.database.dao.OfflinePlaybackProgressDao
import net.subsloth.database.dao.WatchedStateDao
import net.subsloth.database.entity.AccountPlaybackProgressEntity
import net.subsloth.database.entity.OfflinePlaybackProgressEntity
import net.subsloth.database.entity.WatchedStateEntity
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

private const val PROFILE = "profile-1"

private class FakeSessionPort(initial: Session = Session.Anonymous) : SessionPort {
    private val sessionState = MutableStateFlow(initial)
    override val state: StateFlow<Session> = sessionState.asStateFlow()
    override fun current(): Session = sessionState.value
    override suspend fun open(credentials: Credentials): Outcome<Unit> {
        sessionState.value = Session.Authenticated(
            userId = PROFILE,
            openedAtEpochSeconds = 0,
            credentials = credentials,
        )
        return Outcome.Success(Unit)
    }

    override suspend fun close(): Outcome<Unit> {
        sessionState.value = Session.Anonymous
        return Outcome.Success(Unit)
    }

    override suspend fun invalidate(): Outcome<Unit> = close()
}

private class FakeAccountDao : AccountPlaybackProgressDao {
    val rows = mutableListOf<AccountPlaybackProgressEntity>()
    override suspend fun getByProfileAndContentId(
        profileKey: String,
        contentId: String,
    ): AccountPlaybackProgressEntity? = rows.firstOrNull { it.profileKey == profileKey && it.contentId == contentId }

    override fun getAllForProfile(profileKey: String): Flow<List<AccountPlaybackProgressEntity>> =
        MutableStateFlow(rows.filter { it.profileKey == profileKey })

    override suspend fun upsert(entity: AccountPlaybackProgressEntity) {
        rows.removeAll { it.profileKey == entity.profileKey && it.contentId == entity.contentId }
        rows += entity
    }

    override suspend fun deleteAllForProfile(profileKey: String) {
        rows.removeAll { it.profileKey == profileKey }
    }
}

private class FakeOfflineDao : OfflinePlaybackProgressDao {
    val rows = mutableListOf<OfflinePlaybackProgressEntity>()
    override fun getAll(): Flow<List<OfflinePlaybackProgressEntity>> = MutableStateFlow(rows)
    override suspend fun getByContentId(contentId: String): OfflinePlaybackProgressEntity? =
        rows.firstOrNull { it.contentId == contentId }

    override suspend fun upsert(entity: OfflinePlaybackProgressEntity) {
        rows.removeAll { it.contentId == entity.contentId }
        rows += entity
    }

    override suspend fun deleteAll() {
        rows.clear()
    }
}

private class FakeWatchedDao : WatchedStateDao {
    val rows = mutableListOf<WatchedStateEntity>()
    override fun getAllForProfile(profileKey: String): Flow<List<WatchedStateEntity>> =
        MutableStateFlow(rows.filter { it.profileKey == profileKey })

    override suspend fun getByProfileAndContentId(profileKey: String, contentId: String): WatchedStateEntity? =
        rows.firstOrNull { it.profileKey == profileKey && it.contentId == contentId }

    override suspend fun upsert(entity: WatchedStateEntity) {
        rows.removeAll { it.profileKey == entity.profileKey && it.contentId == entity.contentId }
        rows += entity
    }

    override suspend fun deleteAllForProfile(profileKey: String) {
        rows.removeAll { it.profileKey == profileKey }
    }
}

class AccountMediaRuntimeTest {
    private val accountDao = FakeAccountDao()
    private val offlineDao = FakeOfflineDao()
    private val watchedDao = FakeWatchedDao()
    private val session = FakeSessionPort()
    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.fromEpochSeconds(1_000)
    }

    private fun runtime(api: Api? = null): AccountMediaRuntime = AccountMediaRuntime(
        sessionPort = session,
        catalogRepository = { error("catalog repository is not used in these tests") },
        api = { api ?: error("api is not used in these tests") },
        accountPlaybackProgressDao = { accountDao },
        offlinePlaybackProgressDao = { offlineDao },
        watchedStateDao = { watchedDao },
        clock = fixedClock,
        anonymousProfileKey = "default",
    )

    @Test
    fun `anonymous session uses the fallback profile key`() {
        assertThat(runtime().currentProfileKey().value).isEqualTo("default")
    }

    @Test
    fun `authenticated session uses the session profile key`() = runTest {
        session.open(Credentials("user", "password"))
        assertThat(runtime().currentProfileKey().value).isEqualTo(PROFILE)
    }

    @Test
    fun `online progress persists for the active profile and marks watched past the threshold`() = runTest {
        session.open(Credentials("user", "password"))
        val runtime = runtime()
        val mediaId = net.subsloth.core.model.media.Media.MediaId.Episode(EpisodeId(77))

        runtime.savePlaybackProgress(mediaId, positionSeconds = 950, durationSeconds = 1_000)

        val row = requireNotNull(accountDao.rows.singleOrNull())
        assertThat(row.profileKey).isEqualTo(PROFILE)
        assertThat(row.contentId).isEqualTo("77")
        assertThat(row.contentType).isEqualTo("episode")
        assertThat(row.updatedAtEpochSeconds).isEqualTo(1_000)
        assertThat(watchedDao.rows.map { it.contentId }).contains("77")
    }

    @Test
    fun `offline progress persists in the shared offline table`() = runTest {
        session.open(Credentials("user", "password"))
        val runtime = runtime()
        val mediaId = net.subsloth.core.model.media.Media.MediaId.Movie(MovieId(5))

        runtime.savePlaybackProgress(
            mediaId,
            positionSeconds = 10,
            durationSeconds = 100,
            playbackMode = PlaybackMode.OFFLINE,
        )

        assertThat(accountDao.rows).isEmpty()
        assertThat(offlineDao.rows.map { it.contentId }).contains("5")
    }

    @Test
    fun `listAccountPlaybackProgress maps rows to typed media ids`() = runTest {
        session.open(Credentials("user", "password"))
        accountDao.upsert(
            AccountPlaybackProgressEntity(
                profileKey = PROFILE,
                contentId = "42",
                contentType = "movie",
                positionSeconds = 950,
                durationSeconds = 1_000,
                updatedAtEpochSeconds = 12,
            ),
        )

        val progress = runtime().listAccountPlaybackProgress().getOrThrow().single()

        assertThat(progress.mediaId).isEqualTo(net.subsloth.core.model.media.Media.MediaId.Movie(MovieId(42)))
        assertThat(progress.positionSeconds).isEqualTo(950)
        assertThat(progress.isWatched).isTrue()
        assertThat(progress.lastUpdatedEpochSeconds).isEqualTo(Instant.fromEpochSeconds(12))
    }

    @Test
    fun `resolveSubtitleTracks maps the live DTO shape`() = runTest {
        val episodeJson = """
            {"id":77,"show_id":1,"season":1,"number":2,"name":"Episode 2",
             "subtitles":[{"code":"en","language":"English","format":"srt","url":"https://cdn.example/en.srt"}]}
        """.trimIndent()
        val client = ClientFactory.create(
            engine = MockEngine {
                respond(
                    content = ByteReadChannel(episodeJson),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val runtime = runtime(api = Api(client))
        try {
            val subtitles = runtime.resolveSubtitleTracks(
                net.subsloth.core.model.media.Media.MediaId.Episode(EpisodeId(77)),
            )

            assertThat(subtitles.single().language).isEqualTo(LanguageCode("en"))
            assertThat(subtitles.single().url).isEqualTo("https://cdn.example/en.srt")
        } finally {
            client.close()
        }
    }

    @Test
    fun `listWatchedContentIds and isWatched use the active profile`() = runTest {
        session.open(Credentials("user", "password"))
        val runtime = runtime()
        val mediaId = net.subsloth.core.model.media.Media.MediaId.Episode(EpisodeId(9))

        runtime.markWatched(mediaId)

        assertThat(runtime.listWatchedContentIds()).contains("9")
        assertThat(runtime.isWatched(mediaId)).isTrue()
        assertThat(runtime.isWatched(net.subsloth.core.model.media.Media.MediaId.Episode(EpisodeId(10)))).isFalse()
    }

    @Test
    fun `loadPlaybackProgress finds the typed entry`() = runTest {
        session.open(Credentials("user", "password"))
        accountDao.upsert(
            AccountPlaybackProgressEntity(
                profileKey = PROFILE,
                contentId = "8",
                contentType = "show",
                positionSeconds = 300,
                durationSeconds = 600,
                updatedAtEpochSeconds = 5,
            ),
        )

        val found: PlaybackProgress? = runtime().loadPlaybackProgress(
            net.subsloth.core.model.media.Media.MediaId.Show(net.subsloth.core.model.identifier.ShowId(8)),
        )

        assertThat(found?.positionSeconds).isEqualTo(300)
    }
}
