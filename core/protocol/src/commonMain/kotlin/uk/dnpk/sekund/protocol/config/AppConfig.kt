package uk.dnpk.sekund.protocol.config

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
    /** Format version, used to migrate old files (see ConfigRepository in :app:runtime). */
    val version: Int = CURRENT_CONFIG_VERSION,
    val orientation: Orientation = Orientation.Landscape,
    val clock: ClockConfig = ClockConfig(),
    /** Id of the instance (of a clock face type) shown in the main square. */
    val clockFace: String = "clock",
    val layout: LayoutConfig = LayoutConfig(),
    /** Every configured widget, clock face and source. The layout refers to these by id. */
    val instances: List<InstanceConfig> = emptyList(),
    /** The LAN API used by the companion app. */
    val api: ApiConfig = ApiConfig(),
    /** Checking for and installing new versions of Sekund. */
    val updates: UpdatesConfig = UpdatesConfig(),
    /** The optional Sekund server (docs/server.md). */
    val server: ServerLinkConfig = ServerLinkConfig(),
)

/**
 * The optional Sekund server, which lets the companion reach this device from anywhere, relays
 * webhooks (e.g. instant Google Calendar updates) and signs in to services with its own OAuth clients.
 *
 * @property url the server's address, e.g. `"https://sekund.example.org"`; null (the default) for no server.
 */
@Serializable
data class ServerLinkConfig(
    val url: String? = null,
)

/**
 * App updates (see docs/updates.md). Sekund always checks; these only change how.
 *
 * @property installAutomatically download and install new versions without waiting for a tap. On Android
 *   as device owner that's silent; elsewhere the system installer still asks for confirmation.
 * @property checkEvery how often to look for a new version, e.g. `"6h"` or `"30m"` (at least 5 minutes).
 */
@Serializable
data class UpdatesConfig(
    val installAutomatically: Boolean = false,
    val checkEvery: String = "6h",
)

/**
 * The device API on the local network (see docs/api/device-api.md). Changes apply after a restart.
 *
 * @property deviceName shown in the companion when it finds this device, and advertised over mDNS.
 */
@Serializable
data class ApiConfig(
    val enabled: Boolean = true,
    val port: Int = 8080,
    val deviceName: String = "Sekund",
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
