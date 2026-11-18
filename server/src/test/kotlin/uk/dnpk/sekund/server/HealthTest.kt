package uk.dnpk.sekund.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import uk.dnpk.sekund.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthTest {
    @Test
    fun `health endpoint responds ok`() = testApplication {
        application { module() }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().startsWith("ok"))
    }
}
