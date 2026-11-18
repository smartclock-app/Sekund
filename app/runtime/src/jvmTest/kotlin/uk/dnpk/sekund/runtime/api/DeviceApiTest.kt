package uk.dnpk.sekund.runtime.api

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.builtins.ListSerializer
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.api.ApiError
import uk.dnpk.sekund.protocol.api.Catalog
import uk.dnpk.sekund.protocol.api.ConfigSaved
import uk.dnpk.sekund.protocol.api.DeviceEvent
import uk.dnpk.sekund.protocol.api.DeviceInfo
import uk.dnpk.sekund.protocol.api.InstanceStatus
import uk.dnpk.sekund.protocol.api.PairStarted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The device API as a companion sees it, through Ktor's in-memory test engine. Each test reads like
 * the requests a companion would make.
 */
class DeviceApiTest {
    private val graph = startedTestGraph()

    @Test
    fun everythingButPairingNeedsAToken() = testApplication {
        application { deviceApiModule(graph.api) }

        val answer = client.get("/v1/device")

        assertEquals(HttpStatusCode.Unauthorized, answer.status)
        val error = SekundJson.decodeFromString(ApiError.serializer(), answer.bodyAsText())
        assertEquals("unauthorized", error.error.code)
    }

    @Test
    fun pairingWithThePinOnScreenGivesAWorkingToken() = testApplication {
        application { deviceApiModule(graph.api) }

        val pairing = pair(client, graph)
        val device = client.get("/v1/device") { signedIn(pairing) }

        assertEquals(HttpStatusCode.OK, device.status)
        val info = SekundJson.decodeFromString(DeviceInfo.serializer(), device.bodyAsText())
        assertEquals(pairing.deviceId, info.id)
        assertEquals(1, info.apiVersion)
        assertTrue(graph.alerts.alerts.value.isEmpty(), "the PIN alert goes away once paired")
    }

    @Test
    fun aWrongPinIsRefusedAndTooManyEndThePairing() = testApplication {
        application { deviceApiModule(graph.api) }
        val started = SekundJson.decodeFromString(
            PairStarted.serializer(),
            client.post("/v1/pair") { jsonBody("""{ "clientName": "Guesser" }""") }.bodyAsText(),
        )
        val wrong = if (pinShownOnScreen(graph) == "000000") "111111" else "000000"

        var last = HttpStatusCode.OK
        repeat(Pairing.MAX_ATTEMPTS) {
            last = client.post("/v1/pair/confirm") { jsonBody("""{ "pairingId": "${started.pairingId}", "pin": "$wrong" }""") }.status
        }

        assertEquals(HttpStatusCode.TooManyRequests, last)
        val afterwards = client.post("/v1/pair/confirm") { jsonBody("""{ "pairingId": "${started.pairingId}", "pin": "$wrong" }""") }
        assertEquals(HttpStatusCode.NotFound, afterwards.status)
    }

    @Test
    fun configChangesNeedTheCurrentEtag() = testApplication {
        application { deviceApiModule(graph.api) }
        val pairing = pair(client, graph)
        val etag = client.get("/v1/config") { signedIn(pairing) }.headers[HttpHeaders.ETag]!!

        val withoutEtag = client.patch("/v1/config") {
            signedIn(pairing)
            jsonBody("""{ "orientation": "portrait" }""")
        }
        assertEquals(HttpStatusCode.PreconditionRequired, withoutEtag.status)

        val saved = client.patch("/v1/config") {
            signedIn(pairing)
            header(HttpHeaders.IfMatch, etag)
            jsonBody("""{ "orientation": "portrait", "clock": { "use24Hour": true } }""")
        }
        assertEquals(HttpStatusCode.OK, saved.status)
        assertEquals("portrait", graph.config.config.value.orientation.name.lowercase())
        assertTrue(graph.config.config.value.clock.use24Hour)
        assertTrue(graph.config.config.value.clock.showSeconds, "untouched settings stay as they were")

        val stale = client.patch("/v1/config") {
            signedIn(pairing)
            header(HttpHeaders.IfMatch, etag)
            jsonBody("""{ "orientation": "landscape" }""")
        }
        assertEquals(HttpStatusCode.PreconditionFailed, stale.status)
    }

