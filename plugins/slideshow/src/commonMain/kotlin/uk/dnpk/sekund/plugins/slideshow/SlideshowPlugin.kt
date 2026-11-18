package uk.dnpk.sekund.plugins.slideshow

import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.plugin.PluginRegistrar
import uk.dnpk.sekund.sdk.plugin.SekundPlugin

/**
 * The slideshow clock face. Photos come from photo sources: the built-in Immich definition
 * (`immich.photos.json`), or any other declarative or compiled photo source.
 */
object SlideshowPlugin : SekundPlugin {
    /** The clock face's type id, as used in config.json (and by the legacy importer). */
    const val SLIDESHOW = "slideshow"

    override val manifest = PluginManifest(
        id = "slideshow",
        name = "Slideshow",
        version = "1.0.0",
        description = "A photo slideshow behind the clock.",
    )

    override fun register(registrar: PluginRegistrar) {
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.ClockFaces,
                type = SLIDESHOW,
                name = "Photo slideshow",
                description = "Photos from your photo sources, with the time on top.",
                settings = SlideshowSettingsSchema,
                create = { context -> SlideshowFace(context) },
            ),
        )
    }
}
