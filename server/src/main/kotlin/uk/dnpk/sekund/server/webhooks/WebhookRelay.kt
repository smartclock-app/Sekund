package uk.dnpk.sekund.server.webhooks

import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.server.Secrets
import uk.dnpk.sekund.server.relay.Outbox

/**
 * The generic webhook relay: a device asks for a public URL (`webhook.register`), other services POST
 * to it, and each request is passed on to the device as a `webhook.event`.
 *
 * Nothing here knows what the hooks are for: Google Calendar's push notifications are just one user.
 *
 * @param publicUrl the server's public address; webhooks are off without one.
 */
class WebhookRelay(
    private val store: WebhookStore,
    private val outbox: Outbox,
    private val publicUrl: String?,
) {
    /** Answers a device's `webhook.register` with the hook's URL (or an error if webhooks are off). */
    fun register(deviceId: String, request: RelayMessage.WebhookRegister): RelayMessage {
        if (publicUrl == null) return RelayMessage.Error(request.id, "This server has no publicUrl, so it can't receive webhooks")
        if (request.key.isBlank() || request.key.length > 200) return RelayMessage.Error(request.id, "Webhook keys are 1 to 200 characters")
        val hookId = store.register(deviceId, request.key)
        return RelayMessage.WebhookRegistered(replyTo = request.id, key = request.key, url = "$publicUrl/hooks/$hookId")
    }

    /**
     * Someone POSTed to `/hooks/{hookId}`. Returns false if there's no such hook. The device gets it
     * as soon as it's connected (see [Outbox]).
     */
    suspend fun received(hookId: String, headers: Map<String, String>, body: String): Boolean {
        val hook = store.find(hookId) ?: return false
        val id = "wh_" + Secrets.randomToken(9)
        val event = RelayMessage.WebhookEvent(id = id, key = hook.key, headers = headers, body = body)
        outbox.deliver(hook.deviceId, id, event)
        return true
    }
}
