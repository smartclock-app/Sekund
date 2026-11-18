package uk.dnpk.sekund.runtime.relay

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.runtime.auth.randomUrlSafe
import uk.dnpk.sekund.sdk.context.WebhookCall
import uk.dnpk.sekund.sdk.context.Webhooks
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds

/**
 * Webhooks through the Sekund server, for the whole app. Each instance sees only its own hooks through
 * [forInstance]; a hook's key on the server is `"<instance id>/<name>"`.
 *
 * Hooks are registered with the server when an instance first asks for its URL, and again after every
 * reconnect (the server gives the same URL each time). Without a server, URLs are null and calls never
 * arrive, so plugins carry on polling.
 */
class RelayWebhooks : RelayFeature {
    private val log = Logger.withTag("Webhooks")
    private val lock = Mutex()

    /** The URL of every hook an instance asked for, by key (null until the server answers). */
    private val urls = mutableMapOf<String, MutableStateFlow<String?>>()

    /** The open link while connected to a server that relays webhooks. */
    @Volatile
    private var link: RelayLink? = null

    /** Ids of recent calls, to ignore the repeats that at-least-once delivery can bring. */
    private val recentIds = ArrayDeque<String>()

    private val calls = MutableSharedFlow<RelayMessage.WebhookEvent>(extraBufferCapacity = 64)

    /** The [Webhooks] handed to one instance. */
    fun forInstance(instanceId: String): Webhooks = InstanceWebhooks(instanceId)

    override suspend fun connected(link: RelayLink, welcome: RelayMessage.Welcome) {
        if (!welcome.webhooks) return
        val keys = lock.withLock {
            this.link = link
            urls.keys.toList()
        }
        for (key in keys) register(link, key)
    }

    override fun disconnected() {
        // URLs stay as they are: the server keeps accepting calls while the device is away, and passes
        // them on when it's back.
        link = null
    }

    override suspend fun handle(link: RelayLink, message: RelayMessage): Boolean {
        if (message !is RelayMessage.WebhookEvent) return false
        val isRepeat = lock.withLock {
            if (message.id in recentIds) {
                true
            } else {
                recentIds.addLast(message.id)
                if (recentIds.size > 200) recentIds.removeFirst()
                false
            }
        }
        if (!isRepeat) calls.emit(message)
        link.send(RelayMessage.Ack(message.id))
        return true
    }

    /** The URL flow for [key], registering it with the server the first time it's asked for. */
    private suspend fun urlFor(key: String): MutableStateFlow<String?> {
        val created = MutableStateFlow<String?>(null)
        val currentLink: RelayLink?
        lock.withLock {
            val existing = urls[key]
            if (existing != null) return existing
            urls[key] = created
            currentLink = link
        }
        if (currentLink != null) register(currentLink, key)
        return created
    }

    private suspend fun register(link: RelayLink, key: String) {
        val id = randomUrlSafe(9)
        val answer = try {
            link.request(id, RelayMessage.WebhookRegister(id = id, key = key), 30.seconds)
        } catch (error: Exception) {
            log.w { "Couldn't register webhook $key: ${error.message}" }
            return
        }
        if (answer is RelayMessage.WebhookRegistered) {
            val flow = lock.withLock { urls[key] } ?: return
            flow.value = answer.url
        } else if (answer is RelayMessage.Error) {
            log.w { "The server wouldn't register webhook $key: ${answer.message}" }
        }
    }

    private inner class InstanceWebhooks(private val instanceId: String) : Webhooks {
        override fun url(name: String): Flow<String?> = flow {
            // Looked up (and registered) when someone starts collecting, not when url() is called.
            urlFor("$instanceId/$name").collect { url -> emit(url) }
        }

        override fun calls(name: String): Flow<WebhookCall> {
            val key = "$instanceId/$name"
            return calls
                .filter { event -> event.key == key }
                .map { event -> WebhookCall(event.headers, event.body) }
        }
    }
}
