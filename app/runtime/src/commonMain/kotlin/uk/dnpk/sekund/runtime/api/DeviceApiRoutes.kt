package uk.dnpk.sekund.runtime.api

import co.touchlab.kermit.Logger
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.serialization.json.JsonElement
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.api.ApiError
import uk.dnpk.sekund.protocol.api.AuthorizationCodeResult
import uk.dnpk.sekund.protocol.api.ConnectAccount
import uk.dnpk.sekund.protocol.api.CredentialUpload
import uk.dnpk.sekund.protocol.api.DeviceEvent
import uk.dnpk.sekund.protocol.api.ErrorBody
import uk.dnpk.sekund.protocol.api.InstanceUpdate
import uk.dnpk.sekund.protocol.api.NewInstance
import uk.dnpk.sekund.protocol.api.PairConfirm
import uk.dnpk.sekund.protocol.api.PairRequest
import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.protocol.config.LayoutConfig
import uk.dnpk.sekund.runtime.config.etagOf

private val log = Logger.withTag("DeviceApi")

/**
 * The device API's HTTP routes (documented in docs/api/device-api.md). Each route is a few lines:
 * read the request, call [DeviceApi], send the answer.
 *
 * Every route except pairing needs `Authorization: Bearer <token>`. Errors are always
 * `{"error": {"code", "message", "details"}}` (see [ApiError]).
 */
fun Application.deviceApiModule(api: DeviceApi) {
    install(ContentNegotiation) { json(SekundJson) }
    install(WebSockets) { pingPeriodMillis = 30_000 }
    install(StatusPages) {
        exception<ApiProblem> { call, problem ->
            call.respond(HttpStatusCode.fromValue(problem.status), ApiError(ErrorBody(problem.code, problem.message, problem.details)))
        }
        exception<BadRequestException> { call, cause ->
            // Usually a body that isn't valid JSON or doesn't have the expected shape.
            val message = cause.cause?.message ?: cause.message ?: "Bad request"
            call.respond(HttpStatusCode.BadRequest, ApiError(ErrorBody("bad_request", message)))
        }
        exception<Throwable> { call, cause ->
            log.e(cause) { "Unexpected error in ${call.request.path()}" }
            call.respond(HttpStatusCode.InternalServerError, ApiError(ErrorBody("internal", cause.message ?: "Something went wrong")))
        }
    }

    routing {
        route("/v1") {
            // Pairing is the only thing allowed without a token.
            post("/pair") {
                val request = call.receive<PairRequest>()
                call.respond(api.pairing.start(request.clientName))
            }
            post("/pair/confirm") {
                val request = call.receive<PairConfirm>()
                call.respond(api.pairing.confirm(request.pairingId, request.pin))
            }

            withToken(api) {
                authenticatedRoutes(api)
            }
        }
    }
}

