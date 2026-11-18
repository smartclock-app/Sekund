package uk.dnpk.sekund.runtime.api

import uk.dnpk.sekund.protocol.plugin.Capability

/**
 * Facts about the platform the app runs on, passed in by each entry point.
 *
 * @property name `"android"` or `"desktop"`.
 * @property capabilities what this device can do (filled in by the platform phases).
 */
data class PlatformInfo(
    val name: String,
    val appVersion: String,
    val capabilities: List<Capability> = emptyList(),
)
