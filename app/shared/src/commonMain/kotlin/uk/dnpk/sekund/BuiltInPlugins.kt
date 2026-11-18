package uk.dnpk.sekund

import uk.dnpk.sekund.plugins.clock.ClockPlugin
import uk.dnpk.sekund.sdk.plugin.SekundPlugin

/**
 * Every plugin compiled into the app.
 *
 * To add a plugin: create its module in plugins/, add it to the dependencies in
 * app/shared/build.gradle.kts, then add its plugin object to this list.
 */
val BuiltInPlugins: List<SekundPlugin> = listOf(
    ClockPlugin,
)
