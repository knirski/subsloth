package net.subsloth.testing.contract

import kotlinx.serialization.json.JsonObject
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.regex.Pattern

class MockMappingVerificationTest {
    companion object {
        @JvmStatic
        fun endpoints(): List<Endpoint> = Endpoint.entries
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    fun `fixture exists on classpath and matches declared format`(endpoint: Endpoint) {
        for (method in endpoint.methods) {
            val text = FixtureLoader.loadFixtureText(endpoint, method)
            assertThat(text).isNotEmpty()

            if (endpoint.responseKind == ResponseKind.Json) {
                val element = FixtureLoader.loadFixtureJson(endpoint, method)
                if (element is JsonObject) {
                    assertThat(element).isNotEmpty()
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    fun `fixture body has no sensitive fields`(endpoint: Endpoint) {
        for (method in endpoint.methods) {
            val text = FixtureLoader.loadFixtureText(endpoint, method).lowercase()
            assertThat(text).doesNotContain("\"password\":")
            assertThat(text).doesNotContain("\"email\":")
            assertThat(text).doesNotContain("\"auth_token\":")
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    fun `urlPattern is valid regex`(endpoint: Endpoint) {
        Pattern.compile(endpoint.urlPattern)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    fun `stub replays configured method status and content type`(endpoint: Endpoint) {
        val server = WireMockServerFactory.create()
        try {
            server.start()

            val client = HttpClient.newHttpClient()

            for (method in endpoint.methods) {
                val request =
                    HttpRequest
                        .newBuilder()
                        .uri(URI.create(server.baseUrl().trimEnd('/') + endpoint.examplePath))
                        .method(method.name, HttpRequest.BodyPublishers.noBody())
                        .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                val expectedBody = FixtureLoader.loadFixtureText(endpoint, method)

                assertThat(response.statusCode()).isEqualTo(endpoint.responseStatus)
                when (endpoint.responseKind) {
                    ResponseKind.RedirectLocation -> {
                        assertThat(response.headers().firstValue("Location")).isPresent()
                        assertThat(response.body()).isEmpty()
                        assertThat(
                            response
                                .headers()
                                .firstValue("Location")
                                .get()
                                .trim(),
                        ).isEqualTo(expectedBody.trim())
                    }

                    else -> {
                        assertThat(response.headers().firstValue("Content-Type"))
                            .hasValue(endpoint.contentType)
                        assertThat(response.body()).isNotEmpty()
                        assertThat(response.body()).isEqualTo(expectedBody)
                    }
                }
            }
        } finally {
            server.stop()
        }
    }
}
