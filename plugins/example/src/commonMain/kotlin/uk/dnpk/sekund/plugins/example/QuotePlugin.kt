package uk.dnpk.sekund.plugins.example

import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.plugin.PluginRegistrar
import uk.dnpk.sekund.sdk.plugin.SekundPlugin
import uk.dnpk.sekund.sdk.widget.WidgetLocation

/** The example plugin from docs/plugins/authoring.md: one widget that shows a quote from a web API. */
object QuotePlugin : SekundPlugin {
    /** The widget's type, as used in config.json (`"type": "quote"`). */
    const val QUOTE = "quote"

    override val manifest = PluginManifest(
        id = "example",
        name = "Example",
        version = "1.0.0",
        description = "A quote of the day. The example from the plugin guide.",
    )

    override fun register(registrar: PluginRegistrar) {
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.Widgets,
                type = QUOTE,
                name = "Quote",
                description = "A quote, refreshed every few hours.",
                settings = QuoteSettingsSchema,
                widgetLocations = setOf(WidgetLocation.Sidebar),
                create = { context -> QuoteWidget(context) },
            ),
        )
    }
}
