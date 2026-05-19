package net.subsloth.core.domain.port

/**
 * Port for checking network connectivity status.
 *
 * Implementations are provided by the Android shell.
 */
interface ConnectivityPort {
    /** Returns `true` when the device has network connectivity. */
    fun isOnline(): Boolean

    /** Returns `true` when the device is connected to a metered network. */
    fun isMetered(): Boolean
}
