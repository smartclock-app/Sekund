package uk.dnpk.sekund.protocol.api

import kotlinx.serialization.Serializable

/** `POST /v1/pair`: a companion asks to pair. The device then shows a 6-digit PIN on screen. */
@Serializable
data class PairRequest(val clientName: String)

/** The answer to [PairRequest]: which pairing to confirm, and until when (ISO-8601). */
@Serializable
data class PairStarted(
    val pairingId: String,
    val expiresAt: String,
)

/** `POST /v1/pair/confirm`: the PIN the person read off the screen. */
@Serializable
data class PairConfirm(
    val pairingId: String,
    val pin: String,
)

/**
 * The answer to [PairConfirm]. Send [token] as `Authorization: Bearer <token>` on every other request.
 * It's shown only this once; the device keeps just a hash of it.
 */
@Serializable
data class PairResult(
    val token: String,
    val clientId: String,
    val deviceId: String,
)

/** A paired companion, as listed by `GET /v1/clients` and on the device. */
@Serializable
data class PairedClient(
    val id: String,
    val name: String,
    val pairedAt: String,
    val lastSeenAt: String? = null,
)
