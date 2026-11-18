package uk.dnpk.sekund.plugins.calendar

import uk.dnpk.sekund.sdk.calendar.CalendarEvent
import uk.dnpk.sekund.sdk.context.Loadable

/**
 * The events of all a calendar's sources put together, plus anything worth warning about.
 *
 * @property events every source's latest good events, unsorted.
 * @property stillLoading how many sources haven't loaded anything yet.
 * @property problems one sentence per source that failed or is showing old data, for a warning card.
 */
data class MergedEvents(
    val events: List<CalendarEvent>,
    val stillLoading: Int,
    val problems: List<String>,
) {
    companion object {
        /**
         * Combines the state of each source. [sources] pairs a source's instance id with its state.
         * A failing source only adds a problem; the others' events are still shown.
         */
        fun of(sources: List<Pair<String, Loadable<List<CalendarEvent>>>>): MergedEvents {
            val events = mutableListOf<CalendarEvent>()
            val problems = mutableListOf<String>()
            var stillLoading = 0

            for ((sourceId, state) in sources) {
                when (state) {
                    is Loadable.Loading -> stillLoading++
                    is Loadable.Failed -> problems.add("$sourceId: ${state.message}")
                    is Loadable.Ready -> {
                        events.addAll(state.data)
                        val refreshError = state.refreshError
                        if (refreshError != null) problems.add("$sourceId may be out of date: $refreshError")
                    }
                }
            }
            return MergedEvents(events, stillLoading, problems)
        }
    }
}
