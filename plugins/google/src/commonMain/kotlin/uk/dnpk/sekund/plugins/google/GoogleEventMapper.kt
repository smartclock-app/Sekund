package uk.dnpk.sekund.plugins.google

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uk.dnpk.sekund.sdk.calendar.CalendarEvent
import uk.dnpk.sekund.sdk.settings.ColorHex
import kotlin.time.Instant

/** Turns Google's events into Sekund [CalendarEvent]s. Pure logic, so it's tested without any network. */
object GoogleEventMapper {

    /**
     * Converts the events of one [calendar]. Times are converted into [timeZone] (the device's).
     *
     * Skipped: cancelled occurrences, events without a usable start or end, and (with [showRepeatsOnce])
     * every event whose title already appeared earlier in this calendar.
     */
    fun map(
        calendar: GoogleCalendarListEntry,
        events: List<GoogleEvent>,
        timeZone: TimeZone,
        showRepeatsOnce: Boolean,
    ): List<CalendarEvent> {
        val colorText = calendar.backgroundColor
        val color = if (colorText == null) null else ColorHex.parse(colorText)
        val seenTitles = mutableSetOf<String>()
        val result = mutableListOf<CalendarEvent>()

        for (event in events) {
            if (event.status == "cancelled") continue
            val title = event.summary ?: "No title"
            if (showRepeatsOnce) {
                if (title in seenTitles) continue
                seenTitles.add(title)
            }

            val start = toLocal(event.start, timeZone) ?: continue
            val end = toLocal(event.end, timeZone) ?: continue

            result.add(
                CalendarEvent(
                    // Event ids are only unique within a calendar, so include the calendar's id.
                    id = "${calendar.id}/${event.id}",
                    titleLines = listOf(title),
                    start = start,
                    end = end,
                    allDay = event.start.date != null,
                    color = color,
                ),
            )
        }
        return result
    }

    /** An all-day date becomes midnight; a timed one is converted from its UTC offset into [timeZone]. */
    private fun toLocal(time: GoogleEventTime, timeZone: TimeZone): LocalDateTime? {
        try {
            val date = time.date
            if (date != null) return LocalDateTime(LocalDate.parse(date), LocalTime(0, 0))
            val dateTime = time.dateTime ?: return null
            return Instant.parse(dateTime).toLocalDateTime(timeZone)
        } catch (error: IllegalArgumentException) {
            return null // a date Google shouldn't send; skip the event rather than fail the calendar
        }
    }
}
