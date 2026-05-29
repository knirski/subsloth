package net.subsloth.core.domain.policy

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class OfflineHomePolicyTest {
    @Test
    fun `offline home surfaces offline library first when device is offline and downloads exist`() {
        assertThat(
            OfflineHomePolicy.shouldSurfaceOfflineLibrary(
                isOnline = false,
                playableDownloads = 3,
            ),
        ).isTrue()
    }

    @Test
    fun `offline home does not surface when online even with downloads`() {
        assertThat(
            OfflineHomePolicy.shouldSurfaceOfflineLibrary(
                isOnline = true,
                playableDownloads = 3,
            ),
        ).isFalse()
    }

    @Test
    fun `offline home does not surface when offline but no downloads`() {
        assertThat(
            OfflineHomePolicy.shouldSurfaceOfflineLibrary(
                isOnline = false,
                playableDownloads = 0,
            ),
        ).isFalse()
    }
}
