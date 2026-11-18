package uk.dnpk.sekund.server.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import uk.dnpk.sekund.server.SekundServer

/**
 * The browser side of signing in through the server: a link from the device that forwards to the
 * provider, and the provider's redirect back. Both are opened by a person in a browser, so they answer
 * with a small web page rather than JSON.
 */
fun Route.oauthRoutes(server: SekundServer) {
    get("/oauth/{provider}/start") {
        val provider = call.parameters["provider"] ?: ""
        val flowId = call.request.queryParameters["flow"] ?: ""
        val target = server.oauth.authorizeRedirect(provider, flowId)
        if (target == null) {
            call.respondText(page("This sign-in link has expired. Start again on your Sekund."), ContentType.Text.Html, HttpStatusCode.NotFound)
            return@get
        }
        call.respondRedirect(target)
    }
    get("/oauth/{provider}/callback") {
        val parameters = call.request.queryParameters
        val message = server.oauth.callback(
            provider = call.parameters["provider"] ?: "",
            code = parameters["code"],
            state = parameters["state"],
            error = parameters["error"],
        )
        call.respondText(page(message), ContentType.Text.Html)
    }
}

/** A minimal page with one sentence, readable on a phone. */
private fun page(message: String): String {
    val safe = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return """
        <!doctype html>
        <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Sekund</title></head>
        <body style="font-family: system-ui, sans-serif; margin: 2rem; font-size: 1.2rem;"><p>$safe</p></body></html>
    """.trimIndent()
}
