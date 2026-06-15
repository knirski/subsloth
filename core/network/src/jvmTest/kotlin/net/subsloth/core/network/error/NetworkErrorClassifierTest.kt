package net.subsloth.core.network.error

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.SyncError
import net.subsloth.core.network.media.client.ResponseValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NetworkErrorClassifierTest {
    @Test
    fun timeout_maps_to_Timeout() {
        val error = HttpRequestTimeoutException(HttpRequestBuilder())
        assertEquals(NetworkError.Timeout, NetworkErrorClassifier.classifyToNetwork(error))
        assertEquals(SyncError.Timeout, NetworkErrorClassifier.classifyToSync(error))
    }

    @Test
    fun unknown_io_maps_to_NoConnectivity() {
        // Engine-internal IO failures (e.g. DNS, socket reset) are not
        // importable from commonMain; the classifier treats any
        // non-Ktor-public exception as connectivity loss.
        val error = RuntimeException("host not found")
        assertEquals(NetworkError.NoConnectivity, NetworkErrorClassifier.classifyToNetwork(error))
        assertEquals(SyncError.NoConnectivity, NetworkErrorClassifier.classifyToSync(error))
    }

    @Test
    fun response_5xx_maps_to_HttpError() = runBlocking {
        val ex = responseExceptionForStatus(HttpStatusCode.InternalServerError)
        val classified = NetworkErrorClassifier.classifyToNetwork(ex)
        assertIs<NetworkError.HttpError>(classified)
        assertEquals(500, classified.code)
    }

    @Test
    fun response_429_maps_to_RateLimited() = runBlocking {
        val ex = responseExceptionForStatus(HttpStatusCode.TooManyRequests)
        val classified = NetworkErrorClassifier.classifyToNetwork(ex)
        assertIs<NetworkError.RateLimited>(classified)
    }

    @Test
    fun response_404_maps_to_UnexpectedResponse() = runBlocking {
        val ex = responseExceptionForStatus(HttpStatusCode.NotFound)
        assertEquals(NetworkError.UnexpectedResponse, NetworkErrorClassifier.classifyToNetwork(ex))
    }

    @Test
    fun response_5xx_in_sync_maps_to_ServerError() = runBlocking {
        val ex = responseExceptionForStatus(HttpStatusCode.InternalServerError)
        val classified = NetworkErrorClassifier.classifyToSync(ex)
        assertIs<SyncError.ServerError>(classified)
        assertEquals(500, classified.code)
    }

    @Test
    fun response_validation_exception_maps_to_UnexpectedResponse_for_network() {
        val error = ResponseValidationException(NetworkError.UnexpectedResponse, "validation failed")
        assertEquals(NetworkError.UnexpectedResponse, NetworkErrorClassifier.classifyToNetwork(error))
    }

    @Test
    fun response_validation_exception_maps_to_Unknown_for_sync() {
        val error = ResponseValidationException(NetworkError.UnexpectedResponse, "validation failed")
        assertEquals(SyncError.Unknown, NetworkErrorClassifier.classifyToSync(error))
    }
}

private fun responseExceptionForStatus(status: HttpStatusCode): ResponseException = runBlocking {
    val client = HttpClient(
        MockEngine { _ ->
            respond(
                content = "",
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        },
    )
    val response: HttpResponse = client.get("/")
    ResponseException(response, "status ${response.status.value}")
}
