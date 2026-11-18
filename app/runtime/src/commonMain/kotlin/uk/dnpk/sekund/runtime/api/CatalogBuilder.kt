package uk.dnpk.sekund.runtime.api

import uk.dnpk.sekund.protocol.api.AuthRequirementStatus
import uk.dnpk.sekund.protocol.api.Catalog
import uk.dnpk.sekund.protocol.api.CatalogExtensionPoint
import uk.dnpk.sekund.protocol.api.CatalogPlugin
import uk.dnpk.sekund.protocol.api.CatalogType
import uk.dnpk.sekund.runtime.auth.AuthRegistry
import uk.dnpk.sekund.runtime.plugins.PluginRegistry
import uk.dnpk.sekund.sdk.plugin.ExtensionPoint
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.widget.WidgetLocation

/** Builds the `GET /v1/catalog` answer from the installed plugins and the auth registry. */
object CatalogBuilder {
    fun build(registry: PluginRegistry, auth: AuthRegistry, problems: List<String>): Catalog {
        val installedProviders = auth.providers.value.keys
        val accounts = auth.accounts.value

        val plugins = registry.manifests.map { manifest ->
            val requirements = manifest.requires.auth.map { requirement ->
                AuthRequirementStatus(
                    provider = requirement.provider,
                    optional = requirement.optional,
                    providerInstalled = requirement.provider in installedProviders,
                    accountsConnected = accounts.count { account -> account.provider == requirement.provider },
                )
            }
            CatalogPlugin(manifest = manifest, declarative = registry.isExtra(manifest.id), auth = requirements)
        }

        val types = registry.allContributions().map { contribution ->
            CatalogType(
                type = contribution.type,
                pluginId = registry.pluginIdOf(contribution.type) ?: "",
                name = contribution.name,
                description = contribution.description,
                extensionPoint = contribution.point.id,
                locations = contribution.widgetLocations.map { location -> locationName(location) },
                settings = contribution.settings.describe(),
                unavailable = registry.unavailableReason(contribution.type, installedProviders),
            )
        }

        // The built-in points always, plus any a plugin defined for itself.
        val points = linkedMapOf<String, ExtensionPoint<*>>()
        for (point in listOf(ExtensionPoints.Widgets, ExtensionPoints.ClockFaces, ExtensionPoints.CalendarSources, ExtensionPoints.PhotoSources)) {
            points[point.id] = point
        }
        for (contribution in registry.allContributions()) {
            points[contribution.point.id] = contribution.point
        }

        return Catalog(
            plugins = plugins,
            types = types,
            extensionPoints = points.values.map { point -> CatalogExtensionPoint(point.id, point.name) },
            problems = registry.problems + problems,
        )
    }

    /** How a widget location is written in the API and in definitions. */
    fun locationName(location: WidgetLocation): String = when (location) {
        WidgetLocation.Main -> "main"
        WidgetLocation.Sidebar -> "sidebar"
    }

    /** The reverse of [locationName], or null if [name] isn't a location. */
    fun locationOf(name: String): WidgetLocation? = when (name) {
        "main" -> WidgetLocation.Main
        "sidebar" -> WidgetLocation.Sidebar
        else -> null
    }
}
