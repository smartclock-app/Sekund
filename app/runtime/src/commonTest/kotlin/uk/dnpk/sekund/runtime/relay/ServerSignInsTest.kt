package uk.dnpk.sekund.runtime.relay

import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.runtime.auth.AuthTestRig
import uk.dnpk.sekund.runtime.auth.Credential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerSignInsTest {
    /** A provider that allows the server's client ("byoOrServer"). */
    private val provider = """
        {
          "id": "example",
          "name": "Example",
          "methods": [{
            "type": "oauth2.authorizationCode",
            "authorizeUrl": "https://auth.example.com/authorize",
            "tokenUrl": "https://auth.example.com/token",
            "scopes": ["read"],
            "client": { "mode": "byoOrServer" }
          }],
          "baseUrls": ["https://api.example.com"]
        }
    """.trimIndent()

    /** Plays the server: hands out a sign-in link, and refreshes tokens. */
    private val server = FakeRelayLink { message ->
        when (message) {
            is RelayMessage.OAuthStart -> RelayMessage.OAuthStarted(message.id, "fl_1", "https://server/oauth/example/start?flow=fl_1", "2030-01-01T00:00:00Z")
            is RelayMessage.TokenRefresh -> RelayMessage.TokenRefreshed(message.id, 200, """{"access_token":"fresh","expires_in":3600}""")
            else -> null
        }
    }

    @Test
    fun `signing in through the server shows a link, then stores the delivered tokens`() = runTest {
        // The token endpoint must never be called directly: only the server knows the client secret.
        val rig = AuthTestRig(backgroundScope) { respondError(HttpStatusCode.InternalServerError) }
        rig.addProvider(provider)
        val signIns = ServerSignIns(rig.registry, rig.alerts)
        assertFalse(signIns.canSignIn("example", 0))

        signIns.connected(server, RelayMessage.Welcome(owner = "sam", oauthProviders = listOf("example")))
        assertTrue(signIns.canSignIn("example", 0))

        val signIn = signIns.start("example", 0, emptyMap(), reconnect = null)
        val waiting = assertIs<ServerSignInStatus.Waiting>(signIn.status.value)
        assertEquals("https://server/oauth/example/start?flow=fl_1", waiting.url)
        assertEquals(waiting.url, rig.alerts.alerts.value.getValue("signin:fl_1").qrCode)
        assertEquals(listOf("read"), (server.sent.single() as RelayMessage.OAuthStart).scopes)

        signIns.handle(server, RelayMessage.CredentialDelivered("c1", "fl_1", accessToken = "access", refreshToken = "refresh", expiresInSeconds = 3600))

        val connected = assertIs<ServerSignInStatus.Connected>(signIn.status.value)
        val stored = rig.registry.readCredential(connected.account)
        assertEquals("access", stored?.accessToken)
        assertEquals(true, stored?.viaServer)
        assertEquals(RelayMessage.Ack("c1"), server.sent.last())
        assertNull(rig.alerts.alerts.value["signin:fl_1"])
    }

    @Test
    fun `accounts from the server's client are refreshed through the server`() = runTest {
        lateinit var signIns: ServerSignIns
        val rig = AuthTestRig(backgroundScope, serverRefresh = { provider, token -> signIns.refresh(provider, token) }) {
            respondError(HttpStatusCode.InternalServerError)
        }
        rig.addProvider(provider)
        signIns = ServerSignIns(rig.registry, rig.alerts)
        signIns.connected(server, RelayMessage.Welcome(owner = "sam", oauthProviders = listOf("example")))
        val expired = Credential(accessToken = "old", refreshToken = "refresh", expiresAtEpochSeconds = rig.clock.epochSeconds - 10, viaServer = true)
        val account = rig.registry.saveSignIn(rig.registry.requireProvider("example"), 0, emptyMap(), expired, null)

        val signing = rig.registry.credentialForRequest(account.id)

        assertEquals("fresh", signing.credential.accessToken)
        assertEquals("refresh", signing.credential.refreshToken) // not rotated, so kept
        assertEquals(true, signing.credential.viaServer)
        val asked = server.sent.filterIsInstance<RelayMessage.TokenRefresh>().single()
        assertEquals("example", asked.provider)
        assertEquals("refresh", asked.refreshToken)
    }
}
