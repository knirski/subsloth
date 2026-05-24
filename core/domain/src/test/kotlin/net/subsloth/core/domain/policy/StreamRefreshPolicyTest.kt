package net.subsloth.core.domain.policy

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamRefreshPolicyTest {
    // ── canRefresh ────────────────────────────────────────────────────────

    @Test
    fun `canRefresh returns true when refresh not used and online`() {
        assertTrue(StreamRefreshPolicy.canRefresh(refreshUsed = false, isOfflinePlayback = false))
    }

    @Test
    fun `canRefresh returns false when refresh already used`() {
        assertFalse(StreamRefreshPolicy.canRefresh(refreshUsed = true, isOfflinePlayback = false))
    }

    @Test
    fun `canRefresh returns false for offline playback regardless of refresh state`() {
        assertFalse(StreamRefreshPolicy.canRefresh(refreshUsed = false, isOfflinePlayback = true))
        assertFalse(StreamRefreshPolicy.canRefresh(refreshUsed = true, isOfflinePlayback = true))
    }

    // ── markRefreshUsed ──────────────────────────────────────────────────

    @Test
    fun `markRefreshUsed returns true`() {
        assertTrue(StreamRefreshPolicy.markRefreshUsed())
    }
}
