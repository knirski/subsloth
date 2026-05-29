package net.subsloth.core.media.download

import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class OpaquePathPolicyTest {
    @Test
    fun `opaque path uses only allowed components`() {
        val path =
            OpaquePathPolicy.videoPath(
                contentId = "12345",
                extension = "mp4",
                randomId = UUID.fromString("00000000-0000-0000-0000-000000000111"),
            )
        assertThat(path)
            .isEqualTo(
                OfflineRelativePath(
                    "downloads/video/12345/00000000-0000-0000-0000-000000000111.mp4",
                ),
            )
    }

    @Test
    fun `redactor removes absolute local path details`() {
        val redacted =
            PathRedactor.redact(
                "/data/user/0/net.subsloth/files/downloads/video/7/main.mp4",
            )
        assertThat(redacted).isEqualTo("[redacted-local-path]")
    }
}
