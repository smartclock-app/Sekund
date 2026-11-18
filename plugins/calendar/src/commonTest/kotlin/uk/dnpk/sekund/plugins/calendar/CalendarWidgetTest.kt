package uk.dnpk.sekund.plugins.calendar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import uk.dnpk.sekund.sdk.calendar.CalendarEvent
import uk.dnpk.sekund.sdk.calendar.CalendarSource
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.testing.FakeInstanceContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

// Checks that the widget follows its sources live. The drawing itself isn't tested here.
class CalendarWidgetTest {

    /** A calendar source whose events the test sets directly. */
    private class FakeSource(vararg events: CalendarEvent) : CalendarSource {
        override val events = MutableStateFlow<Loadable<List<CalendarEvent>>>(Loadable.Ready(events.toList(), Clock.System.now()))
    }

    private val dentist = event("dentist", start = at("2025-06-19T09:00"))
    private val episode = event("episode", start = at("2025-06-20T21:00"))

    private fun ids(merged: MergedEvents): List<String> = merged.events.map { event -> event.id }

    @Test
    fun `events from all sources are merged, and a broken source only adds a warning`() = runTest {
        val context = FakeInstanceContext(settings().copy(sources = listOf("google", "trakt", "missing")), backgroundScope)
        context.instances.set("google", FakeSource(dentist))
        context.instances.set("trakt", FakeSource(episode))
        val widget = CalendarWidget(context)

        testScheduler.runCurrent()

        assertEquals(listOf("dentist", "episode"), ids(widget.merged.value))
        assertEquals(listOf("missing: No instance called 'missing'"), widget.merged.value.problems)
    }

    @Test
    fun `a source that's recreated is followed`() = runTest {
        val context = FakeInstanceContext(settings().copy(sources = listOf("google")), backgroundScope)
        context.instances.set("google", FakeSource(dentist))
        val widget = CalendarWidget(context)
        testScheduler.runCurrent()

        // What happens when someone edits the Google source's settings.
        context.instances.set("google", FakeSource(episode))
        testScheduler.runCurrent()

        assertEquals(listOf("episode"), ids(widget.merged.value))
    }

    @Test
    fun `a source that fails keeps the others and reports why`() = runTest {
        val google = FakeSource(dentist)
        val context = FakeInstanceContext(settings().copy(sources = listOf("google")), backgroundScope)
        context.instances.set("google", google)
        val widget = CalendarWidget(context)

        google.events.value = Loadable.Ready(listOf(dentist), Clock.System.now(), refreshError = "Timed out")
        testScheduler.runCurrent()

        assertEquals(listOf("dentist"), ids(widget.merged.value))
        assertEquals(listOf("google may be out of date: Timed out"), widget.merged.value.problems)
    }
}
