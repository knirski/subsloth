package net.subsloth.core.ui

import net.subsloth.core.model.error.UiError
import kotlin.test.Test
import kotlin.test.assertEquals

class UiErrorResourcesTest {
    @Test
    fun auth_required_maps_to_AuthRequired_message() {
        val err = UiError.AuthRequired()
        assertEquals(UiErrorMessage.AuthRequired, err.toUiErrorMessage())
    }

    @Test
    fun not_found_maps_to_NotFound_message() {
        val err = UiError.NotFound()
        assertEquals(UiErrorMessage.NotFound, err.toUiErrorMessage())
    }

    @Test
    fun service_error_maps_to_ServiceError_message() {
        val err = UiError.ServiceError()
        assertEquals(UiErrorMessage.ServiceError, err.toUiErrorMessage())
    }

    @Test
    fun offline_maps_to_Offline_message() {
        val err = UiError.Offline()
        assertEquals(UiErrorMessage.Offline, err.toUiErrorMessage())
    }

    @Test
    fun unknown_maps_to_Unknown_message() {
        val err = UiError.Unknown()
        assertEquals(UiErrorMessage.Unknown, err.toUiErrorMessage())
    }

    @Test
    fun detail_does_not_affect_the_mapping() {
        val withDetail = UiError.NotFound(detail = "video 42")
        val withoutDetail = UiError.NotFound()
        assertEquals(withoutDetail.toUiErrorMessage(), withDetail.toUiErrorMessage())
    }
}
