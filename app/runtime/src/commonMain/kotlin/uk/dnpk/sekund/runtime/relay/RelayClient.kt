package uk.dnpk.sekund.runtime.relay

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import uk.dnpk.sekund.protocol.API_VERSION
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.api.DeviceEvent
import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.runtime.auth.secrets.SecretStore
import uk.dnpk.sekund.runtime.services.AlertCenter
import uk.dnpk.sekund.sdk.context.Alert
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The device's link to the optional Sekund server (docs/api/relay-protocol.md): one WebSocket that the
 * device opens itself, so it works behind any home router.
 *
 * Over it the device:
 * - gets claimed (shows a code until someone enters it in the companion), then authenticates with the
 *   device token it was given, kept in the [SecretStore],
 * - answers device API requests from the companion that came through the server ([rpc]),
 * - sends its events while a companion watches them through the server,
 * - and lets [features] (webhooks, signing in through the server) use the link.
 *
 * It reconnects by itself, waiting longer after each failure (up to 5 minutes, with some randomness so
 * many devices don't all reconnect at the same moment after a server restart).
 *
 * @param events the device's events stream (the same as `WS /v1/events`).
 */
class RelayClient(
    private val deviceId: () -> String,
    private val deviceName: () -> String,
    private val appVersion: String,
    private val secrets: SecretStore,
    private val rpc: RpcHandler,
    private val events: () -> Flow<DeviceEvent>,
    private val alerts: AlertCenter,
    private val scope: CoroutineScope,
    private val features: List<RelayFeature> = emptyList(),
    private val http: HttpClient = defaultHttpClient(),
    private val random: Random = Random.Default,
) {
    private val log = Logger.withTag("Relay")
    private val currentStatus = MutableStateFlow<RelayStatus>(RelayStatus.Off)

    /** Where the link has got to, for settings. */
    val status: StateFlow<RelayStatus> = currentStatus

    /**
     * Keeps a link open to whichever server [serverUrl] says (null = none), reconnecting whenever it
     * drops and switching when the address changes.
     */
    fun follow(serverUrl: Flow<String?>): Job = scope.launch {
        serverUrl.distinctUntilChanged().collectLatest { url ->
            if (url.isNullOrBlank()) {
                currentStatus.value = RelayStatus.Off
                alerts.clear(ALERT_OWNER)
            } else {
                keepConnected(url.trim().trimEnd('/'))
            }
        }
    }

    /** Connects to [serverUrl], and again after every disconnection, until cancelled. */
    private suspend fun keepConnected(serverUrl: String) {
        val socketUrl = socketUrlOf(serverUrl)
        if (socketUrl == null) {
            currentStatus.value = RelayStatus.Refused("The server address must start with https:// or http://", retryIn = Duration.INFINITE)
            return
        }
        var failures = 0
        while (true) {
            currentStatus.value = RelayStatus.Connecting(serverUrl)
            val outcome = connectOnce(socketUrl)
            features.forEach { feature -> feature.disconnected() }

            val wait: Duration
            val refusedBecause = outcome.refusedBecause
            if (refusedBecause != null) {
                // e.g. someone else owns this device: nothing changes quickly, so don't keep asking.
                wait = 10.minutes
                currentStatus.value = RelayStatus.Refused(refusedBecause, wait)
                alerts.show(ALERT_OWNER, Alert(title = "The Sekund server refused this device", subtitle = refusedBecause))
            } else {
                failures = if (outcome.wasWelcomed) 1 else failures + 1
                wait = backoff(failures)
                currentStatus.value = RelayStatus.Offline(outcome.error ?: "Disconnected", wait)
            }
            delay(wait)
        }
    }

    /** What happened on one connection. */
    private class Outcome(var wasWelcomed: Boolean = false, var refusedBecause: String? = null, var error: String? = null)

    /** Opens one connection and runs it until it closes. */
    private suspend fun connectOnce(socketUrl: String): Outcome {
        val outcome = Outcome()
        try {
            http.webSocket(socketUrl) { runSession(this, outcome) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            outcome.error = error.message ?: error::class.simpleName
            log.d { "Relay connection failed: ${outcome.error}" }
        }
        return outcome
    }

    private suspend fun runSession(session: DefaultClientWebSocketSession, outcome: Outcome) {
        val link = SessionLink(session)
        var eventsJob: Job? = null
        try {
            val token = secrets.read(TOKEN_KEY)
            link.send(RelayMessage.Hello(deviceId(), token, deviceName(), appVersion, API_VERSION))

            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                val message = decode(frame.readText()) ?: continue // a type from a newer server: ignore
                when (message) {
                    is RelayMessage.ClaimCode -> waitingToBeClaimed(message)
                    is RelayMessage.Claimed -> {
                        secrets.write(TOKEN_KEY, message.deviceToken)
                        log.i { "This device was claimed on the Sekund server" }
                    }
                    is RelayMessage.Welcome -> {
                        outcome.wasWelcomed = true
                        alerts.clear(ALERT_OWNER)
                        currentStatus.value = RelayStatus.Connected(message.owner, message.oauthProviders, message.webhooks)
                        log.i { "Connected to the Sekund server as ${message.owner}'s device" }
                        for (feature in features) {
                            session.launch { feature.connected(link, message) }
                        }
                    }
                    is RelayMessage.Rejected -> {
                        if (message.forgetToken) secrets.delete(TOKEN_KEY)
                        // Forgetting the token means "connect again to be claimed again", which isn't a refusal.
                        if (!message.forgetToken) outcome.refusedBecause = message.reason
                        log.w { "The Sekund server refused this device: ${message.reason}" }
                    }
                    is RelayMessage.RpcRequest -> session.launch { link.send(answer(message)) }
                    is RelayMessage.EventsStart -> {
                        eventsJob?.cancel()
                        eventsJob = session.launch {
                            events().collect { event -> link.send(RelayMessage.Event(event)) }
                        }
                    }
                    is RelayMessage.EventsStop -> {
                        eventsJob?.cancel()
                        eventsJob = null
                    }
                    else -> handleOther(link, message)
                }
            }
        } finally {
            eventsJob?.cancel()
            withContext(NonCancellable) { link.closed() }
        }
    }

    /** Replies go to whoever is waiting for them; anything else to the first feature that wants it. */
    private suspend fun handleOther(link: SessionLink, message: RelayMessage) {
        val replyTo = replyToOf(message)
        if (replyTo != null) {
            link.replyArrived(replyTo, message)
            return
        }
        for (feature in features) {
            if (feature.handle(link, message)) return
        }
    }

    private suspend fun answer(request: RelayMessage.RpcRequest): RelayMessage.RpcResponse {
        try {
            return rpc.handle(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.w(error) { "Couldn't answer ${request.method} ${request.path} from the server" }
            return RelayMessage.RpcResponse(request.id, 500, body = """{"error":{"code":"internal","message":"The device couldn't handle the request"}}""")
        }
    }

    private fun waitingToBeClaimed(message: RelayMessage.ClaimCode) {
        val where = if (message.serverUrl.isEmpty()) "the companion app" else "the companion app, signed in to ${message.serverUrl}"
        alerts.show(ALERT_OWNER, Alert(title = "Claim this Sekund: ${message.code}", subtitle = "Enter the code in $where."))
        // Also logged, for screens nobody is looking at (e.g. setting up a Raspberry Pi over SSH).
        log.i { "Waiting to be claimed: enter ${message.code} in $where" }
        currentStatus.value = RelayStatus.WaitingToBeClaimed(message.code, message.serverUrl, parseInstant(message.expiresAt))
    }

    /** Between 80% and 120% of 2^failures seconds, at most 5 minutes. */
    private fun backoff(failures: Int): Duration {
        val base = (1L shl failures.coerceIn(0, 9)) * 1_000L
        val capped = minOf(base, 5.minutes.inWholeMilliseconds)
        val jitter = 0.8 + random.nextDouble() * 0.4
        return (capped * jitter).toLong().milliseconds
    }

    /** One open connection: sends messages and matches the server's replies to requests. */
    private class SessionLink(private val session: DefaultClientWebSocketSession) : RelayLink {
        private val lock = Mutex()
        private val waiting = mutableMapOf<String, CompletableDeferred<RelayMessage>>()

        override suspend fun send(message: RelayMessage) {
            session.send(Frame.Text(SekundJson.encodeToString(RelayMessage.serializer(), message)))
        }

        override suspend fun request(id: String, message: RelayMessage, timeout: Duration): RelayMessage? {
            val reply = CompletableDeferred<RelayMessage>()
            lock.withLock { waiting[id] = reply }
            try {
                send(message)
                return withTimeoutOrNull(timeout) { reply.await() }
            } finally {
                withContext(NonCancellable) { lock.withLock { waiting.remove(id) } }
            }
        }

        suspend fun replyArrived(replyTo: String, message: RelayMessage) {
            val found = lock.withLock { waiting[replyTo] }
            found?.complete(message)
        }

        /** The connection closed: nobody will get an answer now. */
        suspend fun closed() {
            val all = lock.withLock { waiting.values.toList() }
            for (reply in all) reply.cancel()
        }
    }

    companion object {
        /** Where the device token is kept in the secret store. */
        const val TOKEN_KEY = "relay.deviceToken"

        /** The alert shown while waiting to be claimed (or refused). */
        const val ALERT_OWNER = "relay"

        /** `https://x` → `wss://x/v1/device/connect` (and http → ws). Null for anything else. */
        fun socketUrlOf(serverUrl: String): String? {
            if (serverUrl.startsWith("https://")) return "wss://" + serverUrl.removePrefix("https://") + "/v1/device/connect"
            if (serverUrl.startsWith("http://")) return "ws://" + serverUrl.removePrefix("http://") + "/v1/device/connect"
            return null
        }

        /** The `replyTo` of messages that answer a request, else null. */
        fun replyToOf(message: RelayMessage): String? = when (message) {
            is RelayMessage.WebhookRegistered -> message.replyTo
            is RelayMessage.OAuthStarted -> message.replyTo
            is RelayMessage.TokenRefreshed -> message.replyTo
            is RelayMessage.Ack -> message.replyTo
            is RelayMessage.Error -> message.replyTo
            else -> null
        }

        private fun decode(text: String): RelayMessage? {
            try {
                return SekundJson.decodeFromString(RelayMessage.serializer(), text)
            } catch (_: SerializationException) {
                return null
            } catch (_: IllegalArgumentException) {
                return null
            }
        }

        private fun parseInstant(text: String): Instant {
            try {
                return Instant.parse(text)
            } catch (_: IllegalArgumentException) {
                return Instant.DISTANT_FUTURE
            }
        }

        /** A WebSocket-capable client; Ktor's pings notice a dead connection within about a minute. */
        private fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            install(WebSockets) { pingIntervalMillis = 30.seconds.inWholeMilliseconds }
        }
    }
}
