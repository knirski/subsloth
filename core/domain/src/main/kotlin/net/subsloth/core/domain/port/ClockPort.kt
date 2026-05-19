package net.subsloth.core.domain.port

/**
 * Port for accessing the current time.
 *
 * Pure domain logic needs a clock abstraction to remain testable and
 * Android-free. Implementations are provided by the Android shell.
 */
interface ClockPort {
    /** Returns the current time in epoch seconds. */
    fun currentEpochSeconds(): Long
}
