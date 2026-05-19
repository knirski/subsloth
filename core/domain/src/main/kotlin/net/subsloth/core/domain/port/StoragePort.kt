package net.subsloth.core.domain.port

/**
 * Port for checking storage availability.
 *
 * Implementations are provided by the Android shell.
 */
interface StoragePort {
    /** Returns the available free space in bytes on the downloads storage. */
    fun availableBytes(): Long

    /** Returns the minimum reserve bytes that must remain free. */
    fun reserveBytes(): Long
}
