package uk.dnpk.sekund.runtime.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.formUrlEncode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import uk.dnpk.sekund.protocol.auth.AuthDefinitionParser
import uk.dnpk.sekund.protocol.auth.AuthProviderDefinition
import uk.dnpk.sekund.runtime.auth.secrets.SecretStore
import uk.dnpk.sekund.runtime.newTestPaths
import uk.dnpk.sekund.runtime.services.AlertCenter
import kotlin.time.Clock
import kotlin.time.Instant

/** A [SecretStore] in memory, so tests can look inside it. */
class InMemorySecretStore : SecretStore {
    val values = mutableMapOf<String, String>()

    override suspend fun read(key: String): String? = values[key]

    override suspend fun write(key: String, value: String) {
        values[key] = value
    }

    override suspend fun delete(key: String) {
        values.remove(key)
    }
}

/** A clock the test moves by hand. */
class FakeClock(var epochSeconds: Long = 1_000_000) : Clock {
    override fun now(): Instant = Instant.fromEpochSeconds(epochSeconds)
}

/** Everything an auth test needs, wired the way AppGraph wires it but with fakes. */
class AuthTestRig(
    scope: CoroutineScope,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val secrets = InMemorySecretStore()
    val alerts = AlertCenter()
    val clock = FakeClock()
    val accountsFile = AccountsFile(newTestPaths().accountsFile)
    val registry = AuthRegistry(HttpClient(MockEngine(handler)), secrets, accountsFile, alerts, scope, clock)

    fun addProvider(json: String): AuthProviderDefinition {
        val parsed = AuthDefinitionParser.parse(json)
        val definition = parsed.definition ?: error("Test definition is invalid: ${parsed.issues}")
        registry.setProviders(registry.providers.value + (definition.id to definition))
        return definition
    }
}

/** The request body as text, whether it was sent as a form or as JSON. */
fun bodyText(request: HttpRequestData): String {
    val body = request.body
    if (body is TextContent) return body.text
    if (body is FormDataContent) return body.formData.formUrlEncode()
    return ""
}

fun MockRequestHandleScope.respondJson(json: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(json, status, headersOf(HttpHeaders.ContentType, "application/json"))

/** A provider with a refreshable OAuth method, for the refresh tests. */
const val EXAMPLE_OAUTH = """
{
  "id": "example",
  "name": "Example",
  "methods": [{
    "type": "oauth2.authorizationCode",
    "authorizeUrl": "https://auth.example.com/authorize",
    "tokenUrl": "https://auth.example.com/token",
    "scopes": ["read", "write"],
    "client": { "mode": "fixed", "id": "sekund-test" }
  }],
  "baseUrls": ["https://api.example.com"]
}
"""
