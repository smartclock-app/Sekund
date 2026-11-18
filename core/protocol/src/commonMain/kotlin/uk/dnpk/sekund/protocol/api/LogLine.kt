package uk.dnpk.sekund.protocol.api

import kotlinx.serialization.Serializable

/** One line of the device's log, as returned by `GET /v1/logs?tail=200`. */
@Serializable
data class LogLine(
    val at: String,
    val level: String,
    val tag: String,
    val message: String,
)
