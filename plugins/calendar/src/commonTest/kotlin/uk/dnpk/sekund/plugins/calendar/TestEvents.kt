package uk.dnpk.sekund.plugins.calendar

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import uk.dnpk.sekund.sdk.calendar.CalendarEvent

/** Wednesday 18 June 2025, 10:00: "now" in most tests (the same date the old app's tests used). */
val WEDNESDAY_10AM = LocalDateTime(2025, 6, 18, 10, 0)

/** Parses "2025-06-18T14:00" into a LocalDateTime, to keep tests short. */
fun at(text: String): LocalDateTime = LocalDateTime.parse(text)

/** An event with sensible defaults. Without an [end] it lasts one hour (so don't start it at 23:00). */
fun event(
    id: String,
    start: LocalDateTime,
    end: LocalDateTime? = null,
    title: String = "Event $id",
    allDay: Boolean = false,
    titleLines: List<String> = listOf(title),
): CalendarEvent {
    var finish = end
    if (finish == null) {
        finish = LocalDateTime(start.date, LocalTime(start.hour + 1, start.minute))
    }
    return CalendarEvent(id = id, titleLines = titleLines, start = start, end = finish, allDay = allDay)
}

/** Settings with no labels, no filters and room for 50 events. */
fun settings(
    maxEvents: Int = 50,
    oddWeekTitle: String = "",
    evenWeekTitle: String = "",
): CalendarSettings = CalendarSettings(
    sources = emptyList(),
    maxEvents = maxEvents,
    titleFilters = emptyList(),
    oddWeekTitle = oddWeekTitle,
    evenWeekTitle = evenWeekTitle,
)
