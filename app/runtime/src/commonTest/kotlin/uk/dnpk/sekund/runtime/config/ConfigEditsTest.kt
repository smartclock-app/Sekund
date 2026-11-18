package uk.dnpk.sekund.runtime.config

import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.protocol.config.LayoutConfig
import uk.dnpk.sekund.protocol.config.InstanceConfig
import uk.dnpk.sekund.sdk.widget.WidgetLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigEditsTest {
    private val weather = InstanceConfig(id = "weather", type = "weather")
    private val notes = InstanceConfig(id = "notes", type = "note")
    private val base = AppConfig(
        instances = listOf(weather, notes),
        layout = LayoutConfig(main = listOf("weather"), sidebar = listOf("notes", "weather")),
    )

    @Test
    fun newIdsAreNumberedWhenTaken() {
        assertEquals("calendar", ConfigEdits.newInstanceId(base, "calendar"))
        assertEquals("weather-2", ConfigEdits.newInstanceId(base, "weather"))

        val withSecond = ConfigEdits.addInstance(base, InstanceConfig(id = "weather-2", type = "weather"))
        assertEquals("weather-3", ConfigEdits.newInstanceId(withSecond, "weather"))
    }

    @Test
    fun addingCanPlaceTheInstance() {
        val added = ConfigEdits.addInstance(base, InstanceConfig(id = "clock-2", type = "clock"), WidgetLocation.Sidebar)
        assertEquals(listOf("notes", "weather", "clock-2"), added.layout.sidebar)
        assertFailsWith<IllegalArgumentException> { ConfigEdits.addInstance(base, weather) }
    }

    @Test
    fun removingAnInstanceRemovesItFromTheLayout() {
        val removed = ConfigEdits.removeInstance(base, "weather")
        assertEquals(listOf("notes"), removed.instances.map { instance -> instance.id })
        assertEquals(emptyList(), removed.layout.main)
        assertEquals(listOf("notes"), removed.layout.sidebar)
    }

    @Test
    fun movingStopsAtTheEnds() {
        val down = ConfigEdits.move(base, WidgetLocation.Sidebar, "notes", steps = 1)
        assertEquals(listOf("weather", "notes"), down.layout.sidebar)

        val tooFar = ConfigEdits.move(base, WidgetLocation.Sidebar, "weather", steps = -10)
        assertEquals(listOf("weather", "notes"), tooFar.layout.sidebar)
    }
}
