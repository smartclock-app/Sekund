package uk.dnpk.sekund.runtime.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.content.TextContent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.auth.BodyEncoding
import uk.dnpk.sekund.protocol.auth.ClientAuth
import kotlin.io.encoding.Base64

/**
 * Sends one request to an OAuth endpoint (token, device code, revoke) the way a definition asks:
 * as a form or JSON, with the client credentials in the body or a Basic header.
 *
 * Network errors (no connection, timeout) are thrown as they are. HTTP errors are returned as
 * [EndpointAnswer]s, because "400 authorization_pending" is a normal answer while polling.
 */
class TokenEndpoint(private val http: HttpClient) {

    suspend fun post(
        url: String,
        params: Map<String, String>,
        encoding: BodyEncoding,
        clientAuth: ClientAuth,
        clientId: String?,
        clientSecret: String?,
    ): EndpointAnswer {
        val body = params.toMutableMap()
        var basicHeader: String? = null
        when (clientAuth) {
            ClientAuth.Body -> {
                if (clientId != null) body["client_id"] = clientId
                if (clientSecret != null) body["client_secret"] = clientSecret
            }
            ClientAuth.None -> {
                if (clientId != null) body["client_id"] = clientId
            }
            ClientAuth.Basic -> {
                val pair = (clientId ?: "") + ":" + (clientSecret ?: "")
                basicHeader = "Basic " + Base64.encode(pair.encodeToByteArray())
            }
        }

        val response = http.post(url) {
            header(HttpHeaders.Accept, "application/json")
            if (basicHeader != null) header(HttpHeaders.Authorization, basicHeader)
            if (encoding == BodyEncoding.Json) {
                setBody(TextContent(jsonOf(body).toString(), ContentType.Application.Json))
            } else {
                setBody(FormDataContent(formOf(body)))
            }
        }
        return EndpointAnswer(response.status.value, response.bodyAsText())
    }

    private fun jsonOf(values: Map<String, String>): JsonObject {
        val elements = mutableMapOf<String, JsonPrimitive>()
        for ((key, value) in values) elements[key] = JsonPrimitive(value)
        return JsonObject(elements)
    }

    private fun formOf(values: Map<String, String>): Parameters {
        val builder = ParametersBuilder()
        for ((key, value) in values) builder.append(key, value)
        return builder.build()
    }
}

/** An endpoint's HTTP status and raw body. */
class EndpointAnswer(val status: Int, val body: String) {
    val isSuccess: Boolean
        get() = status in 200..299

    /** The body as a [TokenResponse], or an empty one if it isn't JSON (e.g. Trakt's empty 400s). */
    fun tokens(): TokenResponse {
        if (body.isBlank()) return TokenResponse()
        try {
            return SekundJson.decodeFromString(TokenResponse.serializer(), body)
        } catch (error: SerializationException) {
            return TokenResponse()
        } catch (error: IllegalArgumentException) {
            return TokenResponse()
        }
    }

    /** Short text for logs and error messages, e.g. `400 invalid_grant`. */
    fun describe(): String {
        val tokens = tokens()
        val error = tokens.error ?: return "HTTP $status"
        val description = tokens.errorDescription ?: return "HTTP $status $error"
        return "HTTP $status $error: $description"
    }
}
