package uk.dnpk.sekund

import uk.dnpk.sekund.plugins.calendar.CalendarPlugin
import uk.dnpk.sekund.plugins.clock.ClockPlugin
import uk.dnpk.sekund.plugins.slideshow.SlideshowPlugin
import uk.dnpk.sekund.plugins.trakt.TraktPlugin
import uk.dnpk.sekund.plugins.google.GooglePlugin
import uk.dnpk.sekund.plugins.declarative.DeclarativePlugin
import uk.dnpk.sekund.protocol.declarative.DeclarativeDefinition
import uk.dnpk.sekund.sdk.plugin.SekundPlugin

/**
 * Every plugin compiled into the app.
 *
 * To add a plugin: create its module in plugins/, add it to the dependencies in
 * app/shared/build.gradle.kts, then add its plugin object to this list.
 */
val BuiltInPlugins: List<SekundPlugin> = listOf(
    ClockPlugin,
    CalendarPlugin,
    GooglePlugin,
    TraktPlugin,
    SlideshowPlugin,
)

/**
 * Turns a declarative widget definition (a JSON file) into a plugin, using the engine in
 * plugins/declarative. Passed to AppGraph by each platform's entry point.
 */
fun declarativePlugin(definition: DeclarativeDefinition): SekundPlugin = DeclarativePlugin(definition)
