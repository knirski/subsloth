package net.subsloth.core.network.error

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.SyncError
import net.subsloth.core.network.media.client.ResponseValidationException

/**
 * Single source of truth for mapping a [Throwable] raised at the network
 * boundary into a typed [NetworkError] or [SyncError].
 *
 * Replaces two duplicate `isIoError` string-matchers that previously lived
 * in `CatalogRepository` and `UiErrorMapping` and that drifted apart.
 * All classification is done via `is`-checks against Ktor's public
 * exception types — no message string matching.
 *
 * Note: engine-internal types (DNS, socket, native IO) are not
 * importable from `commonMain` and the Ktor engine throws them as a
 * superclass caught at the request boundary. The catch-all branch
 * treats them as connectivity loss, which matches the conservative
 * behaviour of the previous string matcher.
 *
 * Note: Ktor's [io.ktor.client.plugins.ServerResponseException] is a
 * subclass of [ResponseException], so it is matched by the
 * [ResponseException] branch and is not handled separately.
 */
object NetworkErrorClassifier {
    /**
     * Classifies a [Throwable] from a read or write call into a typed
     * [NetworkError] suitable for surfacing to the UI.
     *
     * The status code is preserved on [NetworkError.HttpError] for all
     * 4xx and 5xx codes so that downstream classifiers (e.g.
     * `toUiError`) can map 401 → `AuthRequired` and 404 → `NotFound`.
     */
    fun classifyToNetwork(throwable: Throwable): NetworkError = when (throwable) {
        is HttpRequestTimeoutException -> NetworkError.Timeout

        is ResponseException -> {
            val code = throwable.response.status.value
            when (code) {
                429 -> NetworkError.RateLimited(retryAfterSeconds = parseRetryAfter(throwable))
                in 400..599 -> NetworkError.HttpError(code = code, message = throwable.message.orEmpty())
                else -> NetworkError.UnexpectedResponse
            }
        }

        is ResponseValidationException -> NetworkError.UnexpectedResponse

        else -> NetworkError.NoConnectivity
    }

    /**
     * Classifies a [Throwable] from a catalog [sync] into a typed
     * [SyncError]. Lighter than [classifyToNetwork] because the catalog
     * sync only cares about connectivity / timeout / server / unknown.
     */
    fun classifyToSync(throwable: Throwable): SyncError = when (throwable) {
        is HttpRequestTimeoutException -> SyncError.Timeout
        is ResponseException -> SyncError.ServerError(code = throwable.response.status.value)
        is ResponseValidationException -> SyncError.Unknown
        else -> SyncError.NoConnectivity
    }

    private fun parseRetryAfter(exception: ResponseException): Int? =
        exception.response.headers["Retry-After"]?.toIntOrNull()
}
