package uk.dnpk.sekund.sdk.plugin

import uk.dnpk.sekund.protocol.plugin.PluginManifest

/**
 * The entry point of every compiled plugin. Usually an `object`:
 *
 * ```kotlin
 * object WeatherPlugin : SekundPlugin {
 *     override val manifest = PluginManifest(id = "weather", name = "Weather", version = "1.0.0")
 *
 *     override fun register(registrar: PluginRegistrar) {
 *         registrar.contribute(
 *             Contribution(
 *                 point = ExtensionPoints.Widgets,
 *                 type = "weather",
 *                 name = "Weather",
 *                 settings = WeatherSettingsSchema,
 *                 widgetLocations = setOf(WidgetLocation.Main, WidgetLocation.Sidebar),
 *                 create = { context -> WeatherWidget(context) },
 *             ),
 *         )
 *     }
 * }
 * ```
 *
 * Then add the object to `BuiltInPlugins.kt` in :app:shared.
 */
interface SekundPlugin {
    /** Who this plugin is and what it requires (accounts, device capabilities). */
    val manifest: PluginManifest

    /**
     * Tells Sekund what this plugin provides. Called once at startup.
     * Only register things here. Don't start any work: that happens when instances are created.
     */
    fun register(registrar: PluginRegistrar)
}
