package uk.dnpk.sekund.plugins.declarative

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uk.dnpk.sekund.sdk.context.TimePreferences
import uk.dnpk.sekund.sdk.time.TimeFormat
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round
import kotlin.time.Instant

/** Formatting helpers behind the template filters (`|number:1`, `|date`, `|time`). */
object ValueFormat {
    /** [value] with exactly [decimals] digits after the point, e.g. `fixed(3.14159, 2)` is `"3.14"`. */
    fun fixed(value: Double, decimals: Int): String {
        val places = decimals.coerceIn(0, 6)
        val factor = 10.0.pow(places)
        // Halves round up (7.25 -> 7.3), as people expect. kotlin.math.round would round them to even.
        val scaled = floor(abs(value) * factor + 0.5).toLong()
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        if (places == 0) return sign + scaled.toString()
        val divisor = factor.toLong()
        val whole = scaled / divisor
        val fraction = (scaled % divisor).toString().padStart(places, '0')
        return "$sign$whole.$fraction"
    }

    /** `"3"` for whole numbers, otherwise the number as Kotlin prints it. Used when no `|number` filter is given. */
    fun plain(value: Double): String {
        if (value == round(value) && abs(value) < 1e15) return value.toLong().toString()
        return value.toString()
    }

    /**
     * Reads a date/time in any of the usual API formats: ISO with an offset (`2025-03-03T09:00:00Z`),
     * ISO without one (taken as local time), a plain date, or a Unix timestamp in seconds or milliseconds.
     */
    fun parseDateTime(text: String, timeZone: TimeZone): LocalDateTime? {
        val trimmed = text.trim()
        val number = trimmed.toLongOrNull()
        if (number != null) {
            // Timestamps after the year 5138 in seconds are really milliseconds.
            val instant = if (number > 100_000_000_000L) Instant.fromEpochMilliseconds(number) else Instant.fromEpochSeconds(number)
            return instant.toLocalDateTime(timeZone)
        }
        try {
            return Instant.parse(trimmed).toLocalDateTime(timeZone)
        } catch (_: IllegalArgumentException) {
            // not an instant with an offset; try the next format
        }
        try {
            return LocalDateTime.parse(trimmed)
        } catch (_: IllegalArgumentException) {
            // not a local date-time either
        }
        try {
            val date = LocalDate.parse(trimmed)
            return LocalDateTime(date.year, date.month, date.day, 0, 0)
        } catch (_: IllegalArgumentException) {
            return null
        }
    }

    /** A short date like `"Mon 3 Mar"`. */
    fun shortDate(dateTime: LocalDateTime): String {
        val day = TimeFormat.dayName(dateTime.dayOfWeek).take(3)
        val month = TimeFormat.monthName(dateTime.month).take(3)
        return "$day ${dateTime.day} $month"
    }

    /** A time in the person's preferred style, e.g. `"14:05"` or `"2:05 PM"`. */
    fun time(dateTime: LocalDateTime, preferences: TimePreferences): String = TimeFormat.shortTime(dateTime, preferences)
}
