package uk.dnpk.sekund.runtime.api

import kotlinx.serialization.json.JsonElement

/**
 * Thrown anywhere while handling an API request to answer with an error. The routes turn it into
 * `{"error": {"code": ..., "message": ...}}` with [status] as the HTTP status code.
 */
class ApiProblem(
    val status: Int,
    val code: String,
    override val message: String,
    val details: JsonElement? = null,
) : Exception(message) {
    companion object {
        fun badRequest(message: String) = ApiProblem(400, "bad_request", message)

        fun notFound(message: String) = ApiProblem(404, "not_found", message)

        fun conflict(message: String) = ApiProblem(409, "conflict", message)
    }
}
