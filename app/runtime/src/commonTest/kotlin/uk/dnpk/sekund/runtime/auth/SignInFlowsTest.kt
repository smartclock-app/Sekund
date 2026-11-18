package uk.dnpk.sekund.runtime.auth

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import uk.dnpk.sekund.runtime.platform.sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignInFlowsTest {

    @Test
    fun `device sign-in with Trakt's status codes`() = runTest {
        val trakt = BuiltInAuthProviders.read("trakt.auth.json")
        var polls = 0
        val rig = AuthTestRig(backgroundScope) { request ->
            when (request.url.encodedPath) {
                "/oauth/device/code" -> {
                    assertEquals("""{"client_id":"my-client"}""", bodyText(request))
                    respondJson("""{"device_code": "dev-1", "user_code": "AB12CD34", "verification_url": "https://trakt.tv/activate", "expires_in": 600, "interval": 5}""")
                }
                "/oauth/device/token" -> {
                    polls++
                    assertTrue(bodyText(request).contains(""""code":"dev-1""""))
                    if (polls < 3) respond("", HttpStatusCode.BadRequest) // Trakt's "still waiting"
                    else respondJson("""{"access_token": "tok", "refresh_token": "ref", "expires_in": 7776000}""")
                }
                "/users/settings" -> {
                    assertEquals("my-client", request.headers["trakt-api-key"])
                    assertEquals("Bearer tok", request.headers["Authorization"])
                    respondJson("""{"user": {"username": "sam"}}""")
                }
                else -> error("Unexpected ${request.url}")
            }
        }
        rig.addProvider(trakt)

        val signIn = DeviceSignIn.start(rig.registry, rig.alerts, "trakt", 0, emptyMap(), "my-client", "my-secret")
        val waiting = assertIs<DeviceSignInStatus.Waiting>(signIn.status.value)
        assertEquals("AB12CD34", waiting.userCode)
        assertNotNull(rig.alerts.alerts.value["auth:signin:trakt"])

        val done = signIn.status.first { status -> status !is DeviceSignInStatus.Waiting }
        val connected = assertIs<DeviceSignInStatus.Connected>(done)
        assertEquals("sam", connected.account.label)
        assertEquals(3, polls)
        assertNull(rig.alerts.alerts.value["auth:signin:trakt"])
    }

    @Test
    fun `device sign-in reports a declined request`() = runTest {
        val rig = AuthTestRig(backgroundScope) { request ->
            if (request.url.encodedPath == "/oauth/device/code") {
                respondJson("""{"device_code": "d", "user_code": "U", "verification_url": "https://trakt.tv/activate"}""")
            } else {
                respond("", HttpStatusCode(418, "Denied"))
            }
        }
        rig.addProvider(BuiltInAuthProviders.read("trakt.auth.json"))

        val signIn = DeviceSignIn.start(rig.registry, rig.alerts, "trakt", 0, emptyMap(), "c", "s")

        val done = signIn.status.first { status -> status !is DeviceSignInStatus.Waiting }
        assertEquals(DeviceSignInStatus.Failed("Sign-in was declined."), done)
    }

    @Test
    fun `authorization code sign-in uses PKCE and checks state`() = runTest {
        var sentVerifier: String? = null
        val rig = AuthTestRig(backgroundScope) { request ->
            val body = bodyText(request)
            sentVerifier = body.substringAfter("code_verifier=").substringBefore('&')
            assertTrue(body.contains("code=the-code"))
            respondJson("""{"access_token": "tok", "refresh_token": "ref", "expires_in": 3600}""")
        }
        rig.addProvider(EXAMPLE_OAUTH)
        val signIns = AuthCodeSignIns(rig.registry)

        val request = signIns.start("example", 0, emptyMap(), null, null, redirectUri = "sekund://oauth")
        val url = Url(request.authorizeUrl)
        val state = url.parameters["state"]!!
        assertEquals("read write", url.parameters["scope"])
        assertEquals("sekund-test", url.parameters["client_id"])

        assertFailsWith<IllegalArgumentException> { signIns.complete(request.flowId, "the-code", "forged-state") }
        // A wrong state doesn't burn the sign-in; the real answer still works.
        val account = signIns.complete(request.flowId, "the-code", state)

        assertEquals("Example", account.label)
        val expectedChallenge = urlSafeBase64.encode(sha256(sentVerifier!!.encodeToByteArray()))
        assertEquals(expectedChallenge, url.parameters["code_challenge"])
        assertFailsWith<IllegalArgumentException> { signIns.complete(request.flowId, "the-code", state) } // used up
    }

    @Test
    fun `unfinished browser sign-ins expire after ten minutes`() = runTest {
        val rig = AuthTestRig(backgroundScope) { respond("") }
        rig.addProvider(EXAMPLE_OAUTH)
        val signIns = AuthCodeSignIns(rig.registry)
        val request = signIns.start("example", 0, emptyMap(), null, null, redirectUri = "sekund://oauth")

        rig.clock.epochSeconds += 11 * 60

        val state = Url(request.authorizeUrl).parameters["state"]!!
        assertFailsWith<IllegalArgumentException> { signIns.complete(request.flowId, "code", state) }
    }
}
