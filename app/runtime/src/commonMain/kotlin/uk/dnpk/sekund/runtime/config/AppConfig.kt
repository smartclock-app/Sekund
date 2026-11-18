package uk.dnpk.sekund.runtime.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The contents of config.json.
 *
 * Contains no secrets (accounts are referenced by id), so the file and its backups are safe to share.
 * Every field has a default, so a missing field is never an error.
 *
 * Example:
 * ```json
 * {
 *   "version": 1,
 *   "orientation": "landscape",
 *   "clock": { "use24Hour": false, "showSeconds": true },
 *   "clockFace": "clock",
 *   "layout": { "main": [], "sidebar": ["calendar"] },
 *   "instances": [
 *     { "id": "clock", "type": "digital-clock", "settings": {} },
 *     { "id": "calendar", "type": "calendar", "settings": { "maxEvents": 50 } }
 *   ]
 * }
 * ```
 */
@Serializable
data class AppConfig(
    /** Format version, used to migrate old files. See [ConfigRepository]. */
    val version: Int = CURRENT_CONFIG_VERSION,
    val orientation: Orientation = Orientation.Landscape,
    val clock: ClockConfig = ClockConfig(),
    /** Id of the instance (of a clock face type) shown in the main square. */
    val clockFace: String = "clock",
    val layout: LayoutConfig = LayoutConfig(),
    /** Every configured widget, clock face and source. The layout refers to these by id. */
    val instances: List<InstanceConfig> = emptyList(),
)

const val CURRENT_CONFIG_VERSION: Int = 1

@Serializable
enum class Orientation {
    @SerialName("landscape")
    Landscape,

    @SerialName("portrait")
    Portrait,
}

/** How times are shown, everywhere in the app. */
@Serializable
data class ClockConfig(
    val use24Hour: Boolean = false,
    val showSeconds: Boolean = true,
)

/** Which widget instances appear where, in order. Each entry is an instance id. */
@Serializable
data class LayoutConfig(
    val main: List<String> = emptyList(),
    val sidebar: List<String> = emptyList(),
)

/**
 * One configured instance of a contribution, e.g. "the weather widget for Leeds".
 *
 * @property id unique, chosen when the instance is created, e.g. `"weather-leeds"`.
 * @property type which contribution this is an instance of, e.g. `"weather"`.
 * @property settings the plugin's own settings, read by its SettingsSchema. Kept as raw JSON here so
 *   a mistake in one plugin's settings can never stop the rest of the file from loading.
 * @property refreshInterval optional override of how often this instance refreshes, e.g. `"30m"`.
 */
@Serializable
data class InstanceConfig(
    val id: String,
    val type: String,
    val settings: JsonObject = JsonObject(emptyMap()),
    val refreshInterval: String? = null,
)
