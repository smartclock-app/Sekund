package uk.dnpk.sekund.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import uk.dnpk.sekund.server.config.ServerConfig
import uk.dnpk.sekund.server.http.serverModule
import java.io.File

/**
 * Starts the optional Sekund server. The settings file is `server.json` in the working folder, or the
 * path in the `SEKUND_SERVER_CONFIG` environment variable (see docs/server.md).
 */
fun main() {
    val configPath = System.getenv("SEKUND_SERVER_CONFIG") ?: "server.json"
    val config = ServerConfig.load(File(configPath))
    val server = SekundServer(config)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { serverModule(server) }
        .start(wait = true)
}
