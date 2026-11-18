package uk.dnpk.sekund.runtime.api

import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.api.LegacyImportReport
import uk.dnpk.sekund.runtime.legacy.LEGACY_CONFIG
import uk.dnpk.sekund.sdk.auth.AuthState
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyImportApiTest {
    private val graph = startedTestGraph()

    @Test
    fun anOldConfigReplacesTheCurrentOneAndItsSecretsBecomeAccounts() = testApplication {
        application { deviceApiModule(graph.api) }
        val pairing = pair(client, graph)

        val answer = client.post("/v1/import/legacy") {
            signedIn(pairing)
            jsonBody(LEGACY_CONFIG)
        }

        assertEquals(HttpStatusCode.OK, answer.status)
        val report = SekundJson.decodeFromString(LegacyImportReport.serializer(), answer.bodyAsText())
        assertTrue("weather" in report.instances)
        assertEquals("Kitchen", graph.config.config.value.api.deviceName)

        // The imported OpenWeather account works straight away: its provider is built in.
        val state = runBlocking { graph.auth.state("acc_legacy_openweather").first() }
        assertEquals(AuthState.Valid, state)
    }

    @Test
    fun aNewConfigIsRefused() = testApplication {
        application { deviceApiModule(graph.api) }
        val pairing = pair(client, graph)

        val answer = client.post("/v1/import/legacy") {
            signedIn(pairing)
            jsonBody("""{ "version": 1, "instances": [] }""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, answer.status)
    }

    @Test
    fun anOldConfigFileFoundAtStartupIsImportedAndBackedUp() {
        val started = startedTestGraph { paths ->
            SystemFileSystem.createDirectories(paths.configDir)
            SystemFileSystem.sink(paths.configFile).buffered().use { sink -> sink.writeString(LEGACY_CONFIG) }
        }

        assertEquals("Kitchen", started.config.config.value.api.deviceName)
        val backups = SystemFileSystem.list(started.paths.backupDir).map { file -> file.name }
        assertTrue(backups.any { name -> name.startsWith("legacy-config-") })
    }
}
