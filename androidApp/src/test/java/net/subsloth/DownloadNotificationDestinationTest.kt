package net.subsloth

import net.subsloth.core.media.download.DownloadForegroundService
import net.subsloth.core.ui.DownloadsKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DownloadNotificationDestinationTest {

    @Test
    fun `open downloads action maps to the downloads destination`() {
        val destination = destinationForAction(DownloadForegroundService.ACTION_OPEN_DOWNLOADS)

        assertEquals(DownloadsKey, destination)
    }

    @Test
    fun `other actions map to no destination`() {
        assertNull(destinationForAction(null))
        assertNull(destinationForAction("android.intent.action.MAIN"))
        assertNull(destinationForAction("net.subsloth.action.SOMETHING_ELSE"))
    }
}
