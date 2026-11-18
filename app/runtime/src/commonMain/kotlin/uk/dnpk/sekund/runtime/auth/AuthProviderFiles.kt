package uk.dnpk.sekund.runtime.auth

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
import uk.dnpk.sekund.protocol.auth.AuthDefinitionParser
import uk.dnpk.sekund.protocol.auth.DefinitionIssue
import uk.dnpk.sekund.protocol.auth.ParsedAuthDefinition

/**
 * Loads the auth provider definitions into the [AuthRegistry], and installs or removes the user's own
 * `*.auth.json` files (from the device API). Changes apply immediately: instances waiting for a provider
 * start as soon as it's installed.
 */
class AuthProviderFiles(
    private val folder: Path,
    private val registry: AuthRegistry,
    private val loader: AuthProviderLoader = AuthProviderLoader(folder),
    private val fileSystem: FileSystem = SystemFileSystem,
) {
    private val log = Logger.withTag("Auth")
    private val lock = Mutex()
    private val currentProblems = MutableStateFlow<List<String>>(emptyList())
    private var userFiles: Map<String, Path> = emptyMap()

    /** One line per definition file that couldn't be used. Shown on the Accounts screen. */
    val problems: StateFlow<List<String>> = currentProblems

    /** Reads every definition again and gives them to the registry. */
    suspend fun load() {
        lock.withLock { loadLocked() }
    }

    /** True if [id] comes from the user's own file rather than being built in. */
    fun isUserFile(id: String): Boolean = userFiles.containsKey(id)

    /** The text of the user's own file for [id], or null if it's built in or unknown. */
    fun userFileText(id: String): String? {
        val file = userFiles[id] ?: return null
        return fileSystem.source(file).buffered().use { source -> source.readString() }
    }

    /** Validates and installs [text] as the user's definition for [id]. Nothing is written if it's invalid. */
    suspend fun put(id: String, text: String): ParsedAuthDefinition {
        val parsed = AuthDefinitionParser.parse(text)
        val definition = parsed.definition ?: return parsed
        if (definition.id != id) return ParsedAuthDefinition(null, listOf(DefinitionIssue("id", "Must be '$id' to match the address")))
        lock.withLock {
            val old = userFiles[id]
            if (old != null) fileSystem.delete(old, mustExist = false)
            fileSystem.createDirectories(folder)
            val file = Path(folder, "$id.auth.json")
            val temporary = Path(folder, "$id.auth.json.tmp")
            fileSystem.sink(temporary).buffered().use { sink -> sink.writeString(text) }
            fileSystem.atomicMove(temporary, file)
            loadLocked()
        }
        log.i { "Installed auth provider '$id'" }
        return parsed
    }

    /** Removes the user's file for [id] (a built-in definition with the same id comes back). */
    suspend fun delete(id: String): Boolean {
        lock.withLock {
            val file = userFiles[id] ?: return false
            fileSystem.delete(file, mustExist = false)
            loadLocked()
        }
        return true
    }

    private suspend fun loadLocked() {
        val loaded = loader.load()
        for (problem in loaded.problems) log.w { "Auth provider problem: $problem" }
        currentProblems.value = loaded.problems
        userFiles = loaded.userFiles
        registry.setProviders(loaded.providers)
    }
}
