package uk.dnpk.sekund.protocol.api

import kotlinx.serialization.Serializable
import uk.dnpk.sekund.protocol.plugin.Capability

/**
 * `GET /v1/device`: who this device is and what it can do. Companions use [capabilities] to hide
 * features the device doesn't have (e.g. display control).
 *
 * @property platform `"android"` or `"desktop"`.
 * @property paths where the device keeps its files, so people can find them.
 */
@Serializable
data class DeviceInfo(
    val id: String,
    val name: String,
    val appVersion: String,
    val apiVersion: Int,
    val platform: String,
    val capabilities: List<Capability>,
    val paths: DevicePaths,
)

@Serializable
data class DevicePaths(
    val config: String,
    val data: String,
    val logs: String,
)
