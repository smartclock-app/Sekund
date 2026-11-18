package uk.dnpk.sekund.protocol.api

import kotlinx.serialization.Serializable

/**
 * Something the device can be asked to do with `POST /v1/actions/{name}`, as listed by `GET /v1/actions`.
 *
 * Names look like `device.reload` or `<instanceId>.refresh`.
 */
@Serializable
data class ActionInfo(
    val name: String,
    val description: String,
)

/** The answer to running an action. */
@Serializable
data class ActionResult(
    val name: String,
    val ok: Boolean,
    val message: String? = null,
)
