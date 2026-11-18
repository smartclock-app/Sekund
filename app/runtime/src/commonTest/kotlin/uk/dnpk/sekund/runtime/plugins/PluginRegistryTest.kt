package uk.dnpk.sekund.runtime.plugins

import androidx.compose.runtime.Composable
import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.plugin.PluginRegistrar
import uk.dnpk.sekund.sdk.plugin.SekundPlugin
import uk.dnpk.sekund.sdk.settings.NoSettings
import uk.dnpk.sekund.sdk.widget.Widget
import uk.dnpk.sekund.sdk.widget.WidgetLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private object EmptyWidget : Widget {
    @Composable
    override fun Content(location: WidgetLocation) {
    }
}

/** Builds a test plugin that contributes widgets of the given types. */
private fun plugin(id: String, vararg types: String, locations: Set<WidgetLocation> = setOf(WidgetLocation.Sidebar)) =
    object : SekundPlugin {
        override val manifest = PluginManifest(id = id, name = id, version = "1")

        override fun register(registrar: PluginRegistrar) {
            for (type in types) {
                registrar.contribute(
                    Contribution(
                        point = ExtensionPoints.Widgets,
                        type = type,
                        name = type,
                        settings = NoSettings,
                        widgetLocations = locations,
                        create = { _ -> EmptyWidget },
                    ),
                )
            }
        }
    }

private val crashingPlugin = object : SekundPlugin {
    override val manifest = PluginManifest(id = "crashy", name = "Crashy", version = "1")

    override fun register(registrar: PluginRegistrar) = error("oops")
}

class PluginRegistryTest {
    @Test
    fun `contributions are found by type and by extension point`() {
        val registry = PluginRegistry(listOf(plugin("weather", "weather"), plugin("notes", "note", "sticky")))

        assertNotNull(registry.contribution("sticky"))
        assertEquals("notes", registry.pluginIdOf("sticky"))
        assertEquals(listOf("weather", "note", "sticky"), registry.contributionsTo(ExtensionPoints.Widgets).map { contribution -> contribution.type })
        assertEquals(emptyList(), registry.problems)
    }

    @Test
    fun `broken plugins and contributions are skipped and reported, the rest still load`() {
        val registry = PluginRegistry(
            listOf(
                plugin("Bad Id", "a"),
                plugin("one", "shared"),
                plugin("two", "shared", "fine"),
                plugin("three", "nowhere", locations = emptySet()),
                crashingPlugin,
            ),
        )

        assertEquals("one", registry.pluginIdOf("shared"))
        assertNotNull(registry.contribution("fine"))
        assertNull(registry.contribution("nowhere"))
        assertNull(registry.contribution("a"))
        assertEquals(4, registry.problems.size)
    }
}
