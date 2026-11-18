package uk.dnpk.sekund.sdk.plugin

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.dnpk.sekund.protocol.plugin.AuthRequirement
import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.protocol.plugin.Requirements
import uk.dnpk.sekund.sdk.calendar.CalendarEvent
import uk.dnpk.sekund.sdk.calendar.CalendarSource
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.settings.NoSettings
import uk.dnpk.sekund.sdk.settings.SettingsSchema
import uk.dnpk.sekund.sdk.settings.SettingsValues
import uk.dnpk.sekund.sdk.widget.Widget
import uk.dnpk.sekund.sdk.widget.WidgetLocation
import kotlin.test.Test
import kotlin.test.assertEquals

// A complete (if tiny) plugin, written exactly as a real one would be.
// It provides a "hello" widget and a calendar source that needs a Google account.

private object HelloSettingsSchema : SettingsSchema<String>() {
    val greeting = text("greeting", label = "Greeting", default = "Hello!")

    override fun build(values: SettingsValues): String = values[greeting]
}

private class HelloWidget(private val greeting: String) : Widget {
    @Composable
    override fun Content(location: WidgetLocation) {
        Text(greeting)
    }
}

private class EmptyCalendarSource : CalendarSource {
    override val events: StateFlow<Loadable<List<CalendarEvent>>> = MutableStateFlow(Loadable.Loading)
}

private object HelloPlugin : SekundPlugin {
    override val manifest = PluginManifest(
        id = "hello",
        name = "Hello",
        version = "1.0.0",
        requires = Requirements(auth = listOf(AuthRequirement(provider = "google"))),
    )

    override fun register(registrar: PluginRegistrar) {
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.Widgets,
                type = "hello",
                name = "Hello",
                settings = HelloSettingsSchema,
                widgetLocations = setOf(WidgetLocation.Sidebar),
                create = { context -> HelloWidget(context.settings) },
            ),
        )
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.CalendarSources,
                type = "hello-calendar",
                name = "Hello calendar",
                settings = NoSettings,
                create = { _ -> EmptyCalendarSource() },
            ),
        )
    }
}

/** A registrar that just remembers what it was given, like the runtime does. */
private class RecordingRegistrar : PluginRegistrar {
    val contributions = mutableListOf<Contribution<*, *>>()

    override fun <T : Any, S> contribute(contribution: Contribution<T, S>) {
        contributions.add(contribution)
    }
}

class ExamplePluginTest {
    @Test
    fun `a plugin registers its contributions`() {
        val registrar = RecordingRegistrar()

        HelloPlugin.register(registrar)

        assertEquals(listOf("hello", "hello-calendar"), registrar.contributions.map { contribution -> contribution.type })
        assertEquals(ExtensionPoints.Widgets, registrar.contributions[0].point)
        assertEquals(ExtensionPoints.CalendarSources, registrar.contributions[1].point)
    }
}
