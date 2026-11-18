package uk.dnpk.sekund.server.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import uk.dnpk.sekund.server.SekundServer

/** Public webhook URLs. Anyone may POST to one; the random hook id is what keeps them private. */
fun Route.webhookRoutes(server: SekundServer) {
    post("/hooks/{hookId}") {
        val hookId = call.parameters["hookId"] ?: ""
        val body = call.receiveText()
        if (body.length > MAX_BODY) throw ServerProblem(413, "too_large", "Webhook bodies are limited to $MAX_BODY characters")

        // Header names in lower case; the session-ish ones are left out, as no device needs them.
        val headers = mutableMapOf<String, String>()
        for ((name, values) in call.request.headers.entries()) {
            val lower = name.lowercase()
            if (lower == "authorization" || lower == "cookie") continue
            headers[lower] = values.joinToString(", ")
        }
        if (!server.webhooks.received(hookId, headers, body)) throw ServerProblem.notFound("No such webhook")
        // Answer straight away: services such as Google expect a quick 2xx and retry otherwise.
        call.respond(HttpStatusCode.OK)
    }
}

private const val MAX_BODY = 256 * 1024
