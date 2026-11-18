package uk.dnpk.sekund.plugins.slideshow

import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.settings.SettingsSchema
import uk.dnpk.sekund.sdk.settings.SettingsValues
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The slideshow's settings. Keys match what the legacy importer writes.
 *
 * @property sources ids of the photo source instances to show (e.g. `"immich"`).
 * @property rotateEvery how long each photo stays on screen.
 * @property cacheToDisk keep downloaded photos, so the slideshow still works without network.
 * @property shuffle show photos in a random order instead of the source's order.
 */
data class SlideshowSettings(
    val sources: List<String>,
    val rotateEvery: Duration,
    val cacheToDisk: Boolean,
    val shuffle: Boolean,
)

object SlideshowSettingsSchema : SettingsSchema<SlideshowSettings>() {
    private val sources = instanceRefs(
        "sources",
        label = "Photo sources",
        extensionPoint = ExtensionPoints.PhotoSources.id,
        description = "Where the photos come from.",
    )
    private val rotateEvery = duration("rotateEvery", label = "Change photo every", default = 30.seconds, min = 5.seconds)
    private val cacheToDisk = toggle(
        "cacheToDisk",
        label = "Keep photos for offline use",
        description = "Saves each photo after downloading it, so they still show when the network is down.",
        default = false,
    )
    private val shuffle = toggle("shuffle", label = "Shuffle", default = false)

    override fun build(values: SettingsValues) = SlideshowSettings(
        sources = values[sources],
        rotateEvery = values[rotateEvery],
        cacheToDisk = values[cacheToDisk],
        shuffle = values[shuffle],
    )
}
