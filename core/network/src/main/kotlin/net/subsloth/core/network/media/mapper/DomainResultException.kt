package net.subsloth.core.network.media.mapper

import net.subsloth.core.model.error.DomainError

/**
 * Wraps a [DomainError] as a [Throwable] so it can be used with
 * [kotlin.Result.failure] across module boundaries where domain errors
 * are not themselves Throwable subtypes.
 */
class DomainResultException(val domainError: DomainError) : Exception(domainError.toString())
