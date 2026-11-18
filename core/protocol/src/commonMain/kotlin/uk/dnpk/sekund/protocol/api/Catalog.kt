package uk.dnpk.sekund.protocol.api

import kotlinx.serialization.Serializable
import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.protocol.settings.SettingsDescriptor

/**
 * `GET /v1/catalog`: everything that can be configured, with the settings forms to do it. A companion
 * renders its whole settings UI from this, without knowing any plugin in advance.
 *
 * @property problems plugins or definitions that couldn't be loaded, and why.
 */
@Serializable
data class Catalog(
    val plugins: List<CatalogPlugin>,
    val types: List<CatalogType>,
    val extensionPoints: List<CatalogExtensionPoint>,
    val problems: List<String>,
)

/**
 * One installed plugin.
 *
 * @property declarative true for plugins defined by a JSON file (they can be replaced or removed).
 * @property auth the sign-in providers it needs, with live status.
 */
@Serializable
data class CatalogPlugin(
    val manifest: PluginManifest,
    val declarative: Boolean,
    val auth: List<AuthRequirementStatus>,
)

/**
 * @property providerInstalled false means instances will fail until the provider definition is added.
 * @property accountsConnected how many accounts of this provider exist (any state).
 */
@Serializable
data class AuthRequirementStatus(
    val provider: String,
    val optional: Boolean,
    val providerInstalled: Boolean,
    val accountsConnected: Int,
)

/**
 * One kind of thing an instance can be created from, e.g. the "weather" widget.
 *
 * @property extensionPoint e.g. `"widget"`, `"clockFace"`, `"calendarSource"`.
 * @property locations widgets only: `"main"` and/or `"sidebar"`.
 * @property unavailable why new instances wouldn't work right now, or null if they would.
 */
@Serializable
data class CatalogType(
    val type: String,
    val pluginId: String,
    val name: String,
    val description: String? = null,
    val extensionPoint: String,
    val locations: List<String> = emptyList(),
    val settings: SettingsDescriptor,
    val unavailable: String? = null,
)

@Serializable
data class CatalogExtensionPoint(
    val id: String,
    val name: String,
)
