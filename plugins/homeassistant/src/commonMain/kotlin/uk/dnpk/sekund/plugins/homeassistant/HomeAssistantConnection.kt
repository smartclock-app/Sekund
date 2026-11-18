package uk.dnpk.sekund.plugins.homeassistant

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import uk.dnpk.sekund.sdk.context.Alert
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.settings.AccountRef
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps a WebSocket open to Home Assistant and reports sensor changes to [onSensor]. Reconnects by
 * itself (5 s, 10 s, 20 s... up to 5 minutes between tries) and shows an alert while something is wrong.
 *
 * Call [run] in the instance's scope; it runs until the instance stops.
 */
class HomeAssistantConnection(
    private val context: InstanceContext<HomeAssistantSettings>,
    private val account: AccountRef,
    private val onSensor: suspend (trigger: String, state: String) -> Unit,
) {
    // A copy of the app's client that can also open WebSockets. Pings every 30 s notice a dead connection.
    private val client: HttpClient = context.http.config {
        install(WebSockets) {
            pingIntervalMillis = 30_000
        }
    }

    /** Set when Home Assistant refused the token: the next try waits longer. */
    private var rejected = false

    suspend fun run() {
        var failures = 0
        while (true) {
            rejected = false
            try {
                connectOnce()
                // The server closed a working connection (e.g. Home Assistant restarted): try again soon.
                failures = 0
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failures += 1
                context.log.warn("Home Assistant connection failed (try $failures)", error)
                // One failed try is often just a restart; only bother the user if it keeps failing.
                if (failures >= 3) {
                    context.alerts.show(Alert(title = "Can't reach Home Assistant", subtitle = error.message))
                }
            }
            delay(waitBeforeRetry(failures))
        }
    }

    private fun waitBeforeRetry(failures: Int): Duration {
        if (rejected) return 5.minutes
        return HomeAssistantConnection.retryDelay(failures)
    }

    private suspend fun connectOnce() {
        val server = context.auth.fields(account)["instance_url"]?.trim()?.trimEnd('/')
        if (server.isNullOrEmpty()) {
            context.alerts.show(Alert(title = "Home Assistant", subtitle = "The account has no server address. Reconnect it under Accounts."))
            rejected = true // nothing will change until the account is fixed
            return
        }
        val url = webSocketUrl(server)
        val token = context.auth.connectionToken(account, url)
        val session = HaSession(token, context.settings.cameras.map { camera -> camera.trigger })

        client.webSocket(url) {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val step = session.onMessage(frame.readText())
                for (reply in step.replies) send(Frame.Text(reply))
                val event = step.event ?: continue
                when (event) {
                    is HaEvent.Authenticated -> {
                        context.log.info("Connected to Home Assistant")
                        context.alerts.clear()
                    }
                    is HaEvent.Rejected -> {
                        rejected = true
                        context.alerts.show(Alert(title = "Home Assistant refused the access token", subtitle = event.reason))
                        return@webSocket
                    }
                    is HaEvent.SubscriptionFailed -> {
                        context.alerts.show(Alert(title = "Home Assistant can't watch ${event.trigger}", subtitle = event.reason))
                    }
                    is HaEvent.Triggered -> onSensor(event.entityId, event.state)
                }
            }
        }
    }

    companion object {
        /** `http://ha.local:8123` → `ws://ha.local:8123/api/websocket` (https → wss). */
        fun webSocketUrl(server: String): String {
            val base = when {
                server.startsWith("https://") -> "wss://" + server.removePrefix("https://")
                server.startsWith("http://") -> "ws://" + server.removePrefix("http://")
                else -> "ws://$server"
            }
            return "$base/api/websocket"
        }

        /** 5 s, 10 s, 20 s... capped at 5 minutes. After a clean disconnect (0 failures) 5 s. */
        fun retryDelay(failures: Int): Duration {
            var wait = 5.seconds
            repeat(minOf(maxOf(failures - 1, 0), 6)) { wait *= 2 }
            return minOf(wait, 5.minutes)
        }
    }
}
