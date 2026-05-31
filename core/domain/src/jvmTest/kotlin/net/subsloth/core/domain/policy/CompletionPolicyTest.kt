package net.subsloth.core.domain.policy

import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class CompletionPolicyTest {
    // ── 95% threshold marks item as locally completed ─────────────────────

    @Test
    fun `playback at 95 percent marks item completed`() {
        val result = CompletionPolicy.isCompleted(positionSeconds = 950, durationSeconds = 1000)
        assertThat(result).isTrue()
    }

    @Test
    fun `playback at 100 percent marks item completed`() {
        val result = CompletionPolicy.isCompleted(positionSeconds = 1000, durationSeconds = 1000)
        assertThat(result).isTrue()
    }

    @Test
    fun `playback just below 95 percent is not completed`() {
        val result = CompletionPolicy.isCompleted(positionSeconds = 949, durationSeconds = 1000)
        assertThat(result).isFalse()
    }

    @Test
    fun `playback at 0 percent is not completed`() {
        val result = CompletionPolicy.isCompleted(positionSeconds = 0, durationSeconds = 1000)
        assertThat(result).isFalse()
    }

    // ── Unknown duration: completed only on actual playback-ended event ───

    @Test
    fun `unknown duration is not completed via position alone`() {
        val result = CompletionPolicy.isCompleted(positionSeconds = 500, durationSeconds = 0)
        assertThat(result).isFalse()
    }

    @Test
    fun `unknown duration with maximum position is not completed`() {
        val result = CompletionPolicy.isCompleted(positionSeconds = Long.MAX_VALUE, durationSeconds = 0)
        assertThat(result).isFalse()
    }

    // ── Explicit watched toggle ───────────────────────────────────────────

    @Test
    fun `explicit watched toggle marks as completed`() {
        val progress = progress(positionSeconds = 120, durationSeconds = 600)
        val result = CompletionPolicy.applyExplicitWatched(progress)
        assertThat(result.isWatched).isTrue()
    }

    @Test
    fun `explicit unwatched toggle marks as not watched`() {
        val progress = progress(positionSeconds = 1000, durationSeconds = 1000, isWatched = true)
        val result = CompletionPolicy.applyExplicitUnwatched(progress)
        assertThat(result.isWatched).isFalse()
    }

    // ── Scope isolation: watched toggles are not mirrored ─────────────────

    @Test
    fun `explicit watched toggle does not automatically mirror to shared offline`() {
        val accountProgress = progress(positionSeconds = 120, durationSeconds = 600)
        val offlineProgress = progress(positionSeconds = 120, durationSeconds = 600)

        val updatedAccount = CompletionPolicy.applyExplicitWatched(accountProgress)

        // Offline progress should remain unchanged.
        assertThat(updatedAccount.isWatched).isTrue()
        assertThat(offlineProgress.isWatched).isFalse()
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun progress(positionSeconds: Long, durationSeconds: Long, isWatched: Boolean = false): PlaybackProgress =
        PlaybackProgress(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
            lastUpdatedEpochSeconds = Instant.fromEpochSeconds(1_800_000_000L),
            isWatched = isWatched,
        )
}
