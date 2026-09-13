package net.subsloth.player

import android.content.pm.ActivityInfo
import androidx.lifecycle.ViewModel

/**
 * Keeps the playback screen in sensor landscape and restores the orientation
 * that was requested before playback started.
 *
 * This is an activity-scoped [ViewModel], so it survives configuration
 * changes: a recreation in the player must not overwrite the captured
 * restore value. The value is only cleared when playback actually exits.
 *
 * Orientation values are read and written through callbacks so the policy is
 * unit-testable without an Android `Activity`.
 */
class PlayerOrientationViewModel : ViewModel() {
    private var restoreOrientation: Int? = null

    /** Captures the pre-playback orientation once, then locks sensor landscape. */
    fun enterPlayer(readOrientation: () -> Int, writeOrientation: (Int) -> Unit) {
        if (restoreOrientation == null) {
            restoreOrientation = readOrientation()
        }
        writeOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
    }

    /** Restores the captured orientation. A no-op when playback never entered. */
    fun exitPlayer(writeOrientation: (Int) -> Unit) {
        val orientation = restoreOrientation ?: return
        restoreOrientation = null
        writeOrientation(orientation)
    }
}
