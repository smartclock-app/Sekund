package uk.dnpk.sekund.runtime.relay

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.runtime.api.DeviceApi
import uk.dnpk.sekund.runtime.api.LanApiServer

/**
 * Answers device API requests that came through the server by making the very same request to the
 * device API, privately, over loopback (127.0.0.1, a free port, started the first time it's needed).
 *
 * Going through the real routes means a request behaves exactly the same on the LAN and through the
 * server, with no second copy of the API to keep in step. It's authorised with [DeviceApi.localToken].
 */
class LocalApi(
    private val api: DeviceApi,
    private val http: HttpClient,
) : RpcHandler {
    private val lock = Mutex()
    private var server: LanApiServer? = null

    override suspend fun handle(request: RelayMessage.RpcRequest): RelayMessage.RpcResponse {
        val port = port() ?: return RelayMessage.RpcResponse(request.id, 503, body = ERROR_UNAVAILABLE)
        val query = request.query
        val url = "http://127.0.0.1:$port${request.path}" + (if (query.isNullOrEmpty()) "" else "?$query")

        val response = http.request(url) {
            method = HttpMethod.parse(request.method)
            header(HttpHeaders.Authorization, "Bearer ${api.localToken}")
            for ((name, value) in request.headers) {
                if (name.equals(HttpHeaders.IfMatch, ignoreCase = true) || name.equals(HttpHeaders.Accept, ignoreCase = true)) {
                    header(name, value)
                }
            }
            val body = request.body
            if (body != null) {
                val type = headerOf(request.headers, HttpHeaders.ContentType) ?: "application/json"
                setBody(TextContent(body, ContentType.parse(type)))
            }
        }

        val headers = mutableMapOf<String, String>()
        val etag = response.headers[HttpHeaders.ETag]
        if (etag != null) headers[HttpHeaders.ETag] = etag
        val contentType = response.headers[HttpHeaders.ContentType]
        if (contentType != null) headers[HttpHeaders.ContentType] = contentType
        val text = response.bodyAsText()
        return RelayMessage.RpcResponse(request.id, response.status.value, headers, text.ifEmpty { null })
    }

    /** The loopback server's port, starting it if needed. Null if it couldn't start. */
    private suspend fun port(): Int? = lock.withLock {
        val running = server
        if (running != null) return@withLock running.boundPort
        val created = LanApiServer(api, port = 0, host = "127.0.0.1")
        if (!created.start()) return@withLock null
        server = created
        created.boundPort
    }

    fun stop() {
        server?.stop()
        server = null
    }

    private fun headerOf(headers: Map<String, String>, name: String): String? {
        for ((key, value) in headers) {
            if (key.equals(name, ignoreCase = true)) return value
        }
        return null
    }

    private companion object {
        const val ERROR_UNAVAILABLE = """{"error":{"code":"unavailable","message":"The device API couldn't start"}}"""
    }
}
