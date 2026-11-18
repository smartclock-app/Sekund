package uk.dnpk.sekund.plugins.homeassistant

import uk.dnpk.sekund.protocol.plugin.AuthRequirement
import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.protocol.plugin.Requirements
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.plugin.PluginRegistrar
import uk.dnpk.sekund.sdk.plugin.SekundPlugin
import uk.dnpk.sekund.sdk.widget.WidgetLocation

/** Home Assistant: camera pop-ups when a sensor turns on. */
object HomeAssistantPlugin : SekundPlugin {
    /** The auth provider id (homeassistant.auth.json). */
    const val PROVIDER = "homeassistant"

    /** The widget's type id, as used in config.json (and by the legacy importer). */
    const val HOME_ASSISTANT = "homeassistant"

    override val manifest = PluginManifest(
        id = "homeassistant",
        name = "Home Assistant",
        version = "1.0.0",
        description = "Shows a camera full screen while a sensor is on, e.g. when the doorbell rings.",
        requires = Requirements(auth = listOf(AuthRequirement(provider = PROVIDER))),
    )

    override fun register(registrar: PluginRegistrar) {
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.Widgets,
                type = HOME_ASSISTANT,
                name = "Home Assistant cameras",
                description = "Pops up a camera when a sensor (doorbell, motion...) turns on.",
                settings = HomeAssistantSettingsSchema,
                widgetLocations = setOf(WidgetLocation.Main, WidgetLocation.Sidebar),
                create = { context -> HomeAssistantWidget(context) },
            ),
        )
    }
}
