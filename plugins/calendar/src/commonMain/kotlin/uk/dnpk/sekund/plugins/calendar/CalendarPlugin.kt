package uk.dnpk.sekund.plugins.calendar

import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.plugin.PluginRegistrar
import uk.dnpk.sekund.sdk.plugin.SekundPlugin
import uk.dnpk.sekund.sdk.widget.WidgetLocation

/**
 * Provides the calendar widget. The events come from calendar *sources* contributed by other plugins
 * (Google, Trakt, ...); this plugin only merges, groups and shows them.
 *
 * It's an ordinary widget, so there can be several (e.g. "Family" and "Work" with different sources).
 */
object CalendarPlugin : SekundPlugin {
    /** The calendar widget's type id, as used in config.json (and written by the legacy importer). */
    const val CALENDAR = "calendar"

    override val manifest = PluginManifest(
        id = "calendar",
        name = "Calendar",
        version = "1.0.0",
        description = "Upcoming events from your calendars, grouped by week and month.",
    )

    override fun register(registrar: PluginRegistrar) {
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.Widgets,
                type = CALENDAR,
                name = "Calendar",
                description = "Upcoming events from the calendar sources you pick.",
                settings = CalendarSettingsSchema,
                widgetLocations = setOf(WidgetLocation.Sidebar, WidgetLocation.Main),
                create = { context -> CalendarWidget(context) },
            ),
        )
    }
}
