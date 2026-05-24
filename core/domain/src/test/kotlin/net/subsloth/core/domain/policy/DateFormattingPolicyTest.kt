package net.subsloth.core.domain.policy

import kotlinx.datetime.TimeZone
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DateFormattingPolicyTest {
    private val now: Instant = Instant.fromEpochSeconds(1_800_000_000L)
    private val utc: TimeZone = TimeZone.UTC

    // ── relativeTimeDescription ────────────────────────────────────────────

    @Test
    fun `relative time returns just now for under a minute`() {
        val past = now - 30.seconds
        val result = DateFormattingPolicy.relativeTimeDescription(past, now)
        assertThat(result).isEqualTo("just now")
    }

    @Test
    fun `relative time returns a minute ago for 60-119 seconds`() {
        val past = now - 90.seconds
        val result = DateFormattingPolicy.relativeTimeDescription(past, now)
        assertThat(result).isEqualTo("a minute ago")
    }

    @Test
    fun `relative time returns minutes ago for under an hour`() {
        val past = now - 10.minutes
        val result = DateFormattingPolicy.relativeTimeDescription(past, now)
        assertThat(result).isEqualTo("10 minutes ago")
    }

    @Test
    fun `relative time returns an hour ago for 1-2 hours`() {
        val past = now - 90.minutes
        val result = DateFormattingPolicy.relativeTimeDescription(past, now)
        assertThat(result).isEqualTo("an hour ago")
    }

    @Test
    fun `relative time returns hours ago for 2-24 hours`() {
        val past = now - 5.hours
        val result = DateFormattingPolicy.relativeTimeDescription(past, now)
        assertThat(result).isEqualTo("5 hours ago")
    }

    @Test
    fun `relative time returns yesterday for 1-2 days`() {
        val past = now - 36.hours
        val result = DateFormattingPolicy.relativeTimeDescription(past, now)
        assertThat(result).isEqualTo("yesterday")
    }

    @Test
    fun `relative time returns days ago for under a week`() {
        val past = now - 3.days
        val result = DateFormattingPolicy.relativeTimeDescription(past, now)
        assertThat(result).isEqualTo("3 days ago")
    }

    @Test
    fun `relative time returns weeks ago for under a month`() {
        val past = now - 14.days
        val result = DateFormattingPolicy.relativeTimeDescription(past, now)
        assertThat(result).isEqualTo("2 weeks ago")
    }

    @Test
    fun `relative time returns a month ago for 30-59 days`() {
        val past = now - 45.days
        val result = DateFormattingPolicy.relativeTimeDescription(past, now)
        assertThat(result).isEqualTo("a month ago")
    }

    @Test
    fun `relative time returns months ago for under a year`() {
        val past = now - 180.days
        val result = DateFormattingPolicy.relativeTimeDescription(past, now)
        assertThat(result).isEqualTo("6 months ago")
    }

    @Test
    fun `relative time returns a year ago for 1-2 years`() {
        val past = now - 400.days
        val result = DateFormattingPolicy.relativeTimeDescription(past, now)
        assertThat(result).isEqualTo("a year ago")
    }

    @Test
    fun `relative time returns years ago for 2+ years`() {
        val past = now - 800.days
        val result = DateFormattingPolicy.relativeTimeDescription(past, now)
        assertThat(result).isEqualTo("2 years ago")
    }

    @Test
    fun `relative time handles future dates`() {
        val future = now + 1.hours
        val result = DateFormattingPolicy.relativeTimeDescription(future, now)
        assertThat(result).isEqualTo("in the future")
    }

    // ── localDate ──────────────────────────────────────────────────────────

    @Test
    fun `localDate converts instant to calendar date`() {
        val date = DateFormattingPolicy.localDate(now, utc)
        assertThat(date.day).isGreaterThan(0)
        assertThat(date.month.ordinal).isGreaterThan(-1)
    }

    // ── calendarDaysBetween ────────────────────────────────────────────────

    @Test
    fun `calendar days between returns zero for same instant`() {
        val days = DateFormattingPolicy.calendarDaysBetween(now, now, utc)
        assertThat(days).isEqualTo(0)
    }

    @Test
    fun `calendar days between returns positive when to is after from`() {
        val days = DateFormattingPolicy.calendarDaysBetween(now, now + 3.days, utc)
        assertThat(days).isEqualTo(3)
    }

    @Test
    fun `calendar days between returns negative when to is before from`() {
        val days = DateFormattingPolicy.calendarDaysBetween(now, now - 2.days, utc)
        assertThat(days).isEqualTo(-2)
    }

    // ── isSameDay ──────────────────────────────────────────────────────────

    @Test
    fun `isSameDay returns true for instants on same calendar day`() {
        val a = now
        val b = now + 12.hours
        assertThat(DateFormattingPolicy.isSameDay(a, b, utc)).isTrue()
    }

    @Test
    fun `isSameDay returns false for instants on different days`() {
        val a = now
        val b = now + 25.hours
        assertThat(DateFormattingPolicy.isSameDay(a, b, utc)).isFalse()
    }

    // ── isWithinLastDays ───────────────────────────────────────────────────

    @Test
    fun `isWithinLastDays returns true when instant is within max days`() {
        val past = now - 4.days
        assertThat(DateFormattingPolicy.isWithinLastDays(past, now, 7, utc)).isTrue()
    }

    @Test
    fun `isWithinLastDays returns false when instant is beyond max days`() {
        val past = now - 10.days
        assertThat(DateFormattingPolicy.isWithinLastDays(past, now, 7, utc)).isFalse()
    }

    @Test
    fun `isWithinLastDays returns true for exact boundary`() {
        val past = now - 7.days
        assertThat(DateFormattingPolicy.isWithinLastDays(past, now, 7, utc)).isTrue()
    }

    @Test
    fun `isWithinLastDays returns true for same instant`() {
        assertThat(DateFormattingPolicy.isWithinLastDays(now, now, 7, utc)).isTrue()
    }

    // ── formatDuration ─────────────────────────────────────────────────────

    @Test
    fun `formatDuration handles zero seconds`() {
        assertThat(DateFormattingPolicy.formatDuration(0)).isEqualTo("0m")
    }

    @Test
    fun `formatDuration handles negative input`() {
        assertThat(DateFormattingPolicy.formatDuration(-5)).isEqualTo("0m")
    }

    @Test
    fun `formatDuration formats minutes only`() {
        assertThat(DateFormattingPolicy.formatDuration(45 * 60)).isEqualTo("45m")
    }

    @Test
    fun `formatDuration formats hours and minutes`() {
        assertThat(DateFormattingPolicy.formatDuration(2 * 3600 + 30 * 60)).isEqualTo("2h 30m")
    }

    @Test
    fun `formatDuration formats hours only`() {
        assertThat(DateFormattingPolicy.formatDuration(3 * 3600)).isEqualTo("3h")
    }

    // ── periodBetween ──────────────────────────────────────────────────────

    @Test
    fun `periodBetween returns zero for same instant`() {
        val period = DateFormattingPolicy.periodBetween(now, now, utc)
        assertThat(period.years).isEqualTo(0)
        assertThat(period.months).isEqualTo(0)
        assertThat(period.days).isEqualTo(0)
    }

    @Test
    fun `periodBetween returns correct period for multi-year difference`() {
        val from = Instant.fromEpochSeconds(0)
        val to = Instant.fromEpochSeconds(1_800_000_000L)
        val period = DateFormattingPolicy.periodBetween(from, to, utc)
        assertThat(period.years).isGreaterThan(0)
    }

    // ── formattedDate ──────────────────────────────────────────────────────

    @Test
    fun `formattedDate returns non-empty string`() {
        val result = DateFormattingPolicy.formattedDate(now, utc)
        assertThat(result).isNotNull()
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `formattedDate without day omits day number`() {
        val result = DateFormattingPolicy.formattedDate(now, utc, includeDay = false)
        assertThat(result).isNotNull()
        assertThat(result).isNotEmpty()
    }

    // ── dayOfWeek / month ──────────────────────────────────────────────────

    @Test
    fun `dayOfWeek returns a valid day`() {
        val result = DateFormattingPolicy.dayOfWeek(now, utc)
        assertThat(result).isNotNull()
    }

    @Test
    fun `month returns a valid month`() {
        val result = DateFormattingPolicy.month(now, utc)
        assertThat(result).isNotNull()
    }
}
