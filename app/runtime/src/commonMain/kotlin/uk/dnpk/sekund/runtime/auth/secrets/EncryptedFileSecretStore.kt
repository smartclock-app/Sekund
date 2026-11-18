package uk.dnpk.sekund.runtime.auth.secrets

import co.touchlab.kermit.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.runtime.platform.restrictToOwner

/**
 * A [SecretStore] kept in one encrypted file (`secrets.bin`).
 *
 * The file holds a JSON object of key -> value, encrypted as a whole with [cipher]. It's read on first
 * use and rewritten (atomically, via a temporary file) on every change. Fine for the handful of
 * accounts a display has.
 *
 * If the file can't be decrypted (e.g. the key was lost), it's renamed to `secrets.bin.unreadable` and
 * the store starts empty: accounts then ask to be reconnected, rather than the app failing to start.
 */
class EncryptedFileSecretStore(
    private val file: Path,
    private val cipher: SecretCipher,
    private val fileSystem: FileSystem = SystemFileSystem,
) : SecretStore {
    private val log = Logger.withTag("Secrets")
    private val lock = Mutex()
    private var values: MutableMap<String, String>? = null

    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    override suspend fun read(key: String): String? = lock.withLock {
        loaded()[key]
    }

    override suspend fun write(key: String, value: String) = lock.withLock {
        val all = loaded()
        all[key] = value
        save(all)
    }

    override suspend fun delete(key: String) = lock.withLock {
        val all = loaded()
        if (all.remove(key) != null) {
            save(all)
        }
    }

    private fun loaded(): MutableMap<String, String> {
        val alreadyLoaded = values
        if (alreadyLoaded != null) return alreadyLoaded

        val fresh = readFile()
        values = fresh
        return fresh
    }

    private fun readFile(): MutableMap<String, String> {
        if (!fileSystem.exists(file)) return mutableMapOf()
        try {
            val encrypted = fileSystem.source(file).buffered().use { source -> source.readByteArray() }
            val text = cipher.decrypt(encrypted).decodeToString()
            return SekundJson.decodeFromString(mapSerializer, text).toMutableMap()
        } catch (error: Exception) {
            log.e(error) { "Can't read $file. Moving it aside; accounts will need reconnecting." }
            fileSystem.atomicMove(file, Path(file.toString() + ".unreadable"))
            return mutableMapOf()
        }
    }

    private fun save(all: Map<String, String>) {
        val folder = file.parent ?: error("$file has no parent folder")
        fileSystem.createDirectories(folder)
        val encrypted = cipher.encrypt(SekundJson.encodeToString(mapSerializer, all).encodeToByteArray())

        val temporary = Path(folder, file.name + ".tmp")
        fileSystem.sink(temporary).buffered().use { sink -> sink.write(encrypted) }
        restrictToOwner(temporary)
        fileSystem.atomicMove(temporary, file)
    }
}
