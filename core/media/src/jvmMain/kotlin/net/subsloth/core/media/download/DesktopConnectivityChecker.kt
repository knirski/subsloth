package net.subsloth.core.media.download

import net.subsloth.core.domain.port.ConnectivityPort

/**
 * Desktop (JVM) [ConnectivityPort].
 *
 * The JVM has no portable metered-network API (Android's
 * `ConnectivityManager`/`NetworkCapabilities` equivalents don't exist for
 * Java desktops), so this checker reports the desktop's flat network
 * model: online and unmetered. The user's Wi-Fi-only download preference
 * (`DownloadPolicy.canTransferOnNetwork` + `TransferPreference`) remains
 * the policy gate; with this checker it just means "user choice only".
 * Caveat: a desktop user on a tethered/metered connection who has
 * disabled Wi-Fi-only will not be blocked here.
 */
class DesktopConnectivityChecker : ConnectivityPort {
    override fun isOnline(): Boolean = true

    override fun isMetered(): Boolean = false
}
