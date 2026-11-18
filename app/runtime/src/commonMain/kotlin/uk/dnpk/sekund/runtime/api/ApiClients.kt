package uk.dnpk.sekund.runtime.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import uk.dnpk.sekund.protocol.SekundPrettyJson
import uk.dnpk.sekund.protocol.api.PairedClient
import uk.dnpk.sekund.runtime.auth.randomUrlSafe
import uk.dnpk.sekund.runtime.auth.urlSafeBase64
import uk.dnpk.sekund.runtime.platform.sha256
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The paired companions and their access tokens, kept in `<data>/api-clients.json`.
 *
 * Only a SHA-256 hash of each token is stored, so the file is safe to read or back up: nobody can get a
 * working token from it. (Like a password hash, except tokens are long and random so a plain hash is enough.)
 */
class ApiClients(
    private val file: Path,
    private val clock: Clock = Clock.System,
    private val fileSystem: FileSystem = SystemFileSystem,
) {
    @Serializable
    private data class Stored(
        val id: String,
        val name: String,
        val tokenHash: String,
        val pairedAt: String,
        val lastSeenAt: String? = null,
    )

    private val serializer = ListSerializer(Stored.serializer())
    private val lock = Mutex()
    private val current = MutableStateFlow<List<Stored>>(emptyList())
    private val publicList = MutableStateFlow<List<PairedClient>>(emptyList())

    /** Every paired companion, for the device's own screens and `GET /v1/clients`. */
    val clients: StateFlow<List<PairedClient>> = publicList

    suspend fun load() {
        lock.withLock { setAll(read()) }
    }

    /**
     * Adds a client and returns its new token. The token is returned only here; afterwards Sekund only
     * knows its hash.
     */
    suspend fun add(name: String): Pair<PairedClient, String> {
        val token = randomUrlSafe(32)
        val stored = Stored(id = "cli_" + randomUrlSafe(6), name = name, tokenHash = hash(token), pairedAt = clock.now().toString())
        lock.withLock { save(current.value + stored) }
        return Pair(toPublic(stored), token)
    }

    /**
     * The client with this [token], or null if it isn't valid. Also notes when the client was last seen
     * (at most once a minute, to avoid writing the file on every request).
     */
    suspend fun authenticate(token: String): PairedClient? {
        val tokenHash = hash(token)
        lock.withLock {
            val found = current.value.firstOrNull { client -> client.tokenHash == tokenHash } ?: return null
            val now = clock.now()
            val lastSeen = found.lastSeenAt?.let { text -> Instant.parse(text) }
            if (lastSeen == null || now - lastSeen > 1.minutes) {
                val updated = found.copy(lastSeenAt = now.toString())
                save(current.value.map { client -> if (client.id == found.id) updated else client })
                return toPublic(updated)
            }
            return toPublic(found)
        }
    }

    /** Removes a client: its token stops working immediately. Returns false if there's no such client. */
    suspend fun revoke(id: String): Boolean {
        lock.withLock {
            val remaining = current.value.filter { client -> client.id != id }
            if (remaining.size == current.value.size) return false
            save(remaining)
            return true
        }
    }

    private fun hash(token: String): String = urlSafeBase64.encode(sha256(token.encodeToByteArray()))

    private fun toPublic(stored: Stored) = PairedClient(stored.id, stored.name, stored.pairedAt, stored.lastSeenAt)

    private fun setAll(clients: List<Stored>) {
        current.value = clients
        publicList.value = clients.map { client -> toPublic(client) }
    }

    /** Must be called with [lock] held. */
    private fun save(clients: List<Stored>) {
        val folder = file.parent ?: error("$file has no parent folder")
        fileSystem.createDirectories(folder)
        val temporary = Path(folder, file.name + ".tmp")
        fileSystem.sink(temporary).buffered().use { sink -> sink.writeString(SekundPrettyJson.encodeToString(serializer, clients)) }
        fileSystem.atomicMove(temporary, file)
        setAll(clients)
    }

    private fun read(): List<Stored> {
        if (!fileSystem.exists(file)) return emptyList()
        val text = fileSystem.source(file).buffered().use { source -> source.readString() }
        try {
            return SekundPrettyJson.decodeFromString(serializer, text)
        } catch (error: SerializationException) {
            // Refuse to start the API with a damaged list rather than silently un-pairing everyone.
            throw IllegalStateException("$file is not a valid clients file: ${error.message}", error)
        }
    }
}
