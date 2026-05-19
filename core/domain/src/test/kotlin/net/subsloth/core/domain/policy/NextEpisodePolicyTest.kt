package net.subsloth.core.domain.policy

import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Episode
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class NextEpisodePolicyTest {
    // ── Next episode follows season and episode order ─────────────────────

    @Test
    fun `next episode follows season and episode order`() {
        val current = episode(season = 1, episodeNum = 3, id = 3, Availability.Available)
        val episodes =
            listOf(
                episode(1, 1, 1, Availability.Available),
                episode(1, 2, 2, Availability.Available),
                current,
                episode(1, 4, 4, Availability.Available),
                episode(2, 1, 5, Availability.Available),
            )

        val result = NextEpisodePolicy.nextEpisode(current, episodes)

        assertThat(result).isNotNull()
        assertThat(result!!.id).isEqualTo(EpisodeId(4))
        assertThat(result.seasonNumber).isEqualTo(1)
        assertThat(result.episodeNumber).isEqualTo(4)
    }

    @Test
    fun `next episode crosses into next season`() {
        val current = episode(season = 1, episodeNum = 6, id = 6, Availability.Available)
        val episodes =
            listOf(
                episode(1, 1, 1, Availability.Available),
                episode(1, 2, 2, Availability.Available),
                current,
                episode(2, 1, 7, Availability.Available),
                episode(2, 2, 8, Availability.Available),
            )

        val result = NextEpisodePolicy.nextEpisode(current, episodes)

        assertThat(result).isNotNull()
        // Should return the next episode in order: season 2, episode 1
        assertThat(result!!.seasonNumber).isEqualTo(2)
        assertThat(result.episodeNumber).isEqualTo(1)
        assertThat(result.id).isEqualTo(EpisodeId(7))
    }

    @Test
    fun `no next episode when current is the last`() {
        val current = episode(2, 1, 6, Availability.Available)
        val episodes =
            listOf(
                episode(1, 1, 1, Availability.Available),
                episode(2, 1, 6, Availability.Available),
            )

        val result = NextEpisodePolicy.nextEpisode(current, episodes)

        assertThat(result).isNull()
    }

    @Test
    fun `returns null for single episode list`() {
        val current = episode(1, 2, 3, Availability.Available)
        val result = NextEpisodePolicy.nextEpisode(current, listOf(current))
        assertThat(result).isNull()
    }

    @Test
    fun `returns null for empty episode list`() {
        val current = episode(1, 2, 3, Availability.Available)
        val result = NextEpisodePolicy.nextEpisode(current, emptyList())
        assertThat(result).isNull()
    }

    // ── Upcoming episodes are excluded from next episode ──────────────────

    @Test
    fun `upcoming episode is not a valid next episode`() {
        val current = episode(1, 1, 5, Availability.Available)
        val nextUnreleased = episode(1, 1, 6, Availability.Upcoming(null))
        val episodes =
            listOf(
                episode(1, 1, 1, Availability.Available),
                current,
                nextUnreleased,
            )

        val result = NextEpisodePolicy.nextEpisode(current, episodes)

        // The upcoming episode is not playable, so no next episode.
        assertThat(result).isNull()
    }

    @Test
    fun `skips upcoming episode and finds next available`() {
        val current = episode(1, 1, 5, Availability.Available)
        val episodes =
            listOf(
                episode(1, 1, 1, Availability.Available),
                current,
                episode(1, 1, 6, Availability.Upcoming(null)),
                episode(1, 1, 7, Availability.Available),
            )

        val result = NextEpisodePolicy.nextEpisode(current, episodes)

        assertThat(result).isNotNull()
        assertThat(result!!.id).isEqualTo(EpisodeId(7))
    }

    // ── Offline next episode only when downloaded and playable ────────────

    @Test
    fun `offline next episode requires downloaded status`() {
        val current = episode(1, 1, 5, Availability.Available)
        val nextOnline = episode(1, 1, 6, Availability.Available)

        val episodes = listOf(current, nextOnline)
        val downloadedIds: Set<EpisodeId> = emptySet()

        val result = NextEpisodePolicy.nextEpisodeOffline(current, episodes, downloadedIds)

        assertThat(result).isNull()
    }

    @Test
    fun `offline next episode returns when next is downloaded`() {
        val current = episode(1, 1, 5, Availability.Available)
        val nextDownloaded = episode(1, 1, 6, Availability.Available)

        val episodes = listOf(current, nextDownloaded)
        val downloadedIds: Set<EpisodeId> = setOf(EpisodeId(6))

        val result = NextEpisodePolicy.nextEpisodeOffline(current, episodes, downloadedIds)

        assertThat(result).isNotNull()
        assertThat(result!!.id).isEqualTo(EpisodeId(6))
    }

    @Test
    fun `offline next episode returns downloaded episode when next is not downloaded`() {
        val current = episode(1, 1, 1, Availability.Available)
        val missing = episode(1, 1, 2, Availability.Available)
        val downloaded = episode(1, 1, 3, Availability.Available)

        val episodes = listOf(current, missing, downloaded)
        val downloadedIds: Set<EpisodeId> = setOf(EpisodeId(1), EpisodeId(3))

        val result = NextEpisodePolicy.nextEpisodeOffline(current, episodes, downloadedIds)

        assertThat(result).isNotNull()
        // Should skip episode 2 (not downloaded) and return episode 3.
        assertThat(result!!.id).isEqualTo(EpisodeId(3))
    }

    @Test
    fun `offline next episode skips multiple non-downloaded episodes`() {
        val current = episode(1, 1, 1, Availability.Available)
        val missing1 = episode(1, 1, 2, Availability.Available)
        val missing2 = episode(1, 1, 3, Availability.Available)
        val downloaded = episode(1, 2, 4, Availability.Available)

        val episodes = listOf(current, missing1, missing2, downloaded)
        val downloadedIds: Set<EpisodeId> = setOf(EpisodeId(1), EpisodeId(4))

        val result = NextEpisodePolicy.nextEpisodeOffline(current, episodes, downloadedIds)

        assertThat(result).isNotNull()
        // Should skip episodes 2 and 3 and return episode 4.
        assertThat(result!!.id).isEqualTo(EpisodeId(4))
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun episode(
        season: Int,
        episodeNum: Int,
        id: Int,
        availability: Availability,
    ): Episode =
        Episode(
            id = EpisodeId(id),
            showId = ShowId(1),
            seasonNumber = season,
            episodeNumber = episodeNum,
            title = "Episode $episodeNum",
            plot = null,
            durationSeconds = 1800L,
            availability = availability,
            imdbId = null,
            qualities = emptyList(),
            subtitles = emptyList(),
            airDateEpochSeconds = null,
            premiereDateEpochSeconds = null,
        )
}
