package net.subsloth.core.domain.port

interface StoragePort {
    fun availableBytes(): Long

    fun totalBytes(): Long

    fun reserveBytes(): Long
}
