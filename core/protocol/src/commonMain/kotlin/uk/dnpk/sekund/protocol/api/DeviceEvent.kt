package uk.dnpk.sekund.protocol.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the device pushes down `WS /v1/events`, one JSON message per event, e.g.
 * `{"kind": "configChanged", "etag": "3f9a..."}`. Companions should ignore kinds they don't know.
 */
@Serializable
sealed class DeviceEvent {
    /** config.json changed (from any source). Fetch it again if you show it. */
    @Serializable
    @SerialName("configChanged")
    data class ConfigChanged(val etag: String) : DeviceEvent()

    /** The running instances changed (one started, failed or was removed). */
    @Serializable
    @SerialName("instancesChanged")
    data class InstancesChanged(val instances: List<InstanceStatus>) : DeviceEvent()

    /** An account was added, removed, or its state changed. */
    @Serializable
    @SerialName("accountsChanged")
    data class AccountsChanged(val accounts: List<AccountSummary>) : DeviceEvent()

    /** An alert card appeared at the top of the sidebar. */
    @Serializable
    @SerialName("alertRaised")
    data class AlertRaised(val owner: String, val title: String, val subtitle: String? = null) : DeviceEvent()

    /** An alert card went away. */
    @Serializable
    @SerialName("alertCleared")
    data class AlertCleared(val owner: String) : DeviceEvent()

    /** A line from the device's log (info and above). */
    @Serializable
    @SerialName("log")
    data class Log(val line: LogLine) : DeviceEvent()

    /** An action finished. */
    @Serializable
    @SerialName("actionResult")
    data class ActionDone(val result: ActionResult) : DeviceEvent()
}
