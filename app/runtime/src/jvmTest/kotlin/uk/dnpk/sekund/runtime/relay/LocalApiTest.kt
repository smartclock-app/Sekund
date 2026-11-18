package uk.dnpk.sekund.runtime.relay

import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.runtime.api.startedTestGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Requests from the server go through the real device API routes, over loopback. */
class LocalApiTest {
    @Test
    fun `a relayed request is answered like the same request on the LAN`() = runBlocking {
        val graph = startedTestGraph()
        val local = LocalApi(graph.api, graph.http)
        try {
            val read = local.handle(RelayMessage.RpcRequest(id = "1", method = "GET", path = "/v1/config"))
            assertEquals(200, read.status)
            val etag = assertNotNull(read.headers[HttpHeaders.ETag])

            val patch = local.handle(
                RelayMessage.RpcRequest(
                    id = "2",
                    method = "PATCH",
                    path = "/v1/config",
                    headers = mapOf(HttpHeaders.ContentType to "application/json", HttpHeaders.IfMatch to etag),
                    body = """{"clock":{"use24Hour":true}}""",
                ),
            )
            assertEquals(200, patch.status, patch.body)
            assertTrue(graph.config.config.value.clock.use24Hour)

            val stale = local.handle(
                RelayMessage.RpcRequest(
                    id = "3",
                    method = "PATCH",
                    path = "/v1/config",
                    headers = mapOf(HttpHeaders.ContentType to "application/json", HttpHeaders.IfMatch to etag),
                    body = "{}",
                ),
            )
            assertEquals(412, stale.status)
        } finally {
            local.stop()
        }
    }
}
