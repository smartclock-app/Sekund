package uk.dnpk.sekund.runtime.services

import co.touchlab.kermit.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import uk.dnpk.sekund.protocol.SekundPrettyJson
import uk.dnpk.sekund.sdk.context.KeyValueStore

/**
 * A [KeyValueStore] kept in one human-readable JSON file per instance (`<data>/storage/<instanceId>.json`),
 * so you can open it to see what a plugin has cached.
 *
 * The whole file is read on first use and rewritten on every change. That's simple and fine for the
 * small amounts of data widgets cache.
 */
class JsonFileStore(
    private val file: Path,
    private val fileSystem: FileSystem = SystemFileSystem,
) : KeyValueStore {
    private val log = Logger.withTag("Storage")
    private val lock = Mutex()
    private var values: MutableMap<String, JsonElement>? = null

    override suspend fun getJson(key: String): JsonElement? = lock.withLock {
        loaded()[key]
    }

    override suspend fun putJson(key: String, value: JsonElement) = lock.withLock {
        val all = loaded()
        all[key] = value
        save(all)
    }

    override suspend fun remove(key: String) = lock.withLock {
        val all = loaded()
        if (all.remove(key) != null) {
            save(all)
        }
    }

    /** The file's contents, read the first time they're needed. */
    private fun loaded(): MutableMap<String, JsonElement> {
        val alreadyLoaded = values
        if (alreadyLoaded != null) return alreadyLoaded

        val fresh = readFile()
        values = fresh
        return fresh
    }

    private fun readFile(): MutableMap<String, JsonElement> {
        if (!fileSystem.exists(file)) return mutableMapOf()
        try {
            val text = fileSystem.source(file).buffered().use { source -> source.readString() }
            val json = SekundPrettyJson.decodeFromString(JsonObject.serializer(), text)
            return json.toMutableMap()
        } catch (error: SerializationException) {
            // It's only a cache, so start empty rather than fail.
            log.w(error) { "Ignoring unreadable storage file $file" }
            return mutableMapOf()
        }
    }

    private fun save(all: Map<String, JsonElement>) {
        val folder = file.parent ?: error("$file has no parent folder")
        fileSystem.createDirectories(folder)
        val temporary = Path(folder, file.name + ".tmp")
        val text = SekundPrettyJson.encodeToString(JsonObject.serializer(), JsonObject(all))
        fileSystem.sink(temporary).buffered().use { sink -> sink.writeString(text) }
        fileSystem.atomicMove(temporary, file)
    }
}
