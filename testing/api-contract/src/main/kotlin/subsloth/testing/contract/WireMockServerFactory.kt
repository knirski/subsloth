package subsloth.testing.contract

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

object WireMockServerFactory {
    fun create(port: Int = 0): WireMockServer {
        val server = WireMockServer(WireMockConfiguration().port(port))

        for (endpoint in Endpoint.entries) {
            for (method in endpoint.methods) {
                val body =
                    try {
                        FixtureLoader.loadFixtureText(endpoint, method)
                    } catch (_: Exception) {
                        println(
                            "[wiremock] WARNING: Fixture not found on classpath" +
                                " for ${endpoint.fixtureName} $method at ${endpoint.resourcePathFor(method)}" +
                                " - stub skipped",
                        )
                        continue
                    }

                server.stubFor(
                    requestBuilder(method, endpoint.urlPattern)
                        .willReturn(responseBuilder(endpoint, body)),
                )
            }
        }

        return server
    }

    private fun requestBuilder(
        method: HttpMethod,
        urlPattern: String,
    ): MappingBuilder =
        when (method) {
            HttpMethod.GET -> get(urlPathMatching(urlPattern))
            HttpMethod.POST -> post(urlPathMatching(urlPattern))
            HttpMethod.DELETE -> delete(urlPathMatching(urlPattern))
        }

    private fun responseBuilder(
        endpoint: Endpoint,
        body: String,
    ): ResponseDefinitionBuilder {
        val response = aResponse().withStatus(endpoint.responseStatus)
        endpoint.contentType?.let { contentType ->
            response.withHeader("Content-Type", contentType)
        }

        return when (endpoint.responseKind) {
            ResponseKind.RedirectLocation -> {
                response.withHeader("Location", body.trim()).withBody("")
            }

            else -> {
                response.withBody(body)
            }
        }
    }
}
