package uk.dnpk.sekund.sdk.testing

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonElement
import uk.dnpk.sekund.sdk.auth.AuthAccess
import uk.dnpk.sekund.sdk.auth.AuthState
import uk.dnpk.sekund.sdk.context.Alert
import uk.dnpk.sekund.sdk.context.Alerts
import uk.dnpk.sekund.sdk.context.EventBus
import uk.dnpk.sekund.sdk.context.FileCache
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.context.InstanceLookup
import uk.dnpk.sekund.sdk.context.KeyValueStore
import uk.dnpk.sekund.sdk.context.LinkedInstance
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.context.Overlay
import uk.dnpk.sekund.sdk.context.Overlays
import uk.dnpk.sekund.sdk.context.PluginLog
import uk.dnpk.sekund.sdk.context.RefreshPolicy
import uk.dnpk.sekund.sdk.context.Refresher
import uk.dnpk.sekund.sdk.context.SekundEvent
import uk.dnpk.sekund.sdk.context.TimePreferences
import uk.dnpk.sekund.sdk.context.TimeService
import uk.dnpk.sekund.sdk.context.now
import uk.dnpk.sekund.sdk.plugin.ExtensionPoint
import uk.dnpk.sekund.sdk.settings.AccountRef

/**
 * An [InstanceContext] for tests, built from the fakes in this file. Everything is in memory and
 * nothing happens on its own: the test decides when data refreshes ([ManualRefresher.runAll]) and
 * what the time is ([FakeTime]).
 *
 * ```kotlin
 * val api = FakeApi { request -> if (request.url.encodedPath == "/items") json("[]") else null }
 * val context = FakeInstanceContext(MySettings(...), backgroundScope, api = api)
 * val source = MySource(context)
 * context.refresher.runAll()
 * assertEquals(..., source.events.value)
 * ```
 *
 * @param api answers every request, through both [http] and the signed-in clients from [auth].
 */
class FakeInstanceContext<S>(
    override val settings: S,
    override val scope: CoroutineScope,
    val api: FakeApi = FakeApi { null },
    override val time: FakeTime = FakeTime(),
    override val instanceId: String = "test",
) : InstanceContext<S> {
    override val http: HttpClient = api.client
    override val auth: FakeAuth = FakeAuth(api.client)
    override val refresher: ManualRefresher = ManualRefresher(time)
    override val storage: MemoryStore = MemoryStore()
    override val files: MemoryFileCache = MemoryFileCache()
    override val alerts: RecordingAlerts = RecordingAlerts()
    override val overlays: RecordingOverlays = RecordingOverlays()
    override val events: FakeEventBus = FakeEventBus()
    override val log: RecordingLog = RecordingLog()
    override val instances: FakeInstances = FakeInstances()
}

/**
 * A pretend web API. [handler] gets each request and returns the response, or null for "404 Not Found".
 * Every request is remembered in [requests].
 */
class FakeApi(private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?) {
    val requests = mutableListOf<HttpRequestData>()

    val client: HttpClient = HttpClient(
        MockEngine { request ->
            requests.add(request)
            handler(request) ?: respondError(HttpStatusCode.NotFound)
        },
    )

    /** The paths requested so far, e.g. "/users/me/lists/tv/items". */
    val paths: List<String>
        get() = requests.map { request -> request.url.encodedPath }
}

/** A JSON answer, for use inside a [FakeApi] handler. */
fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

/** A clock the test sets. Starts at Wednesday 18 June 2025 10:00, in UTC. */
class FakeTime(
    start: LocalDateTime = LocalDateTime(2025, 6, 18, 10, 0),
    zone: TimeZone = TimeZone.UTC,
    preferences: TimePreferences = TimePreferences(use24Hour = true),
) : TimeService {
    override val everySecond = MutableStateFlow(start)
    override val everyMinute = MutableStateFlow(start)
    override val timeZone = MutableStateFlow(zone)
    override val preferences = MutableStateFlow(preferences)

    /** Moves the clock to [time]. */
    fun set(time: LocalDateTime) {
        everySecond.value = time
        everyMinute.value = time
    }
}

/**
 * A [Refresher] that never runs anything by itself. Call [runAll] to run every poll once, like one
 * scheduled refresh. Failures behave like the real one: earlier data is kept and marked stale.
 */
class ManualRefresher(private val time: FakeTime) : Refresher {
    private class Poll<T>(val fetch: suspend () -> T, val state: MutableStateFlow<Loadable<T>>)

    private val polls = mutableListOf<Poll<*>>()

    /** The policies passed to [poll], so tests can check intervals. */
    val policies = mutableListOf<RefreshPolicy>()

    /** How many times [refreshNow] was called. */
    var refreshNowCalls = 0
        private set

    override fun <T> poll(policy: RefreshPolicy, fetch: suspend () -> T): StateFlow<Loadable<T>> {
        val state = MutableStateFlow<Loadable<T>>(Loadable.Loading)
        polls.add(Poll(fetch, state))
        policies.add(policy)
        return state
    }

