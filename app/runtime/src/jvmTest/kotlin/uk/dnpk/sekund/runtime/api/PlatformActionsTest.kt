package uk.dnpk.sekund.runtime.api

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.MutableStateFlow
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.api.DeviceInfo
import uk.dnpk.sekund.protocol.plugin.Capability
import uk.dnpk.sekund.runtime.platform.DisplayControl
import uk.dnpk.sekund.runtime.platform.KioskControl
import uk.dnpk.sekund.runtime.platform.PlatformServices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A screen that's only a variable. [ready] false behaves like Android before device admin is granted. */
private class FakeDisplay(var ready: Boolean = true) : DisplayControl {
    override val isOn: MutableStateFlow<Boolean> = MutableStateFlow(true)
    override fun isReady(): Boolean = ready
    override fun requestSetup() {}
    override suspend fun turnOn() { isOn.value = true }
    override suspend fun turnOff() {
        check(ready) { "Display control isn't set up" }
        isOn.value = false
    }
}

private class FakeKiosk : KioskControl {
    var locked = true
    override fun isLocked(): Boolean = locked
    override fun enter() { locked = true }
    override fun exit() { locked = false }
}

/** Display and kiosk control, as a companion uses them through the device API. */
class PlatformActionsTest {
    @Test
    fun displayAndKioskAreAdvertisedAndRun() = testApplication {
        val display = FakeDisplay()
        val kiosk = FakeKiosk()
        val graph = startedTestGraph(PlatformServices(display = display, kiosk = kiosk))
        application { deviceApiModule(graph.api) }
        val pairing = pair(client, graph)

        val info = SekundJson.decodeFromString(DeviceInfo.serializer(), client.get("/v1/device") { signedIn(pairing) }.bodyAsText())
        val listed = client.get("/v1/actions") { signedIn(pairing) }.bodyAsText()
        val off = client.post("/v1/actions/display.off") { signedIn(pairing) }.bodyAsText()
        val exit = client.post("/v1/actions/kiosk.exit") { signedIn(pairing) }.bodyAsText()

        assertTrue(Capability.DisplayControl in info.capabilities && Capability.Kiosk in info.capabilities)
        assertTrue(listed.contains("\"display.off\"") && listed.contains("\"kiosk.exit\""), listed)
        assertTrue(off.contains("\"ok\":true"), off)
        assertFalse(display.isOn.value)
        assertFalse(graph.displayIsOn.value, "the app follows the real screen")
        assertTrue(exit.contains("\"ok\":true"), exit)
        assertFalse(kiosk.locked)
    }

    @Test
    fun displayOffExplainsWhenNotSetUp() = testApplication {
        val graph = startedTestGraph(PlatformServices(display = FakeDisplay(ready = false)))
        application { deviceApiModule(graph.api) }
        val pairing = pair(client, graph)

        val off = client.post("/v1/actions/display.off") { signedIn(pairing) }.bodyAsText()

        assertTrue(off.contains("\"ok\":false") && off.contains("isn't set up"), off)
    }

    @Test
    fun withoutDisplayControlTheActionsDontExist() = testApplication {
        val graph = startedTestGraph()
        application { deviceApiModule(graph.api) }
        val pairing = pair(client, graph)

        val listed = client.get("/v1/actions") { signedIn(pairing) }.bodyAsText()
        val off = client.post("/v1/actions/display.off") { signedIn(pairing) }

        assertFalse(listed.contains("display."), listed)
        assertEquals(404, off.status.value)
    }
}
