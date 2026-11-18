package uk.dnpk.sekund.plugins.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.dnpk.sekund.sdk.calendar.CalendarEvent
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.context.LinkedInstance
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.widget.Widget
import uk.dnpk.sekund.sdk.widget.WidgetLocation
import uk.dnpk.sekund.ui.components.MessageCard
import uk.dnpk.sekund.ui.components.SekundCard
import uk.dnpk.sekund.ui.theme.Sekund

/**
 * The calendar: events from the chosen sources, grouped into "This Week", "Next Week" and months,
 * one card per group.
 *
 * The widget doesn't fetch anything itself. Each source polls on its own schedule; the widget just
 * follows their latest events (see [merged]) and regroups them every minute, so "Today" turns into
 * "Tomorrow" at midnight and finished events drop off without a refresh.
 */
class CalendarWidget(private val context: InstanceContext<CalendarSettings>) : Widget {
    private val settings = context.settings

    private val filters: List<TitleFilter> = settings.titleFilters.map { text -> TitleFilter(text) }

    /**
     * All sources' events put together. Updates whenever any source loads new events or is recreated.
     * Internal (not private) so tests can check it.
     */
    internal val merged: StateFlow<MergedEvents> = mergeSources()

    init {
        for (filter in filters) {
            if (filter.isPlainTextOnly) {
                context.log.warn("'${filter.text}' isn't a valid regular expression; only titles exactly equal to it are hidden")
            }
        }
    }

    private fun mergeSources(): StateFlow<MergedEvents> {
        val initial = MergedEvents(events = emptyList(), stillLoading = settings.sources.size, problems = emptyList())
        // Nothing to follow: a flow that never changes.
        if (settings.sources.isEmpty()) return MutableStateFlow(initial)

        val perSource: List<Flow<Pair<String, Loadable<List<CalendarEvent>>>>> =
            settings.sources.map { sourceId -> followSource(sourceId) }

        // combine ≈ Promise.all, but live: re-runs whenever any one source's state changes.
        return combine(perSource) { states -> MergedEvents.of(states.toList()) }
            .stateIn(context.scope, SharingStarted.Eagerly, initial)
    }

    /**
     * The events of source [sourceId], following it as it's recreated. If the instance is missing or
     * broken, this reports that as a failure for this source only.
     */
    @OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest is stable in practice, just not marked so
    private fun followSource(sourceId: String): Flow<Pair<String, Loadable<List<CalendarEvent>>>> {
        val linked = context.instances.watch(ExtensionPoints.CalendarSources, sourceId)
        // flatMapLatest: whenever the source instance changes, stop listening to the old one's events
        // and start listening to the new one's (like switchMap in RxJS).
        return linked.flatMapLatest { instance ->
            when (instance) {
                is LinkedInstance.Running -> instance.value.events.map { state -> sourceId to state }
                is LinkedInstance.Unavailable -> flowOf(sourceId to Loadable.Failed(instance.reason))
            }
        }
    }

    @Composable
    override fun Content(location: WidgetLocation) {
        val now by context.time.everyMinute.collectAsState()
        val preferences by context.time.preferences.collectAsState()
        val state by merged.collectAsState()

        if (settings.sources.isEmpty()) {
            MessageCard(title = "No calendars chosen", message = "Pick some in this calendar's settings.")
            return
        }

        // Regrouping is cheap, but there's no need to redo it on every recomposition.
        val groups = remember(state.events, now) { EventGrouping.group(state.events, now, settings, filters) }

        Column(verticalArrangement = Arrangement.spacedBy(Sekund.theme.spacing)) {
            if (state.problems.isNotEmpty()) {
                MessageCard(
                    title = "Some events may be missing",
                    message = state.problems.joinToString("\n"),
                    barColor = Sekund.theme.colors.warning,
                )
            }

            if (groups.isEmpty()) {
                val message = if (state.stillLoading > 0) "Loading events…" else "No upcoming events"
                MessageCard(title = message)
            }

            for (group in groups) {
                SekundCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(group.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        for (event in group.events) {
                            EventRow(event, EventTimeText.describe(event, now.date, preferences))
                        }
                    }
                }
            }
        }
    }
}
