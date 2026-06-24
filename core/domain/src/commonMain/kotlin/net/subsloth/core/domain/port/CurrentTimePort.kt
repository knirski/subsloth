package net.subsloth.core.domain.port

import kotlin.time.Instant

/**
 * Port for accessing the current time.
 *
 * Domain logic needs a clock abstraction to remain testable and
 * Android-free. Implementations are provided by the platform shell.
 *
 * The port exposes two methods: [now] returns a typed [Instant] for
 * time arithmetic (durations, comparisons, `kotlin.time` operators);
 * [millisNow] returns a `Long` for millisecond-precision epoch timestamps (cache
 * ages, retry-after timers, last-updated timestamps stored as
 * `Long` epoch-millisecond values). Most consumers want one or the other, not
 * both — pick the one that matches the stored value's type.
 */
interface CurrentTimePort {
    /** Returns the current time as a UTC [Instant] for type-safe arithmetic. */
    fun now(): Instant

    /** Returns the current time in epoch milliseconds. */
    fun millisNow(): Long
}
