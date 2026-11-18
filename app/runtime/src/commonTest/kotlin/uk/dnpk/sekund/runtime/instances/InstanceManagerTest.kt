package uk.dnpk.sekund.runtime.instances

import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.protocol.config.InstanceConfig
import uk.dnpk.sekund.runtime.newTestPaths
import uk.dnpk.sekund.runtime.plugins.PluginRegistry
import uk.dnpk.sekund.runtime.services.AlertCenter
import uk.dnpk.sekund.runtime.services.OverlayCenter
import uk.dnpk.sekund.runtime.services.SharedEventBus
import uk.dnpk.sekund.sdk.context.Alert
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.context.LinkedInstance
import uk.dnpk.sekund.sdk.context.TimePreferences
import uk.dnpk.sekund.sdk.context.TimeService
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.plugin.PluginRegistrar
import uk.dnpk.sekund.sdk.plugin.SekundPlugin
import uk.dnpk.sekund.sdk.settings.SettingsSchema
import uk.dnpk.sekund.sdk.settings.SettingsValues
import uk.dnpk.sekund.sdk.widget.Widget
import uk.dnpk.sekund.sdk.widget.WidgetLocation
import uk.dnpk.sekund.runtime.auth.AccountsFile
import uk.dnpk.sekund.runtime.auth.AuthRegistry
import uk.dnpk.sekund.runtime.auth.InMemorySecretStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

// A test plugin with one widget type ("note") whose instances remember their context.

private object NoteSchema : SettingsSchema<String>() {
    val text = text("text", label = "Text", default = "empty")

    override fun build(values: SettingsValues) = values[text]
}

private class NoteWidget(val context: InstanceContext<String>) : Widget {
    @Composable
    override fun Content(location: WidgetLocation) {
    }
}

private object NotePlugin : SekundPlugin {
    override val manifest = PluginManifest(id = "notes", name = "Notes", version = "1")

    override fun register(registrar: PluginRegistrar) {
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.Widgets,
                type = "note",
                name = "Note",
                settings = NoteSchema,
                widgetLocations = setOf(WidgetLocation.Sidebar),
                create = { context ->
                    if (context.settings == "explode") error("boom")
                    NoteWidget(context)
                },
            ),
        )
    }
}

private object FakeTime : TimeService {
    private val start = LocalDateTime(2026, 1, 1, 12, 0)
    override val everySecond: StateFlow<LocalDateTime> = MutableStateFlow(start)
    override val everyMinute: StateFlow<LocalDateTime> = MutableStateFlow(start)
    override val timeZone: StateFlow<TimeZone> = MutableStateFlow(TimeZone.UTC)
    override val preferences: StateFlow<TimePreferences> = MutableStateFlow(TimePreferences())
}

private fun note(id: String, text: String) =
    InstanceConfig(id = id, type = "note", settings = JsonObject(mapOf("text" to JsonPrimitive(text))))

class InstanceManagerTest {
    private val alerts = AlertCenter()

    private val registry = PluginRegistry(listOf(NotePlugin))

    private fun TestScope.manager() = InstanceManager(
        registry = registry,
        services = SharedServices(
            paths = newTestPaths(),
            http = HttpClient(MockEngine { respondOk() }),
            time = FakeTime,
            events = SharedEventBus(),
            alerts = alerts,
            overlays = OverlayCenter(),
            auth = AuthRegistry(HttpClient(MockEngine { respondOk() }), InMemorySecretStore(), AccountsFile(newTestPaths().accountsFile), alerts, backgroundScope),
            wakeUps = MutableSharedFlow(),
            displayIsOn = MutableStateFlow(true),
        ),
        appScope = backgroundScope,
    )

    private fun RunningInstance?.widget(): NoteWidget = assertIs<NoteWidget>(this!!.valueFor(ExtensionPoints.Widgets))

    @Test
    fun `instances are created with their decoded settings`() = runTest {
        val manager = manager()

        manager.apply(listOf(note("a", "hello")))

        assertEquals("hello", manager.instances.value["a"].widget().context.settings)
    }

