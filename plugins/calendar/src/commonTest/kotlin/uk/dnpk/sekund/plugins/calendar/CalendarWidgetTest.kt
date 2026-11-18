package uk.dnpk.sekund.plugins.calendar

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import uk.dnpk.sekund.sdk.auth.AuthAccess
import uk.dnpk.sekund.sdk.calendar.CalendarEvent
import uk.dnpk.sekund.sdk.calendar.CalendarSource
import uk.dnpk.sekund.sdk.context.Alerts
import uk.dnpk.sekund.sdk.context.EventBus
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.context.InstanceLookup
import uk.dnpk.sekund.sdk.context.KeyValueStore
import uk.dnpk.sekund.sdk.context.LinkedInstance
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.context.PluginLog
import uk.dnpk.sekund.sdk.context.Refresher
import uk.dnpk.sekund.sdk.context.TimePreferences
import uk.dnpk.sekund.sdk.context.TimeService
import uk.dnpk.sekund.sdk.plugin.ExtensionPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

// Checks that the widget follows its sources live. The drawing itself isn't tested here.
class CalendarWidgetTest {

    /** A calendar source whose events the test sets directly. */
    private class FakeSource(vararg events: CalendarEvent) : CalendarSource {
        override val events = MutableStateFlow<Loadable<List<CalendarEvent>>>(Loadable.Ready(events.toList(), Clock.System.now()))
    }

    /** Pretends to be the runtime's list of instances: one slot per id, which the test fills or empties. */
    private class FakeInstances : InstanceLookup {
        private val slots = mutableMapOf<String, MutableStateFlow<LinkedInstance<CalendarSource>>>()

        fun slot(id: String): MutableStateFlow<LinkedInstance<CalendarSource>> =
            slots.getOrPut(id) { MutableStateFlow(LinkedInstance.Unavailable("No instance called '$id'")) }

        override fun <T : Any> watch(point: ExtensionPoint<T>, instanceId: String): StateFlow<LinkedInstance<T>> {
            // The calendar only ever asks for calendar sources, so this cast is safe here.
            @Suppress("UNCHECKED_CAST")
            return slot(instanceId) as StateFlow<LinkedInstance<T>>
        }
    }

    private class FakeContext(
        override val settings: CalendarSettings,
        override val scope: CoroutineScope,
        override val instances: InstanceLookup,
    ) : InstanceContext<CalendarSettings> {
        override val instanceId = "calendar"
        override val time: TimeService = object : TimeService {
            override val everySecond = MutableStateFlow(WEDNESDAY_10AM)
            override val everyMinute = MutableStateFlow(WEDNESDAY_10AM)
            override val timeZone = MutableStateFlow(TimeZone.UTC)
            override val preferences = MutableStateFlow(TimePreferences(use24Hour = true))
        }
        override val log: PluginLog = object : PluginLog {
            override fun debug(message: String) {}
            override fun info(message: String) {}
            override fun warn(message: String, error: Throwable?) {}
            override fun error(message: String, error: Throwable?) {}
        }

        // The calendar widget doesn't use these.
        override val http: HttpClient get() = error("not used")
        override val auth: AuthAccess get() = error("not used")
        override val refresher: Refresher get() = error("not used")
        override val storage: KeyValueStore get() = error("not used")
        override val alerts: Alerts get() = error("not used")
        override val events: EventBus get() = error("not used")
    }

    private val instances = FakeInstances()
    private val dentist = event("dentist", start = at("2025-06-19T09:00"))
    private val episode = event("episode", start = at("2025-06-20T21:00"))

    private fun ids(merged: MergedEvents): List<String> = merged.events.map { event -> event.id }

    @Test
    fun `events from all sources are merged, and a broken source only adds a warning`() = runTest {
        instances.slot("google").value = LinkedInstance.Running(FakeSource(dentist))
        instances.slot("trakt").value = LinkedInstance.Running(FakeSource(episode))
        val sourceIds = listOf("google", "trakt", "missing")
        val widget = CalendarWidget(FakeContext(settings().copy(sources = sourceIds), backgroundScope, instances))

        testScheduler.runCurrent()

        assertEquals(listOf("dentist", "episode"), ids(widget.merged.value))
        assertEquals(listOf("missing: No instance called 'missing'"), widget.merged.value.problems)
    }

    @Test
    fun `a source that's recreated is followed`() = runTest {
        instances.slot("google").value = LinkedInstance.Running(FakeSource(dentist))
        val widget = CalendarWidget(FakeContext(settings().copy(sources = listOf("google")), backgroundScope, instances))
        testScheduler.runCurrent()

        // What happens when someone edits the Google source's settings.
        instances.slot("google").value = LinkedInstance.Running(FakeSource(episode))
        testScheduler.runCurrent()

        assertEquals(listOf("episode"), ids(widget.merged.value))
    }

    @Test
    fun `a source that fails keeps the others and reports why`() = runTest {
        val google = FakeSource(dentist)
        instances.slot("google").value = LinkedInstance.Running(google)
        val widget = CalendarWidget(FakeContext(settings().copy(sources = listOf("google")), backgroundScope, instances))

        google.events.value = Loadable.Ready(listOf(dentist), Clock.System.now(), refreshError = "Timed out")
        testScheduler.runCurrent()

        assertEquals(listOf("dentist"), ids(widget.merged.value))
        assertEquals(listOf("google may be out of date: Timed out"), widget.merged.value.problems)
    }
}
