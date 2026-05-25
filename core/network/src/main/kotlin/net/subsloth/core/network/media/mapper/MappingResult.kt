package net.subsloth.core.network.media.mapper

import kotlinx.collections.immutable.ImmutableList

data class MappingResult<T>(val items: ImmutableList<T>, val skipped: Int) {
    val total: Int get() = items.size + skipped
}
