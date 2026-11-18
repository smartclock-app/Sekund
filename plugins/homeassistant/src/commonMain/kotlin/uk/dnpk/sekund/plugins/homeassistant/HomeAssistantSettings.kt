package uk.dnpk.sekund.plugins.homeassistant

import uk.dnpk.sekund.sdk.settings.AccountRef
import uk.dnpk.sekund.sdk.settings.SettingsSchema
import uk.dnpk.sekund.sdk.settings.SettingsValues
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The Home Assistant widget's settings. Keys match what the legacy importer writes.
 *
 * @property showCameraFor how long a camera stays up after its trigger turns off again.
 */
data class HomeAssistantSettings(
    val account: AccountRef?,
    val cameras: List<CameraSettings>,
    val showCameraFor: Duration,
)

/**
 * One camera to pop up.
 *
 * @property entityId the camera, e.g. `camera.front_door`.
 * @property trigger the sensor that shows it while it's `on`, e.g. `binary_sensor.doorbell`.
 * @property streamUrl optional MJPEG stream to show instead of Home Assistant's own (blank = Home Assistant's).
 * @property aspectRatio width ÷ height of the picture, e.g. 16/9.
 */
data class CameraSettings(
    val entityId: String,
    val trigger: String,
    val streamUrl: String,
    val aspectRatio: Float,
)

object CameraSettingsSchema : SettingsSchema<CameraSettings>() {
    private val entityId = text("entityId", label = "Camera", placeholder = "camera.front_door", required = true)
    private val trigger = text(
        "trigger",
        label = "Show it while this is on",
        placeholder = "binary_sensor.doorbell",
        required = true,
    )
    private val streamUrl = text(
        "streamUrl",
        label = "Stream address (optional)",
        description = "An MJPEG stream to show instead of Home Assistant's own, e.g. from go2rtc.",
    )
    private val aspectRatio = text("aspectRatio", label = "Shape", default = "16:9", description = "Width:height, e.g. 16:9 or 4:3.")

    override fun build(values: SettingsValues) = CameraSettings(
        entityId = values[entityId].trim(),
        trigger = values[trigger].trim(),
        streamUrl = values[streamUrl].trim(),
        aspectRatio = AspectRatio.parse(values[aspectRatio]),
    )
}

object HomeAssistantSettingsSchema : SettingsSchema<HomeAssistantSettings>() {
    private val account = account("account", label = "Home Assistant account", provider = HomeAssistantPlugin.PROVIDER)
    private val cameras = objectList("cameras", label = "Cameras", itemSchema = CameraSettingsSchema)
    private val showCameraFor = duration(
        "showCameraFor",
        label = "Keep showing for",
        description = "How long a camera stays up after its sensor turns off.",
        default = 5.seconds,
        min = 0.seconds,
    )

    override fun build(values: SettingsValues) = HomeAssistantSettings(
        account = values[account],
        cameras = values[cameras],
        showCameraFor = values[showCameraFor],
    )
}

/** Reads `16:9`, `4:3` or a plain number like `1.78`. Anything else (or nonsense like `0`) gives 16:9. */
object AspectRatio {
    const val DEFAULT = 16f / 9f

    fun parse(text: String): Float {
        val parts = text.split(":")
        val ratio = if (parts.size == 2) {
            val width = parts[0].trim().toFloatOrNull()
            val height = parts[1].trim().toFloatOrNull()
            if (width == null || height == null || height == 0f) null else width / height
        } else {
            text.trim().toFloatOrNull()
        }
        if (ratio == null || ratio <= 0f || ratio.isNaN() || ratio.isInfinite()) return DEFAULT
        return ratio
    }
}
