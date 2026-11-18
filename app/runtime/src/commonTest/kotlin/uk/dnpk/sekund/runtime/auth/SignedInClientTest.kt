package uk.dnpk.sekund.runtime.auth

import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.sdk.auth.AccountUnavailableException
import uk.dnpk.sekund.sdk.auth.AuthState
import uk.dnpk.sekund.sdk.auth.CredentialHostException
import uk.dnpk.sekund.sdk.auth.ProviderNotDeclaredException
import uk.dnpk.sekund.sdk.settings.AccountRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SignedInClientTest {

    @Test
    fun `api keys go where the definition says`() = runTest {
        val seen = mutableListOf<String>()
        val rig = AuthTestRig(backgroundScope) { request ->
            seen.add(request.url.toString() + " x-api-key=" + request.headers["x-api-key"])
            respond("ok")
        }
        rig.addProvider(
            """{"id": "weather", "name": "Weather", "methods": [{"type": "apiKey"}],
                "inject": {"type": "query", "name": "appid", "format": "{api_key}"},
                "baseUrls": ["https://api.weather.test/"]}""",
        )
        rig.addProvider(
            """{"id": "photos", "name": "Photos", "fields": [{"key": "instance_url", "label": "Server"}],
                "methods": [{"type": "apiKey"}], "inject": {"type": "header", "name": "x-api-key", "format": "{api_key}"},
                "baseUrls": ["{instance_url}/"]}""",
        )
        val weather = rig.registry.connectWithKey("weather", 0, emptyMap(), " key-1 ")
        val photos = rig.registry.connectWithKey("photos", 0, mapOf("instance_url" to "http://photos.local:2283/"), "key-2")

        val access = PluginAuthAccess(rig.registry, "test", setOf("weather", "photos"))
        access.client(AccountRef("weather", weather.id)).get("https://api.weather.test/now?q=London")
        access.client(AccountRef("photos", photos.id)).get("http://photos.local:2283/api/assets")

        assertEquals("https://api.weather.test/now?q=London&appid=key-1 x-api-key=null", seen[0])
        assertEquals("http://photos.local:2283/api/assets x-api-key=key-2", seen[1])
        assertEquals("Photos (photos.local:2283)", photos.label)
    }

    @Test
    fun `credentials are never sent to other hosts`() = runTest {
        val rig = AuthTestRig(backgroundScope) { respond("ok") }
        rig.addProvider(EXAMPLE_OAUTH)
        val account = rig.registry.saveSignIn(rig.registry.requireProvider("example"), 0, emptyMap(), Credential(accessToken = "a"), null)
        val client = PluginAuthAccess(rig.registry, "test", setOf("example")).client(AccountRef("example", account.id))

        assertFailsWith<CredentialHostException> { client.get("https://api.example.com.evil.test/steal") }
        assertFailsWith<CredentialHostException> { client.get("https://elsewhere.test/") }
    }

    @Test
    fun `a plugin can only use providers it declared`() = runTest {
        val rig = AuthTestRig(backgroundScope) { respond("ok") }
        val access = PluginAuthAccess(rig.registry, "clock", setOf("google"))

        assertFailsWith<ProviderNotDeclaredException> { access.client(AccountRef("trakt", "acc_1")) }
        assertEquals(AuthState.NotFound, access.state(AccountRef("trakt", "acc_1")).first())
    }

    @Test
    fun `a 401 refreshes once and retries, saving the rotated refresh token`() = runTest {
        var refreshes = 0
        val rig = AuthTestRig(backgroundScope) { request ->
            if (request.url.host == "auth.example.com") {
                refreshes++
                assertTrue(bodyText(request).contains("refresh_token=refresh-1"))
                assertTrue(bodyText(request).contains("client_id=sekund-test"))
                delay(50) // long enough for the other request to arrive while refreshing
                respondJson("""{"access_token": "new", "refresh_token": "refresh-2", "expires_in": 3600}""")
            } else if (request.headers[HttpHeaders.Authorization] == "Bearer new") {
                respond("data")
            } else {
                respond("expired", HttpStatusCode.Unauthorized)
            }
        }
        rig.addProvider(EXAMPLE_OAUTH)
        val account = rig.registry.saveSignIn(
            rig.registry.requireProvider("example"), 0, emptyMap(),
            Credential(accessToken = "old", refreshToken = "refresh-1"), null,
        )
        val client = PluginAuthAccess(rig.registry, "test", setOf("example")).client(AccountRef("example", account.id))

        // Two requests at once both get a 401, but only one refresh happens.
        val first = async { client.get("https://api.example.com/a").bodyAsText() }
        val second = async { client.get("https://api.example.com/b").bodyAsText() }

        assertEquals("data", first.await())
        assertEquals("data", second.await())
        assertEquals(1, refreshes)
        val saved = SekundJson.decodeFromString(Credential.serializer(), rig.secrets.values.getValue(account.secretKey))
        assertEquals("refresh-2", saved.refreshToken)
        assertEquals(rig.clock.epochSeconds + 3600, saved.expiresAtEpochSeconds)
    }

    @Test
    fun `tokens about to expire are refreshed before the request, keeping a refresh token that wasn't replaced`() = runTest {
        val requests = mutableListOf<String>()
        val rig = AuthTestRig(backgroundScope) { request ->
            requests.add(request.url.host + " " + request.headers[HttpHeaders.Authorization])
            if (request.url.host == "auth.example.com") {
                respondJson("""{"access_token": "new", "expires_in": 3600}""")
            } else {
                respond("data")
            }
        }
        rig.addProvider(EXAMPLE_OAUTH)
        val account = rig.registry.saveSignIn(
            rig.registry.requireProvider("example"), 0, emptyMap(),
            Credential(accessToken = "old", refreshToken = "refresh-1", expiresAtEpochSeconds = rig.clock.epochSeconds + 30),
            null,
        )
        val client = PluginAuthAccess(rig.registry, "test", setOf("example")).client(AccountRef("example", account.id))

        client.get("https://api.example.com/a")

        assertEquals(listOf("auth.example.com null", "api.example.com Bearer new"), requests)
        val saved = SekundJson.decodeFromString(Credential.serializer(), rig.secrets.values.getValue(account.secretKey))
        assertEquals("refresh-1", saved.refreshToken)
    }

    @Test
    fun `a refused refresh asks the user to reconnect, once, centrally`() = runTest {
        val rig = AuthTestRig(backgroundScope) { request ->
            if (request.url.host == "auth.example.com") {
                respondJson("""{"error": "invalid_grant"}""", HttpStatusCode.BadRequest)
            } else {
                respond("expired", HttpStatusCode.Unauthorized)
            }
        }
        rig.addProvider(EXAMPLE_OAUTH)
        val account = rig.registry.saveSignIn(
            rig.registry.requireProvider("example"), 0, emptyMap(),
            Credential(accessToken = "old", refreshToken = "revoked"), null,
        )
        val ref = AccountRef("example", account.id)
        val access = PluginAuthAccess(rig.registry, "test", setOf("example"))

        // The plugin sees the 401 itself...
        assertEquals(HttpStatusCode.Unauthorized, access.client(ref).get("https://api.example.com/a").status)
        // ...the account is marked, remembered across restarts, and one alert is shown...
        assertIs<AuthState.NeedsReauth>(access.state(ref).first())
        assertNotNull(rig.accountsFile.read().single().needsReauth)
        assertNotNull(rig.alerts.alerts.value[AuthRegistry.alertOwner(account.id)])
        // ...and later requests fail straight away instead of hammering the service.
        assertFailsWith<AccountUnavailableException> { access.client(ref).get("https://api.example.com/a") }
    }

    @Test
    fun `removing an account forgets its secret and clears its alert`() = runTest {
        val rig = AuthTestRig(backgroundScope) { respond("ok") }
        rig.addProvider(EXAMPLE_OAUTH)
        val account = rig.registry.saveSignIn(rig.registry.requireProvider("example"), 0, emptyMap(), Credential(accessToken = "a"), null)

        rig.registry.remove(account.id)

        assertTrue(rig.secrets.values.isEmpty())
        assertTrue(rig.registry.accounts.value.isEmpty())
        assertEquals(AuthState.NotFound, rig.registry.state(account.id).first())
    }
}
