package uk.dnpk.sekund.runtime.declarative

import androidx.compose.runtime.Composable
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import uk.dnpk.sekund.protocol.declarative.DeclarativeDefinition
import uk.dnpk.sekund.runtime.newTestPaths
import uk.dnpk.sekund.runtime.plugins.PluginRegistry
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.plugin.PluginRegistrar
import uk.dnpk.sekund.sdk.plugin.SekundPlugin
import uk.dnpk.sekund.sdk.settings.NoSettings
import uk.dnpk.sekund.sdk.widget.Widget
import uk.dnpk.sekund.sdk.widget.WidgetLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object BlankWidget : Widget {
    @Composable
    override fun Content(location: WidgetLocation) {
    }
}

/** Stands in for the real engine (plugins/declarative): one sidebar widget named after the definition. */
private fun fakeEngine(definition: DeclarativeDefinition): SekundPlugin = object : SekundPlugin {
    override val manifest = definition.manifest

    override fun register(registrar: PluginRegistrar) {
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.Widgets,
                type = definition.manifest.id,
                name = definition.manifest.name,
                settings = NoSettings,
                widgetLocations = setOf(WidgetLocation.Sidebar),
                create = { BlankWidget },
            ),
        )
    }
}

private fun definition(id: String, name: String = "Bins") = """
{
  "manifest": { "id": "$id", "name": "$name", "version": "1.0.0" },
  "hosts": ["https://bins.example.com/"],
  "source": { "url": "https://bins.example.com/next", "map": { "fields": { "day": "{$.day}" } } },
  "view": { "kind": "stat", "value": "{day}" }
}
"""

class DeclarativeWidgetsTest {
    private val paths = newTestPaths()
    private val registry = PluginRegistry(emptyList())
    private val widgets = DeclarativeWidgets(paths.widgetsDir, registry, ::fakeEngine, builtIns = { listOf(definition("builtin", "Built in")) })

    @Test
    fun installingAddsAWidgetTypeAndWritesTheFile() = runTest {
        widgets.load()
        assertNotNull(registry.contribution("builtin"))
        assertNull(registry.contribution("bins"))

        val result = widgets.put("bins", definition("bins"))

        assertNotNull(result.definition)
        assertNotNull(registry.contribution("bins"))
        assertTrue(registry.isExtra("bins"))
        assertTrue(SystemFileSystem.exists(Path(paths.widgetsDir, "bins.widget.json")))
    }

    @Test
    fun aUserFileReplacesTheBuiltInOneAndRemovingItBringsItBack() = runTest {
        widgets.load()
        widgets.put("builtin", definition("builtin", "My version"))
        assertEquals("My version", registry.contribution("builtin")?.name)

        assertTrue(widgets.delete("builtin"))
        assertEquals("Built in", registry.contribution("builtin")?.name)
        assertFalse(widgets.delete("builtin"), "built-in definitions can't be deleted")
    }

    @Test
    fun invalidDefinitionsAreRefusedWithReasons() = runTest {
        widgets.load()
        val wrongId = widgets.put("bins", definition("other"))
        assertEquals(listOf("manifest.id"), wrongId.issues.map { issue -> issue.path })

        val broken = widgets.put("bins", "{ not json")
        assertNull(broken.definition)
        assertNull(registry.contribution("bins"))
    }

    @Test
    fun aBrokenFileOnDiskIsReportedAndSkipped() = runTest {
        SystemFileSystem.createDirectories(paths.widgetsDir)
        SystemFileSystem.sink(Path(paths.widgetsDir, "broken.widget.json")).buffered().use { sink -> sink.writeString("{}") }

        widgets.load()

        assertEquals(1, widgets.definitions.value.size) // just the built-in one
        assertTrue(widgets.problems.value.isNotEmpty())
    }
}
