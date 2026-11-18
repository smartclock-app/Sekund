package uk.dnpk.sekund.plugins.alexa

import uk.dnpk.sekund.protocol.plugin.AuthRequirement
import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.protocol.plugin.Requirements
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.plugin.PluginRegistrar
import uk.dnpk.sekund.sdk.plugin.SekundPlugin
import uk.dnpk.sekund.sdk.widget.WidgetLocation

/** Alexa: now playing with synced lyrics, timers and alarms. Also provides Amazon's sign-in code. */
object AlexaPlugin : SekundPlugin {
    /** The auth provider id (amazon.auth.json). */
    const val PROVIDER = "amazon"

    /** The widget's type id, as used in config.json (and by the legacy importer). */
    const val ALEXA = "alexa"

    override val manifest = PluginManifest(
        id = "alexa",
        name = "Alexa",
        version = "1.0.0",
        description = "What your Echo is playing, with synced lyrics, plus its timers and alarms.",
        requires = Requirements(auth = listOf(AuthRequirement(provider = PROVIDER))),
    )

    override fun register(registrar: PluginRegistrar) {
        // amazon.auth.json says {"type": "custom", "handler": "amazon-cookie"}: this is that code.
        registrar.authHandler(AmazonCookieHandler.NAME, AmazonCookieHandler)
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.Widgets,
                type = ALEXA,
                name = "Alexa",
                description = "Now playing, timers and alarms from your Echo devices.",
                settings = AlexaSettingsSchema,
                widgetLocations = setOf(WidgetLocation.Sidebar),
                create = { context -> AlexaWidget(context) },
            ),
        )
    }
}
