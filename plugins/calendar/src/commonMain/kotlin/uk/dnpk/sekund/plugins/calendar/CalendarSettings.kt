package uk.dnpk.sekund.plugins.calendar

import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.settings.SettingsSchema
import uk.dnpk.sekund.sdk.settings.SettingsValues

/**
 * One calendar widget's settings.
 *
 * @property sources ids of the calendar source instances to show events from (e.g. `"google"`, `"trakt"`).
 * @property maxEvents the most events to show; later ones are left out.
 * @property titleFilters hides events whose title matches any of these, either exactly or as a regular
 *   expression (see [TitleFilter]).
 * @property oddWeekTitle added to the "This Week"/"Next Week" heading on odd weeks (see [WeekParity]). Empty = nothing added.
 * @property evenWeekTitle the same for even weeks.
 */
data class CalendarSettings(
    val sources: List<String>,
    val maxEvents: Int,
    val titleFilters: List<String>,
    val oddWeekTitle: String,
    val evenWeekTitle: String,
)

/** How [CalendarSettings] is stored in config.json and shown in the settings screens. */
object CalendarSettingsSchema : SettingsSchema<CalendarSettings>() {
    // The keys match what the legacy Tauri importer writes, so imported configs work unchanged.
    private val sources = instanceRefs(
        "sources",
        label = "Calendars",
        extensionPoint = ExtensionPoints.CalendarSources.id,
        description = "Which calendar sources to show events from.",
    )
    private val maxEvents = number("maxEvents", label = "Most events shown", default = 50, min = 1, max = 500)
    private val titleFilters = textList(
        "titleFilters",
        label = "Hide events matching",
        description = "Hides events whose title is exactly one of these, or matches it as a regular expression (e.g. ^Private).",
    )
    private val oddWeekTitle = text(
        "oddWeekTitle",
        label = "Odd week label",
        description = "Added to the week heading on alternate weeks, e.g. for fortnightly bin collections.",
    )
    private val evenWeekTitle = text(
        "evenWeekTitle",
        label = "Even week label",
        description = "Added to the week heading on the other weeks. The week of 1 January 2024 is even.",
    )

    override fun build(values: SettingsValues) = CalendarSettings(
        sources = values[sources],
        maxEvents = values[maxEvents],
        titleFilters = values[titleFilters],
        oddWeekTitle = values[oddWeekTitle],
        evenWeekTitle = values[evenWeekTitle],
    )
}
