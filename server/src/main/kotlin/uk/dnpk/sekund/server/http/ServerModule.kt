package uk.dnpk.sekund.server.http

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import org.slf4j.LoggerFactory
import uk.dnpk.sekund.protocol.API_VERSION
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.api.ApiError
import uk.dnpk.sekund.protocol.api.ErrorBody
import uk.dnpk.sekund.server.SekundServer

private val log = LoggerFactory.getLogger("Server")

/**
 * The server's HTTP setup: JSON, WebSockets, error handling and every route. The routes live in the
 * `*Routes.kt` files next to this one, one file per area.
 */
fun Application.serverModule(server: SekundServer) {
    install(ContentNegotiation) { json(SekundJson) }
    install(WebSockets) {
        // Protocol-level pings keep connections through proxies alive and notice dead ones.
        pingPeriodMillis = 30_000
        timeoutMillis = 75_000
    }
    install(StatusPages) {
        exception<ServerProblem> { call, problem ->
            call.respond(HttpStatusCode.fromValue(problem.status), ApiError(ErrorBody(problem.code, problem.message)))
        }
        exception<BadRequestException> { call, cause ->
            val message = cause.cause?.message ?: cause.message ?: "Bad request"
            call.respond(HttpStatusCode.BadRequest, ApiError(ErrorBody("bad_request", message)))
        }
        exception<Throwable> { call, cause ->
            log.error("Unexpected error in ${call.request.path()}", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError(ErrorBody("internal", "Something went wrong")))
        }
    }

    routing {
        // For uptime monitors and reverse proxies.
        get("/health") { call.respondText("ok (api v$API_VERSION)") }

        userRoutes(server)
        deviceRoutes(server)
    }
}
