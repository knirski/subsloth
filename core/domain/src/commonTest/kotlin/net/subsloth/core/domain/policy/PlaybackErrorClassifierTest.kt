package net.subsloth.core.domain.policy

import net.subsloth.core.model.error.AuthError
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.DomainError
import net.subsloth.core.model.error.MediaError
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.SyncError
import net.subsloth.core.model.playback.PlaybackError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PlaybackErrorClassifierTest {
    @Test
    fun http_401_maps_to_AuthFailure() {
        val err = NetworkError.HttpError(401, "Unauthorized")
        val classified = PlaybackErrorClassifier.classify(err)
        assertEquals(PlaybackError.AuthFailure, classified)
    }

    @Test
    fun http_403_maps_to_StreamUrlExpired() {
        val err = NetworkError.HttpError(403, "Forbidden")
        val classified = PlaybackErrorClassifier.classify(err)
        assertEquals(PlaybackError.StreamUrlExpired, classified)
    }

    @Test
    fun http_500_maps_to_Recoverable_with_cause() {
        val err = NetworkError.HttpError(500, "Internal Server Error")
        val classified = PlaybackErrorClassifier.classify(err)
        assertIs<PlaybackError.Recoverable>(classified)
        assertSame(err, classified.cause)
    }

    @Test
    fun http_404_maps_to_Recoverable_with_cause() {
        val err = NetworkError.HttpError(404, "Not Found")
        val classified = PlaybackErrorClassifier.classify(err)
        assertIs<PlaybackError.Recoverable>(classified)
        assertSame(err, classified.cause)
    }

    @Test
    fun timeout_maps_to_Recoverable_with_cause() {
        val err = NetworkError.Timeout
        val classified = PlaybackErrorClassifier.classify(err)
        assertIs<PlaybackError.Recoverable>(classified)
        assertSame(err, classified.cause)
    }

    @Test
    fun auth_business_error_maps_to_Recoverable() {
        val err = AuthError.SessionExpired
        val classified = PlaybackErrorClassifier.classify(err)
        assertIs<PlaybackError.Recoverable>(classified)
        assertSame(err, classified.cause)
    }

    @Test
    fun media_business_error_maps_to_Recoverable() {
        val err = MediaError.GeoRestricted
        val classified = PlaybackErrorClassifier.classify(err)
        assertIs<PlaybackError.Recoverable>(classified)
        assertSame(err, classified.cause)
    }

    @Test
    fun sync_error_maps_to_Recoverable() {
        val err = SyncError.Timeout
        val classified = PlaybackErrorClassifier.classify(err)
        assertIs<PlaybackError.Recoverable>(classified)
        assertSame(err, classified.cause)
    }

    @Test
    fun decode_error_maps_to_Recoverable() {
        val err: DomainError = DecodeError.SerializationFailed
        val classified = PlaybackErrorClassifier.classify(err)
        assertIs<PlaybackError.Recoverable>(classified)
        assertSame(err, classified.cause)
    }
}
