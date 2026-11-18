package uk.dnpk.sekund.protocol.plugin

import kotlinx.serialization.Serializable

/**
 * Who a plugin is and what it needs. Every plugin has one, compiled or declarative (JSON).
 *
 * Requirements are *declared* up front so Sekund can check them when the plugin loads and the UI can
 * guide people ("connect a Google account first") before anything fails at runtime.
 *
 * @property id unique, lowercase letters, digits and dashes, e.g. `"weather"`.
 * @property minAppVersion the oldest Sekund version this plugin works with, or null for any.
 */
@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String? = null,
    val minAppVersion: String? = null,
    val requires: Requirements = Requirements(),
)

/** Everything a plugin needs from its surroundings to work. */
@Serializable
data class Requirements(
    val auth: List<AuthRequirement> = emptyList(),
    val capabilities: List<Capability> = emptyList(),
)

/**
 * "This plugin signs in to [provider]", e.g. `AuthRequirement(provider = "google", scopes = listOf(...))`.
 *
 * The provider must exist in the auth registry (an `*.auth.json` definition), otherwise the plugin is shown
 * as unavailable with the reason. Plugins can only use accounts of providers they declare here.
 *
 * @property optional true if the plugin still works (with fewer features) without an account.
 */
@Serializable
data class AuthRequirement(
    val provider: String,
    val scopes: List<String> = emptyList(),
    val optional: Boolean = false,
)

/** Device features a plugin may depend on. Not every platform has every capability. */
@Serializable
enum class Capability {
    /** Turning the screen on and off. */
    DisplayControl,

    /** Locking the device to Sekund (Android device owner). */
    Kiosk,

    /** Downloading and installing app updates. */
    SelfUpdate,

    /** Opening a web browser. */
    OpenBrowser,

    /** Playing video streams (e.g. HLS camera feeds). */
    VideoPlayback,
}
