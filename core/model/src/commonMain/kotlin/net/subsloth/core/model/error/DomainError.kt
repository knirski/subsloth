package net.subsloth.core.model.error

/**
 * Root sealed hierarchy for all recoverable domain errors.
 *
 * Every recoverable failure in the project is a [DomainError] value.
 * Domain errors are *values*, not [Throwable]s: the project uses the
 * [Outcome] wrapper type to thread them through fallible APIs, and the
 * I/O shell translates engine exceptions into typed [DomainError]s at
 * the port boundary.
 *
 * The hierarchy is split into two super-categories so consumers can
 * dispatch on the failure class at the type level:
 *
 * - [Technical] — failures from infrastructure (network, decode, sync).
 *   The user cannot fix these directly; the appropriate response is
 *   "try again" with a backoff and a diagnostic log.
 * - [Business] — failures from the user's context or business rules
 *   (auth, payment, media availability, download, quality, library).
 *   The appropriate response is a specific, actionable message
 *   ("Sign in again", "Subscribe to watch this", "Free up storage").
 *
 * Domain code is expected to handle the super-category exhaustively
 * (e.g. `when (e: DomainError.Technical) { ... }`) and let the
 * compiler catch any new variant.
 */
sealed interface DomainError {
    /**
     * Failures from infrastructure (network, decode, sync). The user
     * cannot act on these directly.
     */
    sealed interface Technical : DomainError

    /**
     * Failures from the user's context or business rules. The user can
     * act on these (re-authenticate, change subscription, free storage).
     */
    sealed interface Business : DomainError
}

// ── Authentication ──────────────────────────────────────────────────────────

/** Errors related to authentication and session state. */
sealed interface AuthError : DomainError.Business {
    /** Provided credentials are invalid or rejected by the server. */
    data object InvalidCredentials : AuthError

    /** The existing session has expired and needs re-authentication. */
    data object SessionExpired : AuthError

    /** Account has been suspended or disabled. */
    data object AccountSuspended : AuthError
}

// ── Payment / Free-tier limits ──────────────────────────────────────────────

/** Errors related to payment or free-tier usage limits. */
sealed interface PaymentLimitError : DomainError.Business {
    /** The user's free-tier concurrent stream limit has been reached. */
    data object ConcurrentStreamLimit : PaymentLimitError

    /** The requested content requires a higher subscription tier. */
    data object SubscriptionRequired : PaymentLimitError
}

// ── Media availability ──────────────────────────────────────────────────────

/** Errors indicating the requested media cannot be served. */
sealed interface MediaError : DomainError.Business {
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
sealed interface DownloadError : DomainError.Business {
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
sealed interface QualityError : DomainError.Business {
    /** The requested quality is not available for this media. */
    data object Unsupported : QualityError

    /** No fallback quality could be determined. */
    data object NoFallback : QualityError

    /** The available quality is below the user's minimum threshold. */
    data object BelowMinimum : QualityError
}

/** Errors during response decoding or data parsing. */
sealed interface DecodeError : DomainError.Technical {
    /** Server returned an unexpected or malformed response body. */
    data object InvalidResponseFormat : DecodeError

    /** Data could not be deserialised into the expected type. */
    data object SerializationFailed : DecodeError

    /** Critical fields are missing from the response. */
    data class MissingFields(val fields: List<String>) : DecodeError
}

// ── Network ─────────────────────────────────────────────────────────────────

/** Errors from the network or transport layer. */
sealed interface NetworkError : DomainError.Technical {
    /** The request timed out. */
    data object Timeout : NetworkError

    /** No network connectivity is available. */
    data object NoConnectivity : NetworkError

    /** Server returned a non-retryable HTTP error. */
    data class HttpError(val code: Int, val message: String) : NetworkError

    /** Unexpected redirect or non-JSON response detected. */
    data object UnexpectedResponse : NetworkError

    /** The request was throttled (HTTP 429). */
    data class RateLimited(val retryAfterSeconds: Int?) : NetworkError
}

// ── Catalog Sync ───────────────────────────────────────────────────────────

/** Errors during catalog synchronization. */
sealed interface SyncError : DomainError.Technical {
    /** No network connectivity is available. */
    data object NoConnectivity : SyncError

    /** The request timed out. */
    data object Timeout : SyncError

    /** Server returned an HTTP error. */
    data class ServerError(val code: Int) : SyncError

    /** An unknown or unexpected error occurred. */
    data object Unknown : SyncError
}

// ── Library ─────────────────────────────────────────────────────────────────

/** Errors related to library operations. */
sealed interface LibraryError : DomainError.Business {
    /** The requested library operation is not supported server-side. */
    data object NotSupported : LibraryError

    /** The item is already in the target library collection. */
    data object AlreadyExists : LibraryError

    /** The item is not found in the target library collection. */
    data object NotFound : LibraryError
}
