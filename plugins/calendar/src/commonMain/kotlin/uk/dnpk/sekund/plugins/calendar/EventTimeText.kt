package uk.dnpk.sekund.plugins.calendar

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import uk.dnpk.sekund.sdk.calendar.CalendarEvent
import uk.dnpk.sekund.sdk.context.TimePreferences
import uk.dnpk.sekund.sdk.time.TimeFormat
import kotlin.time.Duration.Companion.hours

/** The "when" text under an event's title. [secondLine] is only used by long events that span days. */
data class EventWhen(
    val firstLine: String,
    val secondLine: String? = null,
)

/**
 * Describes when an event happens, e.g. (with a 24 hour clock):
 *
 * | Event                              | Text                                            |
 * |------------------------------------|-------------------------------------------------|
 * | all day, one day                   | `Friday 20th`                                   |
 * | all day, several days              | `Friday 20th — Sunday 22nd` (`— Tuesday 1st Jul` across months) |
 * | 24 hours or more                   | `Today (14:00) -` / `Friday 20th (10:00)`       |
 * | no length                          | `Today (09:30)`                                 |
 * | anything else                      | `Tomorrow (14:00 - 15:30)`                      |
 *
 * Days are "Today" and "Tomorrow" where they apply. Times follow the person's 12/24 hour preference.
 */
object EventTimeText {

    fun describe(event: CalendarEvent, today: LocalDate, preferences: TimePreferences): EventWhen {
        val start = event.start
        val end = event.end
        val startDay = dayLabel(start.date, today, withMonth = false)

        if (isWholeDays(event)) {
            val lastDay = lastDayOf(event)
            if (lastDay <= start.date) return EventWhen(startDay)

            val withMonth = lastDay.month != start.month || lastDay.year != start.year
            return EventWhen("$startDay — ${dayLabel(lastDay, today, withMonth)}")
        }

        val startTime = TimeFormat.shortTime(start, preferences)
        val endTime = TimeFormat.shortTime(end, preferences)

        if (start.date != end.date && lastsADayOrMore(start, end)) {
            val withMonth = end.month != start.month || end.year != start.year
            return EventWhen(
                firstLine = "$startDay ($startTime) -",
                secondLine = "${dayLabel(end.date, today, withMonth)} ($endTime)",
            )
        }

        if (start == end) return EventWhen("$startDay ($startTime)")
        return EventWhen("$startDay ($startTime - $endTime)")
    }

    /**
     * True for events that cover whole days: flagged as all-day by their source, or starting and ending
     * exactly at midnight (some sources don't set the flag).
     */
    private fun isWholeDays(event: CalendarEvent): Boolean {
        if (event.allDay) return true
        val midnight = LocalTime(0, 0)
        return event.start.time == midnight && event.end.time == midnight
    }

    /**
     * The last day a whole-day event covers. Calendar APIs give the end as midnight at the start of the
     * *next* day, so an event ending at 00:00 on the 4th really finishes on the 3rd.
     */
    private fun lastDayOf(event: CalendarEvent): LocalDate {
        if (event.end.time == LocalTime(0, 0) && event.end.date > event.start.date) {
            return event.end.date.minus(DatePeriod(days = 1))
        }
        return event.end.date
    }

    private fun lastsADayOrMore(start: LocalDateTime, end: LocalDateTime): Boolean {
        // Converted with UTC only to measure the gap; the times are already local.
        val length = end.toInstant(TimeZone.UTC) - start.toInstant(TimeZone.UTC)
        return length >= 24.hours
    }

    /** "Today", "Tomorrow", or "Friday 20th" ("Friday 20th Jun" with [withMonth]). */
    fun dayLabel(date: LocalDate, today: LocalDate, withMonth: Boolean): String {
        if (date == today) return "Today"
        if (date == today.plus(DatePeriod(days = 1))) return "Tomorrow"

        val text = "${TimeFormat.dayName(date.dayOfWeek)} ${date.day}${TimeFormat.ordinalSuffix(date.day)}"
        if (!withMonth) return text
        return "$text ${TimeFormat.monthName(date.month).take(3)}"
    }
}
