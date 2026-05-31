package subsloth.core.domain.policy

import org.junit.jupiter.api.Test
import subsloth.testing.assertions.assertThat

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
