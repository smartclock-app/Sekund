package uk.dnpk.sekund.plugins.homeassistant

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * One conversation over Home Assistant's WebSocket API, without the networking: feed it each message
 * the server sends ([onMessage]) and send back whatever it returns. Kept free of I/O so it's easy to test.
 *
 * The conversation (https://developers.home-assistant.io/docs/api/websocket):
 * 1. server: `auth_required` → we send `{"type": "auth", "access_token": ...}`
 * 2. server: `auth_ok` (or `auth_invalid`) → we subscribe to a state trigger for each sensor
 * 3. server: `event` each time a sensor changes → we report [HaEvent.Triggered]
 *
 * @param triggers the sensors to watch, e.g. `binary_sensor.doorbell`.
 */
class HaSession(private val token: String, triggers: List<String>) {
    private val triggers: List<String> = triggers.filter { trigger -> trigger.isNotBlank() }.distinct()

    /** Which sensor each subscription (by message id) is for. */
    private val subscriptions = mutableMapOf<Int, String>()

    /** Message ids must go up by one for each message we send in a connection. */
    private var nextId = 1

    /** What to send back, and what (if anything) happened. */
    class Step(val replies: List<String>, val event: HaEvent?)

    fun onMessage(text: String): Step {
        val message = parse(text) ?: return Step(emptyList(), null)
        val type = message["type"]?.jsonPrimitive?.contentOrNull

        return when (type) {
            "auth_required" -> Step(listOf(authMessage()), null)
            "auth_ok" -> Step(subscribeMessages(), HaEvent.Authenticated)
            "auth_invalid" -> {
                val reason = message["message"]?.jsonPrimitive?.contentOrNull ?: "Invalid access token"
                Step(emptyList(), HaEvent.Rejected(reason))
            }
            "result" -> Step(emptyList(), failedSubscription(message))
            "event" -> Step(emptyList(), triggered(message))
            else -> Step(emptyList(), null)
        }
    }

    private fun authMessage(): String {
        val message = buildJsonObject {
            put("type", "auth")
            put("access_token", token)
        }
        return message.toString()
    }

    private fun subscribeMessages(): List<String> {
        val messages = mutableListOf<String>()
        for (trigger in triggers) {
            val id = nextId
            nextId += 1
            subscriptions[id] = trigger
            val message = buildJsonObject {
                put("id", id)
                put("type", "subscribe_trigger")
                putJsonObject("trigger") {
                    put("platform", "state")
                    put("entity_id", trigger)
                }
            }
            messages.add(message.toString())
        }
        return messages
    }

    /** `{"type": "result", "success": false}` for one of our subscriptions: usually a misspelt sensor. */
    private fun failedSubscription(message: JsonObject): HaEvent? {
        val success = message["success"]?.jsonPrimitive?.contentOrNull
        if (success != "false") return null
        val id = message["id"]?.jsonPrimitive?.intOrNull ?: return null
        val trigger = subscriptions[id] ?: return null
        val error = message["error"] as? JsonObject
        val reason = error?.get("message")?.jsonPrimitive?.contentOrNull ?: "unknown error"
        return HaEvent.SubscriptionFailed(trigger, reason)
    }

    /** `event.variables.trigger` holds the sensor and its new state. */
    private fun triggered(message: JsonObject): HaEvent? {
        val event = message["event"] as? JsonObject ?: return null
        val variables = event["variables"] as? JsonObject ?: return null
        val trigger = variables["trigger"] as? JsonObject ?: return null

        val id = message["id"]?.jsonPrimitive?.intOrNull
        val entityId = trigger["entity_id"]?.jsonPrimitive?.contentOrNull ?: subscriptions[id] ?: return null
        val toState = trigger["to_state"] as? JsonObject
        // to_state is null when the sensor was removed: treat that as "off".
        val state = toState?.get("state")?.jsonPrimitive?.contentOrNull ?: "unavailable"
        return HaEvent.Triggered(entityId, state)
    }

    private fun parse(text: String): JsonObject? {
        try {
            return Json.parseToJsonElement(text) as? JsonObject
        } catch (error: Exception) {
            return null
        }
    }
}

/** Something worth knowing that happened on the connection. */
sealed class HaEvent {
    /** The token was accepted; subscriptions have been sent. */
    data object Authenticated : HaEvent()

    /** Home Assistant refused the token. Retrying won't help until the account is fixed. */
    data class Rejected(val reason: String) : HaEvent()

    /** Home Assistant refused to watch [trigger] (e.g. it doesn't exist). */
    data class SubscriptionFailed(val trigger: String, val reason: String) : HaEvent()

    /** [entityId] changed to [state] (`on`, `off`, `unavailable`...). */
    data class Triggered(val entityId: String, val state: String) : HaEvent()
}
