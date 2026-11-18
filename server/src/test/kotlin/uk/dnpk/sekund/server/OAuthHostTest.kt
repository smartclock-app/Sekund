package uk.dnpk.sekund.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.server.testing.testApplication
import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.server.config.OAuthClientConfig
import uk.dnpk.sekund.server.config.ServerConfig
import uk.dnpk.sekund.server.oauth.OAuthHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OAuthHostTest {
    private val config = ServerConfig(
        publicUrl = "https://sekund.example.org",
        oauth = mapOf(
            "google" to OAuthClientConfig(
                clientId = "server-client",
                clientSecret = "server-secret",
                authorizeUrl = "https://accounts.example.com/auth",
                tokenUrl = "https://tokens.example.com/token",
            ),
        ),
    )

    /** What the pretend provider's token endpoint was sent, one form per request. */
    private val tokenRequests = mutableListOf<Parameters>()

    private val provider = HttpClient(
        MockEngine { request ->
            val form = (request.body as FormDataContent).formData
            tokenRequests.add(form)
            val json = if (form["grant_type"] == "authorization_code") {
                """{"access_token":"access-1","refresh_token":"refresh-1","expires_in":3600}"""
            } else {
                """{"access_token":"access-2","expires_in":3600}"""
            }
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    @Test
    fun `a device signs in with the server's client and gets the tokens`() = testApplication {
        val client = startServer(newTestServer(config, provider))
        val browser = createClient { followRedirects = false }
        val (device, _) = claimedDevice(client, client.signUp("sam"))

        device.send(RelayMessage.OAuthStart(id = "o1", provider = "google", scopes = listOf("openid", "calendar"), extraAuthorizeParams = mapOf("access_type" to "offline")))
        val started = device.expect<RelayMessage.OAuthStarted>()
        assertTrue(started.url.startsWith("https://sekund.example.org/oauth/google/start?flow="))

        // The person opens the link: it forwards to Google with the server's client.
        val redirect = browser.get(started.url.removePrefix("https://sekund.example.org"))
        assertEquals(HttpStatusCode.Found, redirect.status)
        val authorize = Url(redirect.headers[HttpHeaders.Location]!!)
        assertEquals("accounts.example.com", authorize.host)
        assertEquals("server-client", authorize.parameters["client_id"])
        assertEquals("https://sekund.example.org/oauth/google/callback", authorize.parameters["redirect_uri"])
        assertEquals("openid calendar", authorize.parameters["scope"])
        assertEquals("offline", authorize.parameters["access_type"])
        assertEquals("S256", authorize.parameters["code_challenge_method"])
        val state = authorize.parameters["state"]!!

        // Google sends the browser back with a code.
        val done = browser.get("/oauth/google/callback?code=the-code&state=$state")
        assertTrue(done.bodyAsText().contains("Signed in"))

        val delivered = device.expect<RelayMessage.CredentialDelivered>()
        assertEquals(started.flowId, delivered.flowId)
        assertEquals("access-1", delivered.accessToken)
        assertEquals("refresh-1", delivered.refreshToken)
        device.send(RelayMessage.Ack(delivered.id))

        val exchange = tokenRequests.single()
        assertEquals("the-code", exchange["code"])
        assertEquals("server-secret", exchange["client_secret"])
        assertEquals(OAuthHost.challengeOf(exchange["code_verifier"]!!), authorize.parameters["code_challenge"])

        // The same state can't be used twice.
        assertTrue(browser.get("/oauth/google/callback?code=again&state=$state").bodyAsText().contains("expired"))
    }

    @Test
    fun `refreshing goes through the server, which adds the client secret`() = testApplication {
        val client = startServer(newTestServer(config, provider))
        val (device, _) = claimedDevice(client, client.signUp("sam"))

        device.send(RelayMessage.TokenRefresh(id = "t1", provider = "google", refreshToken = "refresh-1"))

        val refreshed = device.expect<RelayMessage.TokenRefreshed>()
        assertEquals("t1", refreshed.replyTo)
        assertEquals(200, refreshed.status)
        assertTrue(refreshed.body.contains("access-2"))
        assertEquals("server-secret", tokenRequests.single()["client_secret"])
        assertEquals("refresh-1", tokenRequests.single()["refresh_token"])
    }

    @Test
    fun `a cancelled sign-in is reported to the device, and unknown providers are refused`() = testApplication {
        val client = startServer(newTestServer(config, provider))
        val browser = createClient { followRedirects = false }
        val (device, _) = claimedDevice(client, client.signUp("sam"))

        device.send(RelayMessage.OAuthStart(id = "o1", provider = "dropbox"))
        device.expect<RelayMessage.Error>()

        device.send(RelayMessage.OAuthStart(id = "o2", provider = "google"))
        val started = device.expect<RelayMessage.OAuthStarted>()
        val state = Url(browser.get(started.url.removePrefix("https://sekund.example.org")).headers[HttpHeaders.Location]!!).parameters["state"]
        browser.get("/oauth/google/callback?error=access_denied&state=$state")

        val failed = device.expect<RelayMessage.OAuthFailed>()
        assertEquals(started.flowId, failed.flowId)
        assertTrue(tokenRequests.isEmpty())
    }
}
