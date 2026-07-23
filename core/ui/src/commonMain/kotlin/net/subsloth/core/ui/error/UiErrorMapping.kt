package net.subsloth.core.ui.error

import net.subsloth.core.model.error.AuthError
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.DomainError
import net.subsloth.core.model.error.DownloadError
import net.subsloth.core.model.error.LibraryError
import net.subsloth.core.model.error.MediaError
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.PaymentLimitError
import net.subsloth.core.model.error.QualityError
import net.subsloth.core.model.error.SyncError
import net.subsloth.core.model.error.UiError
import net.subsloth.core.network.error.NetworkErrorClassifier
import net.subsloth.core.network.media.client.ResponseValidationException

fun Throwable.toUiError(): UiError {
    val message = this.message.orEmpty()
    if (this is ResponseValidationException) return UiError.ServiceError(message)
    return when (val networkError = NetworkErrorClassifier.classifyToNetwork(this)) {
        is NetworkError.Timeout -> UiError.Offline(message)

        is NetworkError.NoConnectivity -> UiError.Offline(message)

        is NetworkError.HttpError -> when (networkError.code) {
            401 -> UiError.AuthRequired(message)
            404 -> UiError.NotFound(message)
            in 500..599 -> UiError.ServiceError(message)
            else -> UiError.Unknown(message)
        }

        is NetworkError.RateLimited -> UiError.ServiceError(message)

        is NetworkError.UnexpectedResponse -> UiError.Unknown(message)
    }
}

/**
 * Maps a typed [DomainError] to a [UiError] for presentation.
 *
 * Exhaustive over all DomainError variants — the compiler flags this
 * if a new variant is added.
 */
fun DomainError.toUiError(): UiError = when (this) {
    // ── Authentication ──────────────────────────────────────────────
    is AuthError.InvalidCredentials -> UiError.AuthRequired()

    is AuthError.SessionExpired -> UiError.AuthRequired()

    is AuthError.AccountSuspended -> UiError.AuthRequired()

    // ── Payment / Limits ───────────────────────────────────────────
    is PaymentLimitError.ConcurrentStreamLimit -> UiError.ServiceError()

    is PaymentLimitError.SubscriptionRequired -> UiError.ServiceError()

    // ── Media availability ─────────────────────────────────────────
    is MediaError.Unavailable -> UiError.NotFound()

    is MediaError.NotFound -> UiError.NotFound()

    is MediaError.GeoRestricted -> UiError.NotFound()

    is MediaError.Expired -> UiError.NotFound()

    is MediaError.Upcoming -> UiError.NotFound()

    // ── Download ───────────────────────────────────────────────────
    is DownloadError.InsufficientStorage -> UiError.NotFound()

    is DownloadError.MissingSubtitle -> UiError.NotFound()

    is DownloadError.QueueFull -> UiError.NotFound()

    is DownloadError.NeedsWifi -> UiError.NotFound()

    is DownloadError.MissingLocalFile -> UiError.NotFound()

    is DownloadError.AmbiguousQuality -> UiError.NotFound()

    // ── Quality ────────────────────────────────────────────────────
    is QualityError.Unsupported -> UiError.NotFound()

    is QualityError.NoFallback -> UiError.NotFound()

    is QualityError.BelowMinimum -> UiError.NotFound()

    // ── Decode ─────────────────────────────────────────────────────
    is DecodeError.InvalidResponseFormat -> UiError.ServiceError()

    is DecodeError.SerializationFailed -> UiError.ServiceError()

    is DecodeError.MissingFields -> UiError.ServiceError()

    // ── Network ────────────────────────────────────────────────────
    is NetworkError.Timeout -> UiError.Offline()

    is NetworkError.NoConnectivity -> UiError.Offline()

    is NetworkError.HttpError -> when (code) {
        401 -> UiError.AuthRequired()
        404 -> UiError.NotFound()
        in 500..599 -> UiError.ServiceError()
        else -> UiError.Unknown()
    }

    is NetworkError.UnexpectedResponse -> UiError.Unknown()

    is NetworkError.RateLimited -> UiError.ServiceError()

    // ── Sync ──────────────────────────────────────────────────────
    is SyncError.NoConnectivity -> UiError.Offline()

    is SyncError.Timeout -> UiError.Offline()

    is SyncError.ServerError -> UiError.ServiceError()

    is SyncError.Unknown -> UiError.Unknown()

    // ── Library ────────────────────────────────────────────────────
    is LibraryError.NotSupported -> UiError.ServiceError()

    is LibraryError.AlreadyExists -> UiError.NotFound()

    is LibraryError.NotFound -> UiError.NotFound()
}