    @Test
    fun `unchanged instances are kept, changed ones are recreated`() = runTest {
        val manager = manager()
        manager.apply(listOf(note("a", "one"), note("b", "two")))
        val a = manager.instances.value["a"].widget()
        val b = manager.instances.value["b"].widget()

        manager.apply(listOf(note("a", "one"), note("b", "changed")))

        assertSame(a, manager.instances.value["a"].widget())
        assertNotSame(b, manager.instances.value["b"].widget())
        assertFalse(b.context.scope.isActive) // the old one was stopped
    }

    @Test
    fun `removed instances are stopped and their alert cleared`() = runTest {
        val manager = manager()
        manager.apply(listOf(note("a", "one")))
        val a = manager.instances.value["a"].widget()
        a.context.alerts.show(Alert(title = "Hi"))

        manager.apply(emptyList())

        assertFalse(a.context.scope.isActive)
        assertTrue(alerts.alerts.value.isEmpty())
    }

    @Test
    fun `unknown types and crashing plugins become failed instances, not crashes`() = runTest {
        val manager = manager()

        manager.apply(
            listOf(
                InstanceConfig(id = "x", type = "not-installed"),
                note("y", "explode"),
                note("z", "fine"),
            ),
        )

        assertIs<RunningInstance.Failed>(manager.instances.value["x"])
        assertEquals("boom", assertIs<RunningInstance.Failed>(manager.instances.value["y"]).reason)
        assertEquals("fine", manager.instances.value["z"].widget().context.settings)
    }

    @Test
    fun `an invalid refresh interval is reported as a settings issue`() = runTest {
        val manager = manager()

        manager.apply(listOf(note("a", "x").copy(refreshInterval = "often")))

        val ready = assertIs<RunningInstance.Ready>(manager.instances.value["a"])
        assertEquals(listOf("refreshInterval"), ready.issues.map { issue -> issue.path })
    }

    @Test
    fun `an instance can follow another one as it is recreated and removed`() = runTest {
        val manager = manager()
        manager.apply(listOf(note("a", "watcher"), note("b", "one")))
        val watcher = manager.instances.value["a"].widget()
        val linked = watcher.context.instances.watch(ExtensionPoints.Widgets, "b")
        testScheduler.runCurrent()
        assertEquals("one", linkedNote(linked.value).context.settings)

        manager.apply(listOf(note("a", "watcher"), note("b", "two")))
        testScheduler.runCurrent()
        assertEquals("two", linkedNote(linked.value).context.settings)

        manager.apply(listOf(note("a", "watcher")))
        testScheduler.runCurrent()
        assertEquals(LinkedInstance.Unavailable("No instance called 'b'"), linked.value)
    }

    @Test
    fun `following an instance of another extension point reports it as unavailable`() = runTest {
        val manager = manager()
        manager.apply(listOf(note("a", "watcher"), note("b", "one")))
        val watcher = manager.instances.value["a"].widget()

        val linked = watcher.context.instances.watch(ExtensionPoints.CalendarSources, "b")

        assertIs<LinkedInstance.Unavailable>(linked.value)
    }

    private fun linkedNote(linked: LinkedInstance<Widget>): NoteWidget =
        assertIs<NoteWidget>(assertIs<LinkedInstance.Running<Widget>>(linked).value)

    @Test
    fun `a newly installed plugin brings its failed instances to life, and only those are recreated`() = runTest {
        val manager = manager()
        manager.apply(listOf(note("a", "one"), InstanceConfig(id = "later", type = "later-note")))
        val a = manager.instances.value["a"].widget()
        assertIs<RunningInstance.Failed>(manager.instances.value["later"])

        // What DeclarativeWidgets does when a definition is installed.
        registry.setExtraPlugins(listOf(renamedNotePlugin("later", "later-note")))
        manager.apply(manager.instances.value.values.map { instance -> instance.config })

        assertIs<RunningInstance.Ready>(manager.instances.value["later"])
        assertSame(a, manager.instances.value["a"].widget())
    }
}

/** The note plugin again, under another plugin id and type (as if loaded from a declarative file). */
private fun renamedNotePlugin(pluginId: String, type: String) = object : SekundPlugin {
    override val manifest = PluginManifest(id = pluginId, name = pluginId, version = "1")

    override fun register(registrar: PluginRegistrar) {
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.Widgets,
                type = type,
                name = type,
                settings = NoteSchema,
                widgetLocations = setOf(WidgetLocation.Sidebar),
                create = { context -> NoteWidget(context) },
            ),
        )
    }
}
