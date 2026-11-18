package uk.dnpk.sekund.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.server.Credentials
import uk.dnpk.sekund.protocol.server.SessionStarted
import uk.dnpk.sekund.server.config.DatabaseConfig
import uk.dnpk.sekund.server.config.ServerConfig
import uk.dnpk.sekund.server.http.serverModule
import java.io.File

/** A server with a fresh SQLite database in a temporary file. [http] is what it uses to call OAuth providers. */
fun newTestServer(config: ServerConfig = ServerConfig(), http: HttpClient = HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) })): SekundServer {
    val file = File.createTempFile("sekund-server-test", ".db")
    file.deleteOnExit()
    return SekundServer(config.copy(database = DatabaseConfig("jdbc:sqlite:${file.absolutePath}")), http = http)
}

/** Installs [server] in a `testApplication` and returns a JSON + WebSocket client for it. */
fun ApplicationTestBuilder.startServer(server: SekundServer): HttpClient {
    application { serverModule(server) }
    return createClient {
        install(ContentNegotiation) { json(SekundJson) }
        install(WebSockets)
    }
}

suspend fun HttpClient.signUp(username: String, password: String = "correct horse"): SessionStarted =
    post("/v1/users") {
        contentType(ContentType.Application.Json)
        setBody(Credentials(username, password))
    }.body()
