package uk.dnpk.sekund.server

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import uk.dnpk.sekund.protocol.server.Credentials
import uk.dnpk.sekund.protocol.server.ServerUser
import uk.dnpk.sekund.protocol.server.SessionStarted
import uk.dnpk.sekund.server.config.ServerConfig
import uk.dnpk.sekund.server.users.PasswordHasher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UsersTest {
    @Test
    fun `the first account can always be created and is signed in`() = testApplication {
        val client = startServer(newTestServer())

        val session = client.signUp("Sam")

        assertEquals("sam", session.user.username)
        val me = client.get("/v1/me") { bearerAuth(session.token) }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals(session.user, me.body<ServerUser>())
    }

    @Test
    fun `more accounts need allowSignups`() = testApplication {
        val client = startServer(newTestServer(ServerConfig(allowSignups = false)))
        client.signUp("sam")

        val second = client.post("/v1/users") {
            contentType(ContentType.Application.Json)
            setBody(Credentials("alex", "another password"))
        }

        assertEquals(HttpStatusCode.Forbidden, second.status)
    }

    @Test
    fun `signing in checks the password`() = testApplication {
        val client = startServer(newTestServer())
        client.signUp("sam", "correct horse")

        val wrong = client.post("/v1/sessions") {
            contentType(ContentType.Application.Json)
            setBody(Credentials("sam", "wrong horse"))
        }
        val right = client.post("/v1/sessions") {
            contentType(ContentType.Application.Json)
            setBody(Credentials("SAM", "correct horse"))
        }

        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertEquals(HttpStatusCode.OK, right.status)
        assertEquals("sam", right.body<SessionStarted>().user.username)
    }

    @Test
    fun `signing out ends only that session`() = testApplication {
        val client = startServer(newTestServer())
        val first = client.signUp("sam")

        client.delete("/v1/sessions/current") { bearerAuth(first.token) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/me") { bearerAuth(first.token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/me").status)
    }

    @Test
    fun `password hashes are salted and checkable`() {
        val hasher = PasswordHasher()

        val one = hasher.hash("hunter22")
        val two = hasher.hash("hunter22")

        assertNotEquals(one, two)
        assertTrue(hasher.matches("hunter22", one))
        assertFalse(hasher.matches("hunter23", one))
        assertFalse(hasher.matches("hunter22", "not a hash"))
    }
}
