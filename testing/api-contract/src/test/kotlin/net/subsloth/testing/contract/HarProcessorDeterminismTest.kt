package net.subsloth.testing.contract

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class HarProcessorDeterminismTest {
    private val rules =
        SanitizationRules(
            version = 1,
            description = "test",
            redactFields = emptyList(),
            urlPatterns = emptyList(),
            hostBlocklist = emptyList(),
            responseHeaderRedactions = emptyList(),
            requestHeaderRedactions = emptyList(),
        )

    @Test
    fun `export removes stale web fixtures before rewriting category`() {
        val tempRoot = Files.createTempDirectory("har-processor-determinism-").toFile()
        val nativeDir = File(tempRoot, "native")
        val webDir = File(tempRoot, "web")

        val firstHar =
            File(tempRoot, "first.har").apply {
                writeText(
                    harJson(
                        harEntry(
                            url = "https://media.tv/en/favorite_media?media_id=1",
                            method = "POST",
                            status = 200,
                            body = "media.alert('success', 'Saved to favorites');",
                        ),
                        harEntry(
                            url = "https://media.tv/en/favorite_media?media_id=1",
                            method = "DELETE",
                            status = 200,
                            body = "media.alert('success', 'Removed from favorites');",
                        ),
                        harEntry(
                            url = "https://media.tv/en/shows/the-boys/subscriptions?kind=email",
                            method = "POST",
                            status = 200,
                            body = "media.alert('success', 'Subscription added');",
                        ),
                    ),
                )
            }

        HarProcessor.export(
            harFiles = setOf(firstHar),
            rules = rules,
            nativeOutputDir = nativeDir,
            webOutputDir = webDir,
            keepRaw = true,
        )

        assertThat(File(webDir, "FavoriteMedia.post.js").isFile).isTrue()
        assertThat(File(webDir, "FavoriteMedia.delete.js").isFile).isTrue()
        assertThat(File(webDir, "Subscriptions.post.js").isFile).isTrue()

        val secondHar =
            File(tempRoot, "second.har").apply {
                writeText(
                    harJson(
                        harEntry(
                            url = "https://media.tv/en/favorite_media?media_id=1",
                            method = "POST",
                            status = 200,
                            body = "media.alert('success', 'Saved to favorites');",
                        ),
                    ),
                )
            }

        HarProcessor.export(
            harFiles = setOf(secondHar),
            rules = rules,
            nativeOutputDir = nativeDir,
            webOutputDir = webDir,
            keepRaw = true,
        )

        assertThat(File(webDir, "FavoriteMedia.post.js").isFile).isTrue()
        assertThat(File(webDir, "FavoriteMedia.delete.js").exists()).isFalse()
        assertThat(File(webDir, "Subscriptions.post.js").exists()).isFalse()
    }

    private fun harJson(vararg entries: String): String =
        """
        {"log":{"entries":[${entries.joinToString(",")} ]}}
        """.trimIndent()

    private fun harEntry(url: String, method: String, status: Int, body: String): String =
        """
        {
          "request": {
            "url": "$url",
            "method": "$method",
            "headers": []
          },
          "response": {
            "status": $status,
            "headers": [
              {"name": "content-type", "value": "text/javascript"}
            ],
            "content": {
              "text": ${jsonString(body)}
            }
          }
        }
        """.trimIndent()

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
}
