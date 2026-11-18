package uk.dnpk.sekund.server.http

/**
 * Thrown while handling a request to answer with an error. Turned into the same error shape as the
 * device API, `{"error": {"code": ..., "message": ...}}`, with [status] as the HTTP status.
 */
class ServerProblem(
    val status: Int,
    val code: String,
    override val message: String,
) : Exception(message) {
    companion object {
        fun badRequest(message: String) = ServerProblem(400, "bad_request", message)

        fun unauthorized(message: String) = ServerProblem(401, "unauthorized", message)

        fun forbidden(message: String) = ServerProblem(403, "forbidden", message)

        fun notFound(message: String) = ServerProblem(404, "not_found", message)

        fun conflict(message: String) = ServerProblem(409, "conflict", message)

        fun unavailable(message: String) = ServerProblem(503, "unavailable", message)
    }
}
