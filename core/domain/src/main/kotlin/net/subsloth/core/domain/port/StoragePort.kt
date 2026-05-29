package net.subsloth.core.domain.port

/** Port for querying device storage capacity and reservation levels. */
interface StoragePort {
    /** Available free space in bytes on the downloads storage device. */
    fun availableBytes(): Long

    /** Total capacity in bytes of the downloads storage device. */
    fun totalBytes(): Long

    /** Minimum bytes that must remain free after a download operation. */
    fun reserveBytes(): Long
}