    override fun refreshNow() {
        refreshNowCalls++
    }

    /** Runs every poll once, in the order they were started. */
    suspend fun runAll() {
        for (poll in polls) run(poll)
    }

    private suspend fun <T> run(poll: Poll<T>) {
        try {
            poll.state.value = Loadable.Ready(poll.fetch(), updatedAt = time.now())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val message = error.message ?: error::class.simpleName ?: "Failed"
            val previous = poll.state.value
            if (previous is Loadable.Ready) {
                poll.state.value = previous.copy(refreshError = message)
            } else {
                poll.state.value = Loadable.Failed(message)
            }
        }
    }
}

/** Hands out [client] for every account, without signing anything. Remembers which accounts were asked for. */
class FakeAuth(private val client: HttpClient) : AuthAccess {
    val requestedAccounts = mutableListOf<AccountRef>()
    val states = MutableStateFlow<AuthState>(AuthState.Valid)

    /** What [fields] returns for every account, e.g. `mapOf("domain" to "amazon.co.uk")`. */
    val accountFields = mutableMapOf<String, String>()

    override fun fields(account: AccountRef): Map<String, String> = accountFields

    /** What [connectionToken] returns. Set it to null to make it fail like a provider that doesn't allow it. */
    var token: String? = "test-token"

    /** The URLs [connectionToken] was asked for, in order. */
    val tokenUrls = mutableListOf<String>()

    override suspend fun connectionToken(account: AccountRef, url: String): String {
        tokenUrls.add(url)
        return token ?: throw IllegalStateException("No connection token in this test")
    }

    override fun state(account: AccountRef): Flow<AuthState> = states

    override fun client(account: AccountRef): HttpClient {
        requestedAccounts.add(account)
        return client
    }
}

/** A [KeyValueStore] in memory. [values] can be filled in before the test to pretend there's a cache. */
class MemoryStore : KeyValueStore {
    val values = mutableMapOf<String, JsonElement>()

    override suspend fun getJson(key: String): JsonElement? = values[key]

    override suspend fun putJson(key: String, value: JsonElement) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}

/** A [FileCache] in memory. [files] can be filled in before the test. */
class MemoryFileCache : FileCache {
    val files = mutableMapOf<String, ByteArray>()

    override suspend fun read(name: String): ByteArray? = files[name]

    override suspend fun write(name: String, bytes: ByteArray) {
        files[name] = bytes
    }

    override suspend fun names(): List<String> = files.keys.toList()

    override suspend fun remove(name: String) {
        files.remove(name)
    }
}

/** Remembers the alert currently shown (null = none). */
class RecordingAlerts : Alerts {
    var current: Alert? = null
        private set

    override fun show(alert: Alert) {
        current = alert
    }

    override fun clear() {
        current = null
    }
}

/** Remembers the overlay currently shown (null = none). [tapAway] pretends the user tapped it away. */
class RecordingOverlays : Overlays {
    var current: Overlay? = null
        private set

    override fun show(overlay: Overlay) {
        current = overlay
    }

    override fun clear() {
        current = null
    }

    fun tapAway() {
        val overlay = current ?: return
        current = null
        overlay.onDismissed()
    }
}

/** Remembers everything published, and lets the test send events in through [all]. */
class FakeEventBus : EventBus {
    val published = mutableListOf<SekundEvent>()
    override val all = MutableSharedFlow<SekundEvent>(extraBufferCapacity = 64)

    override suspend fun publish(event: SekundEvent) {
        published.add(event)
        all.emit(event)
    }
}

/** Keeps log lines, prefixed with their level ("WARN Couldn't ..."), so tests can check them. */
class RecordingLog : PluginLog {
    val lines = mutableListOf<String>()

    override fun debug(message: String) {
        lines.add("DEBUG $message")
    }

    override fun info(message: String) {
        lines.add("INFO $message")
    }

    override fun warn(message: String, error: Throwable?) {
        lines.add("WARN $message")
    }

    override fun error(message: String, error: Throwable?) {
        lines.add("ERROR $message")
    }
}

/** Other instances, which the test puts in with [set]. Unknown ids are reported as missing. */
class FakeInstances : InstanceLookup {
    private val slots = mutableMapOf<String, MutableStateFlow<LinkedInstance<Any>>>()

    private fun slot(id: String): MutableStateFlow<LinkedInstance<Any>> =
        slots.getOrPut(id) { MutableStateFlow(LinkedInstance.Unavailable("No instance called '$id'")) }

    /** Makes instance [id] running with [value], or missing if [value] is null. */
    fun set(id: String, value: Any?) {
        slot(id).value = if (value == null) LinkedInstance.Unavailable("No instance called '$id'") else LinkedInstance.Running(value)
    }

    override fun <T : Any> watch(point: ExtensionPoint<T>, instanceId: String): StateFlow<LinkedInstance<T>> {
        // The test is responsible for putting values of the right type in.
        @Suppress("UNCHECKED_CAST")
        return slot(instanceId) as StateFlow<LinkedInstance<T>>
    }
}

