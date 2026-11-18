package uk.dnpk.sekund.plugins.declarative

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonObject
import uk.dnpk.sekund.protocol.declarative.DeclarativeDefinition
import uk.dnpk.sekund.protocol.declarative.DeclarativeParser
import uk.dnpk.sekund.sdk.auth.AuthAccess
import uk.dnpk.sekund.sdk.auth.AuthState
import uk.dnpk.sekund.sdk.context.TimePreferences
import uk.dnpk.sekund.sdk.settings.AccountRef
import uk.dnpk.sekund.sdk.settings.DescriptorSettingsSchema

/** Parses a definition for a test, failing loudly if the test's own JSON is wrong. */
fun definitionOf(json: String): DeclarativeDefinition {
    val parsed = DeclarativeParser.parse(json)
    return parsed.definition ?: error("Test definition is invalid: ${parsed.issues}")
}

/** The settings an instance would get: decoded through the definition's own settings form. */
fun settingsFor(definition: DeclarativeDefinition, json: JsonObject): JsonObject =
    DescriptorSettingsSchema(definition.settings).decode(json).value

/** A template engine in UTC with a 24-hour clock, so results don't depend on the machine running the test. */
fun testTemplates(definition: DeclarativeDefinition): TemplateEngine =
    TemplateEngine(definition.lookups, timeZone = { TimeZone.UTC }, preferences = { TimePreferences(use24Hour = true) })

/** Pretends to be the auth registry: hands out [signedClient] for any account, and records which ones. */
class FakeAuth(private val signedClient: HttpClient) : AuthAccess {
    val requestedAccounts = mutableListOf<AccountRef>()

    override fun state(account: AccountRef): Flow<AuthState> = flowOf(AuthState.Valid)

    override fun client(account: AccountRef): HttpClient {
        requestedAccounts.add(account)
        return signedClient
    }

    override fun fields(account: AccountRef): Map<String, String> = emptyMap()
}

/** A client whose every request is answered by [handler], and which remembers the requests. */
class RecordingApi(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
    val requests = mutableListOf<HttpRequestData>()
    val client = HttpClient(MockEngine { request ->
        requests.add(request)
        handler(request)
    })
}

fun MockRequestHandleScope.respondJson(json: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(json, status, headersOf(HttpHeaders.ContentType, "application/json"))

fun bodyOf(request: HttpRequestData): String = (request.body as? TextContent)?.text ?: ""
