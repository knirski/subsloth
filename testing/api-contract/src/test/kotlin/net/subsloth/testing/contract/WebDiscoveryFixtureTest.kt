package net.subsloth.testing.contract

import kotlinx.serialization.json.JsonObject
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.net.URI
import java.util.regex.Pattern

class WebDiscoveryFixtureTest {
    companion object {
        @JvmStatic
        fun fixtures(): List<Endpoint> = Endpoint.entries
            .filter { it.category == Endpoint.FixtureCategory.WebDiscovery }
            .sortedBy { it.fixtureName }
    }

    private val forbiddenHosts =
        listOf(
            "example.com",
            "media.tv",
            "media-mirror.tv",
            "placehold.co",
            "subsloth.test",
        )

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    fun `fixture matches declared format`(endpoint: Endpoint) {
        for (method in endpoint.methods) {
            when (endpoint.responseKind) {
                ResponseKind.Json -> {
                    val element = FixtureLoader.loadFixtureJson(endpoint, method)
                    if (element is JsonObject) {
                        assertThat(element).isNotEmpty()
                    }
                }

                else -> {
                    assertThat(FixtureLoader.loadFixtureText(endpoint, method)).isNotEmpty()
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    fun `all url values point to invalid hosts`(endpoint: Endpoint) {
        val candidateValues =
            endpoint.methods.flatMap { method ->
                when (endpoint.responseKind) {
                    ResponseKind.Json -> FixtureLoader.fixtureStrings(endpoint.resourcePathFor(method))
                    else -> extractUrls(FixtureLoader.loadFixtureText(endpoint, method))
                }
            }

        for (value in candidateValues) {
            if (value.startsWith("http://") || value.startsWith("https://")) {
                val host = URI(value).host ?: error("Missing host in URL value: $value")
                assertThat(host).endsWith(".invalid")
                forbiddenHosts.forEach { forbiddenHost ->
                    assertThat(host).doesNotContain(forbiddenHost)
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    fun `fixture contains no sensitive fields`(endpoint: Endpoint) {
        for (method in endpoint.methods) {
            val text = FixtureLoader.loadFixtureText(endpoint, method).lowercase()

            assertThat(text).doesNotContain("password")
            assertThat(text).doesNotContain("auth_token")
            assertThat(text).doesNotContain("access_token")
            assertThat(text).doesNotContain("refresh_token")
            assertThat(text).doesNotContain("api_key")
            assertThat(text).doesNotContain("session_id")
            assertThat(text).doesNotContain("authorization")
            assertThat(text).doesNotContain("bearer")
            assertThat(text).doesNotContain("cookie")
            assertThat(text).doesNotContain("set-cookie")
            assertThat(text).doesNotContain("email")
            assertThat(text).doesNotContain("phone")
            assertThat(text).doesNotContain("first_name")
            assertThat(text).doesNotContain("last_name")
            assertThat(text).doesNotContain("address")
            assertThat(text).doesNotContain("ip_address")
            assertThat(text).doesNotContain("geolocation")
            assertThat(text).doesNotContain("device_id")
            assertThat(text).doesNotContain("fingerprint")
            assertThat(text).doesNotContain("credit_card")
            assertThat(text).doesNotContain("payment")
            assertThat(text).doesNotContain("billing")
            assertThat(text).doesNotContain("transaction_id")
            assertThat(text).doesNotContain("browser_trace")
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    fun `fixture path lives under web discovery`(endpoint: Endpoint) {
        endpoint.methods.forEach { method ->
            assertThat(endpoint.resourcePathFor(method)).contains("/media/web-discovery/")
        }
    }

    private fun extractUrls(text: String): List<String> = Pattern
        .compile("https?://[^\\s'\"`]+")
        .matcher(text)
        .results()
        .map { result -> result.group() }
        .toList()
}
