package uk.dnpk.sekund.plugins.clock

import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.plugin.PluginRegistrar
import uk.dnpk.sekund.sdk.plugin.SekundPlugin
import uk.dnpk.sekund.sdk.settings.NoSettings

/** Provides the default clock face: big digital time with the date underneath. */
object ClockPlugin : SekundPlugin {
    /** The type id of the digital clock face, as used in config.json. */
    const val DIGITAL_CLOCK = "digital-clock"

    override val manifest = PluginManifest(
        id = "clock",
        name = "Clock",
        version = "1.0.0",
        description = "The default digital clock face.",
    )

    override fun register(registrar: PluginRegistrar) {
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.ClockFaces,
                type = DIGITAL_CLOCK,
                name = "Digital clock",
                settings = NoSettings,
                create = { context -> DigitalClockFace(context.time) },
            ),
        )
    }
}
