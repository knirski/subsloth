package subsloth.core.domain.policy

import org.junit.jupiter.api.Test
import subsloth.core.model.identifier.MovieId
import subsloth.core.model.media.Media
import subsloth.core.model.progress.PlaybackProgress
import subsloth.testing.assertions.assertThat
import kotlin.time.Instant

class ResumePolicyTest {
    // ── Threshold: ignore progress below 30 seconds ───────────────────────

    @Test
    fun `progress below 30 seconds is ignored for resume`() {
        val progress = progress(positionSeconds = 15, durationSeconds = 600)

        val result = ResumePolicy.resumablePosition(progress)

        assertThat(result).isNull()
    }

    @Test
    fun `progress at exactly 30 seconds is resumable`() {
        val progress = progress(positionSeconds = 30, durationSeconds = 600)

        val result = ResumePolicy.resumablePosition(progress)

        assertThat(result).isNotNull()
        assertThat(result).isEqualTo(30)
    }

    @Test
    fun `progress slightly below 30 seconds is ignored`() {
        val progress = progress(positionSeconds = 29, durationSeconds = 600)

        val result = ResumePolicy.resumablePosition(progress)

        assertThat(result).isNull()
    }

    @Test
    fun `zero progress is ignored`() {
        val progress = progress(positionSeconds = 0, durationSeconds = 600)

        val result = ResumePolicy.resumablePosition(progress)

        assertThat(result).isNull()
    }

    // ── Threshold: progress at or beyond 95% is completed for resume ──────

    @Test
    fun `progress at 95 percent is treated as completed`() {
        val progress = progress(positionSeconds = 950, durationSeconds = 1000)

        val result = ResumePolicy.resumablePosition(progress)

        assertThat(result).isNull()
    }

    @Test
    fun `progress beyond 95 percent is treated as completed`() {
        val progress = progress(positionSeconds = 980, durationSeconds = 1000)

        val result = ResumePolicy.resumablePosition(progress)

        assertThat(result).isNull()
    }

    @Test
    fun `progress just below 95 percent is resumable`() {
        val progress = progress(positionSeconds = 949, durationSeconds = 1000)

        val result = ResumePolicy.resumablePosition(progress)

        assertThat(result).isNotNull()
        assertThat(result).isEqualTo(949)
    }

    // ── Unknown duration handling ─────────────────────────────────────────

    @Test
    fun `resume with unknown duration uses only 30s threshold`() {
        val progress = progress(positionSeconds = 100, durationSeconds = 0)

        val result = ResumePolicy.resumablePosition(progress)

        assertThat(result).isEqualTo(100)
    }

    @Test
    fun `resume with unknown duration and zero position is ignored`() {
        val progress = progress(positionSeconds = 0, durationSeconds = 0)

        val result = ResumePolicy.resumablePosition(progress)

        assertThat(result).isNull()
    }

    @Test
    fun `resume with unknown duration does not infer completion`() {
        val progress = progress(positionSeconds = 500, durationSeconds = 0)

        val result = ResumePolicy.resumablePosition(progress)

        // Should return the position, not treat it as completed.
        assertThat(result).isEqualTo(500)
    }

    // ── Choosing later resumable point across scopes ──────────────────────

    @Test
    fun `chooses later resumable point between account and shared offline progress`() {
        val accountProgress = progress(positionSeconds = 120, durationSeconds = 600)
        val offlineProgress = progress(positionSeconds = 300, durationSeconds = 600)

        val result = ResumePolicy.latestResumablePoint(accountProgress, offlineProgress)

        assertThat(result).isEqualTo(300)
    }

    @Test
    fun `uses account progress when offline is below 30s`() {
        val accountProgress = progress(positionSeconds = 120, durationSeconds = 600)
        val offlineProgress = progress(positionSeconds = 15, durationSeconds = 600)

        val result = ResumePolicy.latestResumablePoint(accountProgress, offlineProgress)

        assertThat(result).isEqualTo(120)
    }

    @Test
    fun `uses offline progress when account is below 30s`() {
        val accountProgress = progress(positionSeconds = 15, durationSeconds = 600)
        val offlineProgress = progress(positionSeconds = 200, durationSeconds = 600)

        val result = ResumePolicy.latestResumablePoint(accountProgress, offlineProgress)

        assertThat(result).isEqualTo(200)
    }

    @Test
    fun `returns null when both progress values are below 30s`() {
        val accountProgress = progress(positionSeconds = 10, durationSeconds = 600)
        val offlineProgress = progress(positionSeconds = 20, durationSeconds = 600)

        val result = ResumePolicy.latestResumablePoint(accountProgress, offlineProgress)

        assertThat(result).isNull()
    }

    @Test
    fun `returns null when both are completed`() {
        val accountProgress = progress(positionSeconds = 960, durationSeconds = 1000)
        val offlineProgress = progress(positionSeconds = 980, durationSeconds = 1000)

        val result = ResumePolicy.latestResumablePoint(accountProgress, offlineProgress)

        assertThat(result).isNull()
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun progress(positionSeconds: Long, durationSeconds: Long): PlaybackProgress = PlaybackProgress(
        mediaId = Media.MediaId.Movie(MovieId(1)),
        positionSeconds = positionSeconds,
        durationSeconds = durationSeconds,
        lastUpdatedEpochSeconds = Instant.fromEpochSeconds(1_800_000_000L),
        isWatched = false,
    )
}
