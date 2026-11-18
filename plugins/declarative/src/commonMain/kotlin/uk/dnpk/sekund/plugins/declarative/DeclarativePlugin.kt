package uk.dnpk.sekund.plugins.declarative

import uk.dnpk.sekund.protocol.declarative.DeclarativeDefinition
import uk.dnpk.sekund.protocol.declarative.DefinitionKind
import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.plugin.PluginRegistrar
import uk.dnpk.sekund.sdk.plugin.SekundPlugin
import uk.dnpk.sekund.sdk.settings.DescriptorSettingsSchema
import uk.dnpk.sekund.sdk.widget.WidgetLocation

/**
 * Turns one declarative definition into an ordinary plugin, so it gets instances, settings screens,
 * the layout and error cards exactly like a compiled plugin. The contribution's type is the definition's id.
 *
 * The runtime creates one of these per loaded definition (it doesn't know this class; the app passes
 * `::DeclarativePlugin` in when it builds the AppGraph).
 */
class DeclarativePlugin(private val definition: DeclarativeDefinition) : SekundPlugin {
    override val manifest: PluginManifest = definition.manifest

    override fun register(registrar: PluginRegistrar) {
        val settings = DescriptorSettingsSchema(definition.settings)
        when (definition.kind) {
            DefinitionKind.Widget -> registrar.contribute(
                Contribution(
                    point = ExtensionPoints.Widgets,
                    type = manifest.id,
                    name = manifest.name,
                    description = manifest.description,
                    settings = settings,
                    widgetLocations = locations(),
                    create = { context -> DeclarativeWidget(definition, context) },
                ),
            )
            DefinitionKind.PhotoSource -> registrar.contribute(
                Contribution(
                    point = ExtensionPoints.PhotoSources,
                    type = manifest.id,
                    name = manifest.name,
                    description = manifest.description,
                    settings = settings,
                    create = { context -> DeclarativePhotoSource(definition, context) },
                ),
            )
        }
    }

    private fun locations(): Set<WidgetLocation> {
        val result = mutableSetOf<WidgetLocation>()
        for (location in definition.locations) {
            if (location == "main") result.add(WidgetLocation.Main)
            if (location == "sidebar") result.add(WidgetLocation.Sidebar)
        }
        return result
    }
}
