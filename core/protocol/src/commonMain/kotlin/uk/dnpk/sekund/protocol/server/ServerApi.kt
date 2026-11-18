package uk.dnpk.sekund.protocol.server

import kotlinx.serialization.Serializable

// The optional server's own API (accounts and devices), used by the companion app.
// Requests to a device through the server use the device API's types, at
// `https://server/v1/devices/{deviceId}/v1/...` (see docs/api/relay-protocol.md).

/** `POST /v1/users` (create an account) and `POST /v1/sessions` (sign in). */
@Serializable
data class Credentials(
    val username: String,
    val password: String,
)

/** A signed-in session. Send [token] as `Authorization: Bearer <token>` on every other request. */
@Serializable
data class SessionStarted(
    val token: String,
    val user: ServerUser,
)

/** `GET /v1/me`. */
@Serializable
data class ServerUser(
    val id: String,
    val username: String,
)

/** `POST /v1/devices/claim`: the code a device shows on screen while nobody owns it. */
@Serializable
data class ClaimDevice(val code: String)

/**
 * One of the signed-in user's devices (`GET /v1/devices`).
 *
 * @property online whether the device is connected to the server right now (only then do requests through
 *   the server work).
 * @property lastSeenAt when the device last connected or disconnected (ISO-8601).
 */
@Serializable
data class ServerDevice(
    val id: String,
    val name: String,
    val online: Boolean,
    val appVersion: String? = null,
    val claimedAt: String,
    val lastSeenAt: String? = null,
)
