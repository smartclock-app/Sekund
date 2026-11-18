package uk.dnpk.sekund.protocol.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import uk.dnpk.sekund.protocol.config.InstanceConfig
import uk.dnpk.sekund.protocol.settings.SettingsIssue

/**
 * One instance as it is right now (`GET /v1/instances`).
 *
 * @property running false if it couldn't start; [problem] says why.
 * @property issues settings that were invalid and replaced by defaults.
 */
@Serializable
data class InstanceStatus(
    val config: InstanceConfig,
    val running: Boolean,
    val problem: String? = null,
    val issues: List<SettingsIssue> = emptyList(),
)

/**
 * `POST /v1/instances`: add an instance.
 *
 * @property id optional; one is made up from [type] if missing.
 * @property settings optional; missing settings take their defaults.
 * @property location widgets only: `"main"` or `"sidebar"` to place it at the end of that zone.
 */
@Serializable
data class NewInstance(
    val type: String,
    val id: String? = null,
    val settings: JsonObject? = null,
    val refreshInterval: String? = null,
    val location: String? = null,
)

/** `PUT /v1/instances/{id}`: replace an instance's settings (and optionally its refresh interval). */
@Serializable
data class InstanceUpdate(
    val settings: JsonObject,
    val refreshInterval: String? = null,
)

/**
 * The answer to every config change: the new ETag (for the next `If-Match`) and any settings
 * problems in the saved config, so a companion can show them straight away.
 */
@Serializable
data class ConfigSaved(
    val etag: String,
    val issues: List<InstanceIssue>,
)

/** A settings problem in one instance. */
@Serializable
data class InstanceIssue(
    val instanceId: String,
    val path: String,
    val message: String,
)
