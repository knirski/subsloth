package net.subsloth.core.domain.port

import kotlin.time.Instant

/**
 * Port for accessing the current time.
 *
 * Pure domain logic needs a clock abstraction to remain testable and
 * Android-free. Implementations are provided by the Android shell.
 */
interface ClockPort {
    /** Returns the current time as a UTC instant. */
    fun now(): Instant
}
