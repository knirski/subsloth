package subsloth.core.network.media.mapper

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import subsloth.core.model.error.DecodeError

data class MappingResult<T>(val items: ImmutableList<T>, val errors: ImmutableList<DecodeError> = persistentListOf()) {
    val total: Int get() = items.size + errors.size
}
