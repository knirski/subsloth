package net.subsloth.core.network.media.mapper

/**
 * Holds the result of a bulk mapping operation, tracking both the
 * successfully mapped items and the count of items that were skipped
 * due to mapping failures.
 */
data class MappingResult<T>(val items: List<T>, val skipped: Int) {
    val total: Int get() = items.size + skipped
}
