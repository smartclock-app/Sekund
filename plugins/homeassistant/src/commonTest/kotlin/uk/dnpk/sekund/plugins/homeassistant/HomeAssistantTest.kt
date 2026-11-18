package uk.dnpk.sekund.plugins.homeassistant

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HaSessionTest {
    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `authenticates, then subscribes once per sensor`() {
        val session = HaSession("secret", listOf("binary_sensor.doorbell", "binary_sensor.doorbell", "binary_sensor.gate", " "))

        val auth = session.onMessage("""{"type": "auth_required", "ha_version": "2026.11.0"}""")
        assertEquals("""{"type":"auth","access_token":"secret"}""", auth.replies.single())

        val ok = session.onMessage("""{"type": "auth_ok"}""")
        assertEquals(HaEvent.Authenticated, ok.event)
        assertEquals(2, ok.replies.size)
        val first = json(ok.replies[0])
        assertEquals("1", first["id"]!!.jsonPrimitive.content)
        assertEquals("subscribe_trigger", first["type"]!!.jsonPrimitive.content)
        assertEquals("binary_sensor.doorbell", first["trigger"]!!.jsonObject["entity_id"]!!.jsonPrimitive.content)
        assertEquals("binary_sensor.gate", json(ok.replies[1])["trigger"]!!.jsonObject["entity_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `reports sensor changes, refusals and failed subscriptions`() {
        val session = HaSession("secret", listOf("binary_sensor.doorbell"))
        session.onMessage("""{"type": "auth_ok"}""")

        val event = session.onMessage(
            """{"id": 1, "type": "event", "event": {"variables": {"trigger": {"platform": "state",
               "entity_id": "binary_sensor.doorbell", "to_state": {"state": "on"}}}}}""",
        )
        assertEquals(HaEvent.Triggered("binary_sensor.doorbell", "on"), event.event)

        val removed = session.onMessage("""{"id": 1, "type": "event", "event": {"variables": {"trigger": {"entity_id": "binary_sensor.doorbell", "to_state": null}}}}""")
        assertEquals(HaEvent.Triggered("binary_sensor.doorbell", "unavailable"), removed.event)

        val failed = session.onMessage("""{"id": 1, "type": "result", "success": false, "error": {"code": "invalid_format", "message": "Entity not found"}}""")
        assertEquals(HaEvent.SubscriptionFailed("binary_sensor.doorbell", "Entity not found"), failed.event)

        assertNull(session.onMessage("""{"id": 1, "type": "result", "success": true}""").event)
        assertNull(session.onMessage("not json").event)
        assertEquals(HaEvent.Rejected("Invalid password"), session.onMessage("""{"type": "auth_invalid", "message": "Invalid password"}""").event)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveCamerasTest {
    private val door = CameraSettings("camera.door", "binary_sensor.doorbell", "", 16f / 9f)
    private val gate = CameraSettings("camera.gate", "binary_sensor.gate", "", 4f / 3f)

    @Test
    fun `a camera shows while its sensor is on, and for a while after`() = runTest {
        val changes = mutableListOf<List<String>>()
        val cameras = ActiveCameras(backgroundScope, listOf(door, gate), 5.seconds) { shown -> changes.add(shown.map { it.entityId }) }

        cameras.sensorChanged("binary_sensor.doorbell", "on")
        cameras.sensorChanged("binary_sensor.doorbell", "off")
        advanceTimeBy(4.seconds)
        runCurrent()
        assertEquals(listOf(listOf("camera.door")), changes)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(listOf(listOf("camera.door"), emptyList()), changes)
    }

    @Test
    fun `turning on again before the delay keeps it up, and other cameras join`() = runTest {
        val changes = mutableListOf<List<String>>()
        val cameras = ActiveCameras(backgroundScope, listOf(door, gate), 5.seconds) { shown -> changes.add(shown.map { it.entityId }) }

        cameras.sensorChanged("binary_sensor.doorbell", "on")
        cameras.sensorChanged("binary_sensor.doorbell", "off")
        advanceTimeBy(3.seconds)
        cameras.sensorChanged("binary_sensor.doorbell", "on")
        cameras.sensorChanged("binary_sensor.gate", "on")
        advanceTimeBy(1.minutes)
        runCurrent()

        assertEquals(listOf(listOf("camera.door"), listOf("camera.door", "camera.gate")), changes)
    }

    @Test
    fun `after tapping away, an old delay doesn't fire and the next ring shows again`() = runTest {
        val changes = mutableListOf<List<String>>()
        val cameras = ActiveCameras(backgroundScope, listOf(door), 5.seconds) { shown -> changes.add(shown.map { it.entityId }) }

        cameras.sensorChanged("binary_sensor.doorbell", "on")
        cameras.sensorChanged("binary_sensor.doorbell", "off")
        cameras.dismissAll()
        advanceTimeBy(1.minutes)
        runCurrent()
        cameras.sensorChanged("binary_sensor.doorbell", "on")

        assertEquals(listOf(listOf("camera.door"), listOf("camera.door")), changes)
    }
}

class HomeAssistantSettingsTest {
    @Test
    fun `aspect ratios can be written as a ratio or a number`() {
        assertEquals(4f / 3f, AspectRatio.parse("4:3"))
        assertEquals(1.5f, AspectRatio.parse(" 1.5 "))
        assertEquals(AspectRatio.DEFAULT, AspectRatio.parse(""))
        assertEquals(AspectRatio.DEFAULT, AspectRatio.parse("16:0"))
        assertEquals(AspectRatio.DEFAULT, AspectRatio.parse("wide"))
    }

    @Test
    fun `websocket addresses follow the server's scheme`() {
        assertEquals("ws://ha.local:8123/api/websocket", HomeAssistantConnection.webSocketUrl("http://ha.local:8123"))
        assertEquals("wss://ha.example.com/api/websocket", HomeAssistantConnection.webSocketUrl("https://ha.example.com"))
        assertEquals(5.seconds, HomeAssistantConnection.retryDelay(0))
        assertEquals(20.seconds, HomeAssistantConnection.retryDelay(3))
        assertEquals(5.minutes, HomeAssistantConnection.retryDelay(20))
    }
}
