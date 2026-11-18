package uk.dnpk.sekund.protocol.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Every error from the device API has this shape, with a matching HTTP status:
 * `{"error": {"code": "not_found", "message": "No instance called 'x'", "details": ...}}`.
 *
 * [ErrorBody.code] is stable and meant for programs; [ErrorBody.message] is for people.
 */
@Serializable
data class ApiError(val error: ErrorBody)

/**
 * @property code one of: `bad_request`, `unauthorized`, `not_found`, `conflict`, `precondition_failed`,
 *   `invalid` (validation failed: see [details]), `too_many_attempts`, `unavailable`.
 * @property details extra data, e.g. the list of validation issues.
 */
@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
)
