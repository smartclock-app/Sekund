package uk.dnpk.sekund.sdk.time

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import uk.dnpk.sekund.sdk.context.TimePreferences

/**
 * Formatting for times and dates the way Sekund shows them (English). Shared so every plugin agrees.
 *
 * Results are split into parts, e.g. the ordinal "rd" separate from the "3", so the UI can style
 * them differently (the ordinal is shown smaller and raised).
 */
object TimeFormat {
    /** "st", "nd", "rd" or "th" for a day of the month (1st, 2nd, 3rd, 4th, 11th, 21st, 22nd...). */
    fun ordinalSuffix(day: Int): String {
        if (day in 11..13) return "th"
        return when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }

    /**
     * Splits [time] into the parts a clock shows, following [preferences]:
     * 24 hour gives "21:05" with no AM/PM; 12 hour gives "09:05" and "PM".
     */
    fun clock(time: LocalDateTime, preferences: TimePreferences): ClockText {
        val minutes = twoDigits(time.minute)
        val seconds = if (preferences.showSeconds) twoDigits(time.second) else null

        if (preferences.use24Hour) {
            return ClockText(hoursAndMinutes = "${twoDigits(time.hour)}:$minutes", seconds = seconds, amPm = null)
        }

        var hour12 = time.hour % 12
        if (hour12 == 0) hour12 = 12
        val amPm = if (time.hour < 12) "AM" else "PM"
        return ClockText(hoursAndMinutes = "${twoDigits(hour12)}:$minutes", seconds = seconds, amPm = amPm)
    }

    /** Short time for event lists: "21:05" or "9:05 PM". */
    fun shortTime(time: LocalDateTime, preferences: TimePreferences): String {
        if (preferences.use24Hour) return "${twoDigits(time.hour)}:${twoDigits(time.minute)}"
        var hour12 = time.hour % 12
        if (hour12 == 0) hour12 = 12
        val amPm = if (time.hour < 12) "AM" else "PM"
        return "$hour12:${twoDigits(time.minute)} $amPm"
    }

    /** The parts of "Monday 3rd March 2025". */
    fun longDate(date: LocalDate): LongDateText = LongDateText(
        weekday = dayName(date.dayOfWeek),
        day = date.day.toString(),
        ordinal = ordinalSuffix(date.day),
        month = monthName(date.month),
        year = date.year.toString(),
    )

    /** "Monday", "Tuesday", ... */
    fun dayName(day: DayOfWeek): String = capitalise(day.name)

    /** "January", "February", ... */
    fun monthName(month: Month): String = capitalise(month.name)

    private fun capitalise(enumName: String): String = enumName.lowercase().replaceFirstChar { char -> char.uppercase() }

    private fun twoDigits(number: Int): String = number.toString().padStart(2, '0')
}

/** A clock reading split into parts. [seconds] and [amPm] are null when not shown. */
data class ClockText(
    val hoursAndMinutes: String,
    val seconds: String?,
    val amPm: String?,
)

/** A long date split into parts, e.g. "Monday", "3", "rd", "March", "2025". */
data class LongDateText(
    val weekday: String,
    val day: String,
    val ordinal: String,
    val month: String,
    val year: String,
)
