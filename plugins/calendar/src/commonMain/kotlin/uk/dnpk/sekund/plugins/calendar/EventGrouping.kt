package uk.dnpk.sekund.plugins.calendar

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import uk.dnpk.sekund.sdk.calendar.CalendarEvent
import uk.dnpk.sekund.sdk.time.TimeFormat

/** A heading ("This Week", "Next Week", "March 2026") and the events under it, in order. */
data class EventGroup(
    val title: String,
    val events: List<CalendarEvent>,
)

/**
 * Turns the events from all sources into the grouped list the calendar shows. Pure logic, no UI, so it
 * can be tested with fixed dates.
 *
 * Steps:
 * 1. drop events that are over, and events hidden by a [TitleFilter],
 * 2. sort by start time, then by title (ignoring case),
 * 3. keep the first `maxEvents`,
 * 4. group them: events this week (including ones already under way) under "This Week", next week under
 *    "Next Week", anything later under its month, e.g. "March 2026". The week headings get the odd/even
 *    label from the settings, if one is set.
 *
 * Fixes two bugs from the old app: weeks are compared by their Monday's date instead of the ISO week
 * number (which went wrong around new year), and the odd/even label is shown and worked out per week.
 */
object EventGrouping {

    fun group(
        events: List<CalendarEvent>,
        now: LocalDateTime,
        settings: CalendarSettings,
        filters: List<TitleFilter>,
    ): List<EventGroup> {
        val shown = mutableListOf<CalendarEvent>()
        for (event in events) {
            if (isOver(event, now)) continue
            if (filters.any { filter -> filter.hides(event) }) continue
            shown.add(event)
        }
        shown.sortWith(eventOrder)
        val limited = shown.take(settings.maxEvents)

        // A LinkedHashMap keeps the headings in the order they were first added, which is date order.
        val groups = LinkedHashMap<String, MutableList<CalendarEvent>>()
        for (event in limited) {
            val heading = headingFor(event, now, settings)
            groups.getOrPut(heading) { mutableListOf() }.add(event)
        }
        return groups.map { (title, groupEvents) -> EventGroup(title, groupEvents) }
    }

    /** Start time first; events starting at the same moment are sorted by title, ignoring case. */
    private val eventOrder = Comparator<CalendarEvent> { a, b ->
        val byStart = a.start.compareTo(b.start)
        if (byStart != 0) {
            byStart
        } else {
            titleOf(a).compareTo(titleOf(b), ignoreCase = true)
        }
    }

    private fun titleOf(event: CalendarEvent): String = event.titleLines.joinToString("\n")

    /**
     * True once [event] has finished. An event with no length (start == end, e.g. "episode airs at 21:00")
     * stays until the end of its day, so it doesn't vanish the moment it starts.
     */
    fun isOver(event: CalendarEvent, now: LocalDateTime): Boolean {
        if (event.start == event.end) return event.start.date < now.date
        return event.end <= now
    }

    private fun headingFor(event: CalendarEvent, now: LocalDateTime, settings: CalendarSettings): String {
        val thisMonday = WeekParity.mondayOf(now.date)
        val nextMonday = thisMonday.plus(DatePeriod(days = 7))
        val eventMonday = WeekParity.mondayOf(event.start.date)

        // Events that have already started (even in an earlier week) are happening now, so "This Week".
        if (event.start < now || eventMonday == thisMonday) return weekHeading("This Week", thisMonday, settings)
        if (eventMonday == nextMonday) return weekHeading("Next Week", nextMonday, settings)
        return "${TimeFormat.monthName(event.start.month)} ${event.start.year}"
    }

    /** "This Week", or "This Week - Bins" if the week's odd/even label is set. */
    private fun weekHeading(base: String, monday: LocalDate, settings: CalendarSettings): String {
        val label = if (WeekParity.isEven(monday)) settings.evenWeekTitle else settings.oddWeekTitle
        if (label.isBlank()) return base
        return "$base - ${label.trim()}"
    }
}