/** Every route that needs a paired client's token. */
private fun Route.authenticatedRoutes(api: DeviceApi) {
    get("/device") { call.respond(api.deviceInfo()) }
    get("/catalog") { call.respond(api.catalog()) }

    // Config, with ETag / If-Match so two companions can't overwrite each other's changes.
    get("/config") {
        val config = api.graph.config.config.value
        call.response.header(HttpHeaders.ETag, quoted(etagOf(config)))
        call.respond(config)
    }
    put("/config") {
        val ifMatch = requireIfMatch(call)
        val saved = api.replaceConfig(ifMatch, call.receive<AppConfig>())
        call.response.header(HttpHeaders.ETag, quoted(saved.etag))
        call.respond(saved)
    }
    patch("/config") {
        val ifMatch = requireIfMatch(call)
        val saved = api.patchConfig(ifMatch, call.receive<JsonElement>())
        call.response.header(HttpHeaders.ETag, quoted(saved.etag))
        call.respond(saved)
    }

    get("/instances") { call.respond(api.instanceStatuses()) }
    post("/instances") {
        val created = api.addInstance(call.receive<NewInstance>())
        call.respond(HttpStatusCode.Created, created)
    }
    get("/instances/{id}") {
        val id = pathParameter(call, "id")
        val status = api.instanceStatuses().firstOrNull { status -> status.config.id == id } ?: throw ApiProblem.notFound("No instance called '$id'")
        call.respond(status)
    }
    put("/instances/{id}") { call.respond(api.updateInstance(pathParameter(call, "id"), ifMatchOf(call), call.receive<InstanceUpdate>())) }
    delete("/instances/{id}") { call.respond(api.deleteInstance(pathParameter(call, "id"), ifMatchOf(call))) }
    put("/layout") { call.respond(api.setLayout(ifMatchOf(call), call.receive<LayoutConfig>())) }

    // Declarative widgets and auth providers: the body is the definition file's text.
    get("/declarative-widgets") { call.respond(api.declarativeWidgets()) }
    get("/declarative-widgets/{id}") { respondJsonText(call, api.declarativeWidgetText(pathParameter(call, "id"))) }
    put("/declarative-widgets/{id}") { call.respond(api.putDeclarativeWidget(pathParameter(call, "id"), call.receiveText())) }
    delete("/declarative-widgets/{id}") {
        api.deleteDeclarativeWidget(pathParameter(call, "id"))
        call.respond(HttpStatusCode.NoContent)
    }
    get("/auth-providers") { call.respond(api.authProviders()) }
    get("/auth-providers/{id}") { respondJsonText(call, api.authProviderText(pathParameter(call, "id"))) }
    put("/auth-providers/{id}") { call.respond(api.putAuthProvider(pathParameter(call, "id"), call.receiveText())) }
    delete("/auth-providers/{id}") {
        api.deleteAuthProvider(pathParameter(call, "id"))
        call.respond(HttpStatusCode.NoContent)
    }

    // Accounts and sign-in flows.
    get("/accounts") { call.respond(api.accounts.summaries()) }
    post("/accounts") { call.respond(api.accounts.connect(call.receive<ConnectAccount>())) }
    get("/accounts/flows/{flowId}") { call.respond(api.accounts.status(pathParameter(call, "flowId"))) }
    post("/accounts/flows/{flowId}/code") {
        val result = call.receive<AuthorizationCodeResult>()
        call.respond(api.accounts.completeAuthorizationCode(pathParameter(call, "flowId"), result.code, result.state))
    }
    put("/accounts/{id}/credential") { call.respond(api.accounts.uploadCredential(pathParameter(call, "id"), call.receive<CredentialUpload>())) }
    delete("/accounts/{id}") {
        api.accounts.remove(pathParameter(call, "id"))
        call.respond(HttpStatusCode.NoContent)
    }

    get("/actions") { call.respond(api.actions.list()) }
    post("/actions/{name}") { call.respond(api.actions.run(pathParameter(call, "name"))) }

    get("/logs") {
        val tail = call.request.queryParameters["tail"]?.toIntOrNull() ?: 200
        call.respond(api.graph.logBuffer.tail(tail.coerceIn(1, 500)))
    }

    // The old Tauri app's config.json, imported as a whole (the current config is backed up first).
    post("/import/legacy") { call.respond(api.importLegacy(call.receiveText())) }

    get("/clients") { call.respond(api.clients.clients.value) }
    delete("/clients/{id}") {
        if (!api.clients.revoke(pathParameter(call, "id"))) throw ApiProblem.notFound("No client called '${pathParameter(call, "id")}'")
        call.respond(HttpStatusCode.NoContent)
    }

    // One JSON message per event. Browsers can't set headers on a WebSocket, so this route also
    // accepts the token as ?token=... (see TokenAuth).
    webSocket("/events") {
        EventStream.of(api).collect { event ->
            send(Frame.Text(SekundJson.encodeToString(DeviceEvent.serializer(), event)))
        }
    }
}

/**
 * Routes declared inside need a token. This adds an invisible ("transparent") level to the route tree
 * so the token check applies only to them, not to their siblings such as `/pair`. (It's how Ktor's own
 * `authenticate { }` works.)
 */
private fun Route.withToken(api: DeviceApi, build: Route.() -> Unit) {
    val protected = createChild(TokenRequiredSelector)
    protected.install(TokenAuth) { this.api = api }
    protected.build()
}

private object TokenRequiredSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
        RouteSelectorEvaluation.Transparent

    override fun toString(): String = "(token required)"
}

/** Settings for [TokenAuth]. */
class TokenAuthConfig {
    lateinit var api: DeviceApi
}

/**
 * Refuses any request without a valid paired-client token (HTTP 401). The token comes from
 * `Authorization: Bearer ...`, or for the events WebSocket only, from `?token=...`.
 */
val TokenAuth = createRouteScopedPlugin("TokenAuth", ::TokenAuthConfig) {
    val api = pluginConfig.api
    onCall { call ->
        val token = tokenOf(call) ?: throw ApiProblem(401, "unauthorized", "Pair first, then send Authorization: Bearer <token>")
        // A request that came through the Sekund server, which already checked it's from the device's owner.
        if (token == api.localToken) return@onCall
        api.clients.authenticate(token) ?: throw ApiProblem(401, "unauthorized", "This token isn't valid (the pairing may have been removed)")
    }
}

private fun tokenOf(call: ApplicationCall): String? {
    val header = call.request.header(HttpHeaders.Authorization)
    if (header != null && header.startsWith("Bearer ", ignoreCase = true)) return header.substring(7).trim()
    if (call.request.path().endsWith("/events")) return call.request.queryParameters["token"]
    return null
}

private fun pathParameter(call: ApplicationCall, name: String): String =
    call.parameters[name] ?: throw ApiProblem.badRequest("Missing $name")

/** The ETag from `If-Match`, without quotes; null if absent or `*` (= any version). */
private fun ifMatchOf(call: ApplicationCall): String? {
    val raw = call.request.header(HttpHeaders.IfMatch)?.trim() ?: return null
    if (raw == "*") return null
    return raw.removePrefix("W/").trim('"')
}

/** Replacing the whole config without saying which version you started from is refused (HTTP 428). */
private fun requireIfMatch(call: ApplicationCall): String? {
    if (call.request.header(HttpHeaders.IfMatch) == null) {
        throw ApiProblem(428, "precondition_required", "Send If-Match with the ETag from GET /v1/config (or * to overwrite anyway)")
    }
    return ifMatchOf(call)
}

private fun quoted(etag: String) = "\"$etag\""

private suspend fun respondJsonText(call: ApplicationCall, text: String) {
    call.respondText(text, ContentType.Application.Json)
}
