package uk.dnpk.sekund.sdk.calendar

import kotlinx.coroutines.flow.StateFlow
import uk.dnpk.sekund.sdk.context.Loadable

/**
 * Provides events to calendar widgets (Google Calendar, Trakt air dates, ...).
 *
 * Each source refreshes on its own schedule, so build [events] with `context.refresher.poll { ... }`.
 * If a source fails, calendar widgets keep showing its last good events and the others are unaffected.
 */
interface CalendarSource {
    /** Upcoming events, oldest first. Past events may be included; widgets filter them out. */
    val events: StateFlow<Loadable<List<CalendarEvent>>>
}
