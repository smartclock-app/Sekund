package uk.dnpk.sekund

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import uk.dnpk.sekund.protocol.API_VERSION

/** Starts the optional Sekund relay server. Real routes arrive in phase 9. */
fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    routing {
        // Simple health check, handy for uptime monitors and reverse proxies.
        get("/health") {
            call.respondText("ok (api v$API_VERSION)")
        }
    }
}