    @Test
    fun instancesCanBeAddedWithTheirProblemsReported() = testApplication {
        application { deviceApiModule(graph.api) }
        val pairing = pair(client, graph)

        val created = client.post("/v1/instances") {
            signedIn(pairing)
            jsonBody("""{ "type": "note", "settings": { "size": 3 }, "location": "sidebar" }""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals(listOf("note", "note-2"), graph.config.config.value.layout.sidebar)

        val updated = client.put("/v1/instances/note-2") {
            signedIn(pairing)
            jsonBody("""{ "settings": { "size": 99 } }""")
        }
        val saved = SekundJson.decodeFromString(ConfigSaved.serializer(), updated.bodyAsText())
        assertEquals(listOf("settings.size"), saved.issues.map { issue -> issue.path })

        val wrongPlace = client.post("/v1/instances") {
            signedIn(pairing)
            jsonBody("""{ "type": "note", "location": "main" }""")
        }
        assertEquals(HttpStatusCode.BadRequest, wrongPlace.status)

        assertEquals(HttpStatusCode.OK, client.delete("/v1/instances/note-2") { signedIn(pairing) }.status)
        assertEquals(listOf("note"), graph.config.config.value.layout.sidebar)
    }

    @Test
    fun declarativeWidgetsAreValidatedAndAppearInTheCatalog() = testApplication {
        application { deviceApiModule(graph.api) }
        val pairing = pair(client, graph)

        val invalid = client.put("/v1/declarative-widgets/bins") {
            signedIn(pairing)
            jsonBody("""{ "manifest": { "id": "bins", "name": "Bins", "version": "1" }, "hosts": [], "source": { "url": "x" } }""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, invalid.status)
        val error = SekundJson.decodeFromString(ApiError.serializer(), invalid.bodyAsText())
        assertTrue(error.error.details.toString().contains("hosts"))

        val valid = client.put("/v1/declarative-widgets/bins") {
            signedIn(pairing)
            jsonBody(
                """
                { "manifest": { "id": "bins", "name": "Bins", "version": "1" },
                  "hosts": ["https://bins.example.com/"],
                  "source": { "url": "https://bins.example.com/next" },
                  "view": { "kind": "stat", "value": "{day}" } }
                """,
            )
        }
        assertEquals(HttpStatusCode.OK, valid.status)

        val catalog = SekundJson.decodeFromString(Catalog.serializer(), client.get("/v1/catalog") { signedIn(pairing) }.bodyAsText())
        assertEquals(setOf("note", "bins"), catalog.types.map { type -> type.type }.toSet())
        assertTrue(catalog.plugins.first { plugin -> plugin.manifest.id == "bins" }.declarative)
    }

    @Test
    fun theEventStreamStartsWithTheCurrentState() = testApplication {
        application { deviceApiModule(graph.api) }
        val pairing = pair(client, graph)
        val socketClient = createClient { install(WebSockets) }

        socketClient.webSocket("/v1/events?token=${pairing.token}") {
            val kinds = mutableSetOf<String>()
            while (!kinds.containsAll(setOf("configChanged", "instancesChanged", "accountsChanged"))) {
                val frame = assertIs<Frame.Text>(incoming.receive())
                val event = SekundJson.decodeFromString(DeviceEvent.serializer(), frame.readText())
                kinds.add(SekundJson.encodeToJsonElement(DeviceEvent.serializer(), event).toString().substringAfter("\"kind\":\"").substringBefore('"'))
            }
        }
    }

    @Test
    fun revokingAClientStopsItsToken() = testApplication {
        application { deviceApiModule(graph.api) }
        val pairing = pair(client, graph)

        assertEquals(HttpStatusCode.NoContent, client.delete("/v1/clients/${pairing.clientId}") { signedIn(pairing) }.status)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/instances") { signedIn(pairing) }.status)
    }

    @Test
    fun instancesShowWhetherTheyRun() = testApplication {
        application { deviceApiModule(graph.api) }
        val pairing = pair(client, graph)

        val statuses = SekundJson.decodeFromString(
            ListSerializer(InstanceStatus.serializer()),
            client.get("/v1/instances") { signedIn(pairing) }.bodyAsText(),
        )

        assertEquals(listOf("note"), statuses.map { status -> status.config.id })
        assertTrue(statuses.single().running)
    }
}
