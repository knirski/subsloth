package subsloth.security

import android.app.Activity
import android.view.WindowManager

/**
 * Controls the `FLAG_SECURE` window policy for sensitive screens.
 *
 * `FLAG_SECURE` is applied only while credential-sensitive screens are visible:
 * login, auth repair, diagnostics, and logout cleanup confirmation.
 * It is NOT applied globally to catalog, details, library, settings,
 * or playback in v1.
 */
object SensitiveScreenPolicy {

    /**
     * Applies [WindowManager.LayoutParams.FLAG_SECURE] to the window,
     * preventing screenshots and screen-sharing on credential-sensitive screens.
     */
    fun enableSecureFlag(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /**
     * Removes [WindowManager.LayoutParams.FLAG_SECURE] from the window,
     * allowing screenshots on non-sensitive screens.
     */
    fun disableSecureFlag(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
