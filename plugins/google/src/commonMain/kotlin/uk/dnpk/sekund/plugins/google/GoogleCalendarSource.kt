package uk.dnpk.sekund.plugins.google

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import uk.dnpk.sekund.sdk.calendar.CalendarEvent
import uk.dnpk.sekund.sdk.calendar.CalendarSource
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.context.RefreshPolicy
import uk.dnpk.sekund.sdk.context.now
import kotlin.time.Duration.Companion.minutes

/**
 * Upcoming events from every (or the chosen) calendar of a Google account, polled every 5 minutes, and
 * refreshed straight away when Google says something changed (with a Sekund server; see [GoogleCalendarPush]).
 *
 * Calendars are fetched in parallel. Token refreshes are handled by the signed-in client, so unlike the
 * old app nothing here touches credentials.
 */
class GoogleCalendarSource(private val context: InstanceContext<GoogleCalendarSettings>) : CalendarSource {
    private val settings = context.settings

    override val events: StateFlow<Loadable<List<CalendarEvent>>> =
        context.refresher.poll(RefreshPolicy(every = 5.minutes)) { fetchAll() }

    init {
        GoogleCalendarPush(context) { api -> chosenCalendars(api.calendars()) }.start()
    }

    private suspend fun fetchAll(): List<CalendarEvent> {
        val account = settings.account ?: throw IllegalStateException("Pick a Google account in this source's settings")
        val api = GoogleCalendarApi(context.auth.client(account))
        val timeZone = context.time.timeZone.value
        val now = context.time.now()

        val calendars = chosenCalendars(api.calendars())

        // coroutineScope + async ≈ Promise.all: fetch every calendar at once, and fail if any one fails
        // (the refresher then keeps showing the last good events).
        return coroutineScope {
            val perCalendar = calendars.map { calendar ->
                async {
                    val events = api.upcomingEvents(calendar.id, from = now, max = settings.maxEventsPerCalendar)
                    GoogleEventMapper.map(calendar, events, timeZone, settings.showRepeatsOnce)
                }
            }
            perCalendar.awaitAll().flatten()
        }
    }

    /** All calendars, or only those named in the settings (ignoring case). */
    private fun chosenCalendars(all: List<GoogleCalendarListEntry>): List<GoogleCalendarListEntry> {
        if (settings.calendars.isEmpty()) return all
        val wanted = settings.calendars.map { name -> name.trim().lowercase() }.toSet()
        val chosen = all.filter { calendar -> calendar.displayName.lowercase() in wanted }
        if (chosen.isEmpty()) {
            val names = all.joinToString(", ") { calendar -> calendar.displayName }
            throw IllegalStateException("None of the chosen calendars exist. This account has: $names")
        }
        return chosen
    }
}
