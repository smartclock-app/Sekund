package uk.dnpk.sekund.server.http

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import uk.dnpk.sekund.protocol.server.Credentials
import uk.dnpk.sekund.protocol.server.ServerUser
import uk.dnpk.sekund.server.SekundServer

/** Creating an account, signing in and out. */
fun Route.userRoutes(server: SekundServer) {
    post("/v1/users") {
        val request = call.receive<Credentials>()
        call.respond(HttpStatusCode.Created, server.users.signUp(request.username, request.password))
    }
    post("/v1/sessions") {
        val request = call.receive<Credentials>()
        call.respond(server.users.signIn(request.username, request.password))
    }
    delete("/v1/sessions/current") {
        signedInUser(call, server)
        server.users.signOut(bearerToken(call) ?: "")
        call.respond(HttpStatusCode.NoContent)
    }
    get("/v1/me") { call.respond(signedInUser(call, server)) }
}

/** The user whose session token came with the request; answers 401 if there's none or it's not valid. */
fun signedInUser(call: ApplicationCall, server: SekundServer): ServerUser {
    val token = bearerToken(call) ?: throw ServerProblem.unauthorized("Sign in first, then send Authorization: Bearer <token>")
    return server.users.authenticate(token) ?: throw ServerProblem.unauthorized("This session has ended. Sign in again.")
}

/** The token from `Authorization: Bearer ...`, or for WebSockets (where browsers can't set headers) `?token=...`. */
fun bearerToken(call: ApplicationCall): String? {
    val header = call.request.header(HttpHeaders.Authorization)
    if (header != null && header.startsWith("Bearer ", ignoreCase = true)) return header.substring(7).trim()
    return call.request.queryParameters["token"]
}
