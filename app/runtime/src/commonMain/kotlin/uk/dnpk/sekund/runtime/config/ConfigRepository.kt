package uk.dnpk.sekund.runtime.config

import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.protocol.config.Orientation
import co.touchlab.kermit.Logger
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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import uk.dnpk.sekund.protocol.SekundPrettyJson
import uk.dnpk.sekund.runtime.AppPaths
import uk.dnpk.sekund.runtime.auth.urlSafeBase64
import uk.dnpk.sekund.runtime.platform.sha256
import kotlin.time.Clock

/**
 * Loads, holds and saves config.json.
 *
 * - [config] is the current config as a `StateFlow` (think: a zustand store). The UI and the instance
 *   manager watch it, so a change applies immediately without restarting anything.
 * - [update] is the only way to change it. It saves the file straight away.
 * - If the file can't be read, Sekund runs on defaults but **never overwrites the broken file**, so
 *   nobody loses their config to a typo. The problem is reported in [loadProblem].
 */
class ConfigRepository(
    private val paths: AppPaths,
    private val fileSystem: FileSystem = SystemFileSystem,
    private val clock: Clock = Clock.System,
) {
    private val log = Logger.withTag("Config")
    private val writeLock = Mutex()

    private val currentConfig = MutableStateFlow(AppConfig())
    private val currentLoadProblem = MutableStateFlow<String?>(null)

    /** The current config. */
    val config: StateFlow<AppConfig> = currentConfig

    /** Why config.json couldn't be read, or null if it loaded fine. Shown in the UI. */
    val loadProblem: StateFlow<String?> = currentLoadProblem

    /** Saving is blocked while the file on disk couldn't be read, to protect it. */
    private var fileIsBroken = false

    /**
     * Reads config.json. If it doesn't exist yet, writes [createDefault]'s config to it.
     * Call once at startup.
     */
    suspend fun load(createDefault: () -> AppConfig) {
        writeLock.withLock {
            if (!fileSystem.exists(paths.configFile)) {
                log.i { "No config.json yet, creating one at ${paths.configFile}" }
                val defaults = createDefault()
                writeFile(defaults)
                currentConfig.value = defaults
                return
            }

            val text = readFile(paths.configFile)
            val parsed = parse(text)
            if (parsed == null) {
                fileIsBroken = true
                currentConfig.value = createDefault()
                return
            }

            fileIsBroken = false
            currentLoadProblem.value = null
            currentConfig.value = parsed
        }
    }

    /**
     * Changes the config and saves it. [change] gets the current config and returns the new one
     * (data classes make this easy: `update { it.copy(orientation = Orientation.Portrait) }`).
     *
     * @return false if nothing was saved because config.json on disk is broken (see [loadProblem]).
     */
    suspend fun update(change: (AppConfig) -> AppConfig): Boolean {
        writeLock.withLock {
            if (fileIsBroken) {
                log.w { "Not saving: config.json couldn't be read, fix or delete it first" }
                return false
            }
            val newConfig = change(currentConfig.value)
            writeFile(newConfig)
            currentConfig.value = newConfig
            return true
        }
    }

    /** The current config's ETag (see [etagOf]). */
    val etag: String
        get() = etagOf(currentConfig.value)

    /**
     * Like [update], but only if the config is still the version the caller last saw. The device API
     * uses this for `If-Match`: if someone else changed the config in between, the change is refused
     * instead of silently overwriting theirs.
     *
     * @param ifMatch the ETag the caller last saw, or null to skip the check.
     */
    suspend fun updateIfMatch(ifMatch: String?, change: (AppConfig) -> AppConfig): ConditionalUpdate {
        writeLock.withLock {
            if (fileIsBroken) return ConditionalUpdate.FileBroken
            val currentEtag = etagOf(currentConfig.value)
            if (ifMatch != null && ifMatch != currentEtag) return ConditionalUpdate.Conflict(currentEtag)
            val newConfig = change(currentConfig.value)
            writeFile(newConfig)
            currentConfig.value = newConfig
            return ConditionalUpdate.Saved(newConfig, etagOf(newConfig))
        }
    }

    /**
     * Copies the current config.json into the backups folder. Keeps the newest [keep] backups.
     * Called on app upgrades and before remote changes.
     */
    suspend fun backup(keep: Int = 5) {
        writeLock.withLock {
            if (!fileSystem.exists(paths.configFile)) return

            fileSystem.createDirectories(paths.backupDir)
            // Colons aren't allowed in Windows file names, so use dashes in the timestamp.
            val timestamp = clock.now().toString().replace(':', '-')
            val backupFile = Path(paths.backupDir, "config-$timestamp.json")
            writeText(backupFile, readFile(paths.configFile))

            deleteOldBackups(keep)
        }
    }

    private fun deleteOldBackups(keep: Int) {
        // Backup names contain an ISO timestamp, so sorting by name sorts by age.
        val backups = fileSystem.list(paths.backupDir)
            .filter { file -> file.name.startsWith("config-") && file.name.endsWith(".json") }
            .sortedBy { file -> file.name }
        val tooOld = backups.dropLast(keep)
        for (file in tooOld) {
            fileSystem.delete(file)
        }
    }

    /** Parses config text, or records the problem and returns null. */
    private fun parse(text: String): AppConfig? {
        try {
            return ConfigJson.decodeFromString(AppConfig.serializer(), text)
        } catch (error: SerializationException) {
            // Invalid JSON, or valid JSON in the wrong shape.
            val problem = "config.json couldn't be read, so Sekund is using defaults until it's fixed: ${error.message}"
            log.e { problem }
            currentLoadProblem.value = problem
            return null
        }
    }

    private fun writeFile(config: AppConfig) {
        writeText(paths.configFile, ConfigJson.encodeToString(AppConfig.serializer(), config) + "\n")
    }

    /**
     * Writes [text] to a temporary file first, then moves it into place. If the app crashes or loses
     * power halfway, the old file is still intact.
     */
    private fun writeText(file: Path, text: String) {
        val folder = file.parent ?: error("$file has no parent folder")
        fileSystem.createDirectories(folder)
        val temporary = Path(folder, file.name + ".tmp")
        fileSystem.sink(temporary).buffered().use { sink -> sink.writeString(text) }
        fileSystem.atomicMove(temporary, file)
    }

    private fun readFile(file: Path): String = fileSystem.source(file).buffered().use { source -> source.readString() }
}

/** The result of [ConfigRepository.updateIfMatch]. */
sealed class ConditionalUpdate {
    data class Saved(val config: AppConfig, val etag: String) : ConditionalUpdate()

    /** Someone else changed the config since the caller read it. [currentEtag] is the new version. */
    data class Conflict(val currentEtag: String) : ConditionalUpdate()

    /** config.json on disk couldn't be read, so nothing is saved until it's fixed. */
    data object FileBroken : ConditionalUpdate()
}

/**
 * A short fingerprint of [config]: the start of a SHA-256 hash of its JSON. Any change to the config
 * changes it, so it works as an HTTP ETag ("version tag").
 */
fun etagOf(config: AppConfig): String {
    val json = ConfigJson.encodeToString(AppConfig.serializer(), config)
    return urlSafeBase64.encode(sha256(json.encodeToByteArray())).take(16)
}

/**
 * JSON settings for config.json: indented for people, and forgiving. `coerceInputValues` means an
 * invalid value for a field with a default (e.g. `"orientation": "sideways"`) uses the default instead
 * of failing the whole file.
 */
private val ConfigJson: Json = Json(from = SekundPrettyJson) {
    coerceInputValues = true
}
