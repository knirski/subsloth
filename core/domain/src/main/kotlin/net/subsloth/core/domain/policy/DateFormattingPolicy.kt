@file:Suppress("TooManyFunctions")

package net.subsloth.core.domain.policy

import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.periodUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Pure policies for date/time formatting and comparison.
 *
 * All functions are deterministic given the same [now] and [timeZone] inputs.
 * No Android framework or side effects.
 */
object DateFormattingPolicy {
    private const val SECONDS_PER_MINUTE: Long = 60
    private const val SECONDS_PER_HOUR: Long = 3_600
    private const val SECONDS_PER_DAY: Long = 86_400
    private const val SECONDS_PER_WEEK: Long = 604_800
    private const val SECONDS_PER_MONTH: Long = 2_592_000
    private const val SECONDS_PER_YEAR: Long = 31_536_000

    /**
     * Returns a human-readable relative time description like "just now",
     * "3 minutes ago", "yesterday", "2 weeks ago".
     *
     * This function is purely duration-based and does not depend on calendar
     * or time-zone. For calendar-aware relative descriptions, use
     * [calendarDaysBetween] or [formattedDate].
     */
    fun relativeTimeDescription(
        instant: Instant,
        now: Instant,
    ): String {
        val diffSeconds = (now - instant).inWholeSeconds

        return when {
            diffSeconds < 0 -> "in the future"
            diffSeconds < SECONDS_PER_MINUTE -> "just now"
            diffSeconds < 2 * SECONDS_PER_MINUTE -> "a minute ago"
            diffSeconds < SECONDS_PER_HOUR -> "${diffSeconds / SECONDS_PER_MINUTE} minutes ago"
            diffSeconds < 2 * SECONDS_PER_HOUR -> "an hour ago"
            diffSeconds < SECONDS_PER_DAY -> "${diffSeconds / SECONDS_PER_HOUR} hours ago"
            diffSeconds < 2 * SECONDS_PER_DAY -> "yesterday"
            diffSeconds < SECONDS_PER_WEEK -> "${diffSeconds / SECONDS_PER_DAY} days ago"
            diffSeconds < 2 * SECONDS_PER_WEEK -> "a week ago"
            diffSeconds < SECONDS_PER_MONTH -> "${diffSeconds / SECONDS_PER_WEEK} weeks ago"
            diffSeconds < 2 * SECONDS_PER_MONTH -> "a month ago"
            diffSeconds < SECONDS_PER_YEAR -> "${diffSeconds / SECONDS_PER_MONTH} months ago"
            diffSeconds < 2 * SECONDS_PER_YEAR -> "a year ago"
            else -> "${diffSeconds / SECONDS_PER_YEAR} years ago"
        }
    }

    /**
     * Converts [instant] to [LocalDate] in the given [timeZone].
     */
    fun localDate(
        instant: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): LocalDate = instant.toLocalDateTime(timeZone).date

    /**
     * Converts [instant] to [LocalTime] in the given [timeZone].
     */
    fun localTime(
        instant: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): LocalTime = instant.toLocalDateTime(timeZone).time

    /**
     * Converts [instant] to [LocalDateTime] in the given [timeZone].
     */
    fun localDateTime(
        instant: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): LocalDateTime = instant.toLocalDateTime(timeZone)

    /**
     * Returns the number of calendar days between [from] and [to].
     * Positive when [to] is after [from].
     */
    fun calendarDaysBetween(
        from: Instant,
        to: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Int = localDate(from, timeZone).daysUntil(localDate(to, timeZone))

    /**
     * Returns `true` when [a] and [b] fall on the same calendar day in [timeZone].
     */
    fun isSameDay(
        a: Instant,
        b: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Boolean = calendarDaysBetween(a, b, timeZone) == 0

    /**
     * Returns `true` when [instant] is within the last [maxDays] calendar days
     * relative to [now].
     */
    fun isWithinLastDays(
        instant: Instant,
        now: Instant,
        maxDays: Int,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Boolean {
        val days = calendarDaysBetween(instant, now, timeZone)
        return days in 0..maxDays
    }

    /**
     * Formats a duration in seconds to a display string like "1h 23m" or "45m".
     */
    fun formatDuration(seconds: Long): String {
        if (seconds < 0) return "0m"
        val hours = seconds / SECONDS_PER_HOUR
        val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    /**
     * Returns the [DayOfWeek] for [instant] in the given [timeZone].
     */
    fun dayOfWeek(
        instant: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): DayOfWeek = localDate(instant, timeZone).dayOfWeek

    /**
     * Returns the [Month] for [instant] in the given [timeZone].
     */
    fun month(
        instant: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Month = localDate(instant, timeZone).month

    /**
     * Builds a [DateTimePeriod] representing the difference between [from] and [to]
     * in calendar terms (years, months, days, hours, etc.).
     */
    fun periodBetween(
        from: Instant,
        to: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): DateTimePeriod = from.periodUntil(to, timeZone)

    /**
     * Returns a descriptive string for the age of content, e.g. "2023",
     * "Jan 2024", "March 15, 2025".
     */
    fun formattedDate(
        instant: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        includeDay: Boolean = true,
    ): String {
        val date = localDate(instant, timeZone)
        val monthName =
            date.month.name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
        return if (includeDay) {
            "$monthName ${date.day}, ${date.year}"
        } else {
            "$monthName ${date.year}"
        }
    }
}
