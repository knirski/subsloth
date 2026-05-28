package net.subsloth.core.model.error

/**
 * Root sealed interface for all recoverable domain errors.
 *
 * All domain and application failures use [Result] with these typed errors
 * instead of unchecked exceptions or nullable sentinels.
 */
sealed interface DomainError

// ── Authentication ──────────────────────────────────────────────────────────

/** Errors related to authentication and session state. */
sealed interface AuthError : DomainError {
    /** Provided credentials are invalid or rejected by the server. */
    data object InvalidCredentials : AuthError

    /** The existing session has expired and needs re-authentication. */
    data object SessionExpired : AuthError

    /** Account has been suspended or disabled. */
    data object AccountSuspended : AuthError
}

// ── Payment / Free-tier limits ──────────────────────────────────────────────

/** Errors related to payment or free-tier usage limits. */
sealed interface PaymentLimitError : DomainError {
    /** The user's free-tier concurrent stream limit has been reached. */
    data object ConcurrentStreamLimit : PaymentLimitError

    /** The requested content requires a higher subscription tier. */
    data object SubscriptionRequired : PaymentLimitError
}

// ── Media availability ──────────────────────────────────────────────────────

/** Errors indicating the requested media cannot be served. */
sealed interface MediaError : DomainError {
    /** Media is not currently available (geo-restricted, removed, etc.). */
    data object Unavailable : MediaError

    /** Media identifier does not match any known content. */
    data object NotFound : MediaError

    /** Media is geo-restricted and not playable from the current region. */
    data object GeoRestricted : MediaError

    /** Previously available media has expired. */
    data object Expired : MediaError

    /** The episode is scheduled for future release and is not playable yet. */
    data object Upcoming : MediaError
}

// ── Download ────────────────────────────────────────────────────────────────

/** Errors related to offline downloading and local storage. */
sealed interface DownloadError : DomainError {
    /** Device storage is too low to complete the download. */
    data object InsufficientStorage : DownloadError

    /** Required subtitle track is missing and cannot be bundled. */
    data object MissingSubtitle : DownloadError

    /** The download queue is full or at capacity. */
    data object QueueFull : DownloadError

    /** Download requires a Wi-Fi connection per transfer preference. */
    data object NeedsWifi : DownloadError

    /** The local file is missing or corrupted. */
    data object MissingLocalFile : DownloadError

    /** The requested quality is ambiguous or unavailable. */
    data object AmbiguousQuality : DownloadError
}

// ── Quality / Decode ────────────────────────────────────────────────────────

/** Errors related to unsupported or unavailable video/audio qualities. */
sealed interface QualityError : DomainError {
    /** The requested quality is not available for this media. */
    data object Unsupported : QualityError

    /** No fallback quality could be determined. */
    data object NoFallback : QualityError

    /** The available quality is below the user's minimum threshold. */
    data object BelowMinimum : QualityError
}

/** Errors during response decoding or data parsing. */
sealed interface DecodeError : DomainError {
    /** Server returned an unexpected or malformed response body. */
    data object InvalidResponseFormat : DecodeError

    /** Data could not be deserialised into the expected type. */
    data object SerializationFailed : DecodeError

    /** Critical fields are missing from the response. */
    data class MissingFields(
        val fields: List<String>,
    ) : DecodeError
}

// ── Network ─────────────────────────────────────────────────────────────────

/** Errors from the network or transport layer. */
sealed interface NetworkError : DomainError {
    /** The request timed out. */
    data object Timeout : NetworkError

    /** No network connectivity is available. */
    data object NoConnectivity : NetworkError

    /** Server returned a non-retryable HTTP error. */
    data class HttpError(
        val code: Int,
        val message: String,
    ) : NetworkError

    /** Unexpected redirect or non-JSON response detected. */
    data object UnexpectedResponse : NetworkError

    /** The request was throttled (HTTP 429). */
    data class RateLimited(
        val retryAfterSeconds: Int?,
    ) : NetworkError
}

// ── Library ─────────────────────────────────────────────────────────────────

/** Errors related to library operations. */
sealed interface LibraryError : DomainError {
    /** The requested library operation is not supported server-side. */
    data object NotSupported : LibraryError

    /** The item is already in the target library collection. */
    data object AlreadyExists : LibraryError

    /** The item is not found in the target library collection. */
    data object NotFound : LibraryError
}
