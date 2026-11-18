package uk.dnpk.sekund.runtime.declarative

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
import uk.dnpk.sekund.protocol.auth.DefinitionIssue
import uk.dnpk.sekund.protocol.declarative.DeclarativeDefinition
import uk.dnpk.sekund.protocol.declarative.DeclarativeParser
import uk.dnpk.sekund.protocol.declarative.DefinitionKind
import uk.dnpk.sekund.protocol.declarative.ParsedDeclarativeDefinition
import uk.dnpk.sekund.runtime.plugins.PluginRegistry
import uk.dnpk.sekund.sdk.plugin.SekundPlugin

/**
 * One loaded declarative definition.
 *
 * @property file where it was read from, or null for one built into the app.
 * @property text the file as written, comments and all (returned by the device API).
 */
data class LoadedDefinition(
    val definition: DeclarativeDefinition,
    val file: Path?,
    val text: String,
) {
    val id: String
        get() = definition.manifest.id

    val isBuiltIn: Boolean
        get() = file == null
}

/**
 * The declarative widgets and photo sources: built-in definitions plus the user's `*.json` files in
 * `<config>/widgets/`. A user file with the same id as a built-in one replaces it.
 *
 * Each definition becomes an "extra" plugin in the [PluginRegistry], so its instances, settings and
 * layout work like any other plugin's. Installing or removing one updates the registry immediately and
 * the affected instances are recreated, with no restart.
 *
 * @param toPlugin turns a definition into a plugin. The engine lives in `plugins/declarative`, which the
 *   runtime can't depend on, so the app passes `::DeclarativePlugin` in.
 * @param builtIns the text of each definition shipped with the app.
 */
class DeclarativeWidgets(
    private val folder: Path,
    private val registry: PluginRegistry,
    private val toPlugin: (DeclarativeDefinition) -> SekundPlugin,
    private val builtIns: suspend () -> List<String> = { emptyList() },
    private val fileSystem: FileSystem = SystemFileSystem,
) {
    private val log = Logger.withTag("Declarative")
    private val lock = Mutex()
    private val current = MutableStateFlow<List<LoadedDefinition>>(emptyList())
    private val currentProblems = MutableStateFlow<List<String>>(emptyList())

    /** Every usable definition, built-in ones first. */
    val definitions: StateFlow<List<LoadedDefinition>> = current

    /** One line per file that couldn't be used, and why. */
    val problems: StateFlow<List<String>> = currentProblems

    /** Reads everything again and updates the registry. */
    suspend fun load() {
        lock.withLock { loadLocked() }
    }

    fun find(id: String): LoadedDefinition? = current.value.firstOrNull { loaded -> loaded.id == id }

    /**
     * Installs (or replaces) the user definition [id] from [text]. Nothing is written unless it's valid
     * and its `manifest.id` is [id].
     *
     * @return the parse result: check `definition != null` for success, otherwise `issues` says why.
     */
    suspend fun put(id: String, text: String): ParsedDeclarativeDefinition {
        val parsed = DeclarativeParser.parse(text)
        val definition = parsed.definition ?: return parsed
        if (definition.manifest.id != id) {
            return ParsedDeclarativeDefinition(null, listOf(DefinitionIssue("manifest.id", "Must be '$id' to match the address")))
        }
        lock.withLock {
            // Remove an older file for the same id first, in case its kind (and so its file name) changed.
            val old = current.value.firstOrNull { loaded -> loaded.id == id }?.file
            if (old != null) fileSystem.delete(old, mustExist = false)
            write(Path(folder, fileNameFor(definition)), text)
            loadLocked()
        }
        log.i { "Installed declarative definition '$id'" }
        return parsed
    }

    /**
     * Removes the user definition [id]. A built-in definition can't be removed.
     *
     * @return false if there's no user file for [id].
     */
    suspend fun delete(id: String): Boolean {
        lock.withLock {
            val file = current.value.firstOrNull { loaded -> loaded.id == id }?.file ?: return false
            fileSystem.delete(file, mustExist = false)
            loadLocked()
        }
        log.i { "Removed declarative definition '$id'" }
        return true
    }

    private suspend fun loadLocked() {
        val byId = linkedMapOf<String, LoadedDefinition>()
        val problems = mutableListOf<String>()

        for ((index, text) in builtIns().withIndex()) {
            add("built-in definition #${index + 1}", null, text, byId, problems)
        }
        if (fileSystem.exists(folder)) {
            val files = fileSystem.list(folder).filter { file -> file.name.endsWith(".json") }.sortedBy { file -> file.name }
            for (file in files) {
                add(file.toString(), file, read(file), byId, problems)
            }
        }

        current.value = byId.values.toList()
        currentProblems.value = problems
        for (problem in problems) log.w { problem }

        val plugins = mutableListOf<SekundPlugin>()
        for (loaded in byId.values) {
            plugins.add(toPlugin(loaded.definition))
        }
        registry.setExtraPlugins(plugins)
    }

    private fun add(source: String, file: Path?, text: String, into: MutableMap<String, LoadedDefinition>, problems: MutableList<String>) {
        val parsed = DeclarativeParser.parse(text)
        val definition = parsed.definition
        if (definition == null) {
            for (issue in parsed.issues) {
                val where = if (issue.path.isEmpty()) "" else " ${issue.path}"
                problems.add("$source:$where ${issue.message}")
            }
            return
        }
        into[definition.manifest.id] = LoadedDefinition(definition, file, text)
    }

    private fun fileNameFor(definition: DeclarativeDefinition): String = when (definition.kind) {
        DefinitionKind.Widget -> "${definition.manifest.id}.widget.json"
        DefinitionKind.PhotoSource -> "${definition.manifest.id}.photos.json"
    }

    private fun read(file: Path): String = fileSystem.source(file).buffered().use { source -> source.readString() }

    /** Writes via a temporary file, so a crash can't leave half a definition behind. */
    private fun write(file: Path, text: String) {
        fileSystem.createDirectories(folder)
        val temporary = Path(folder, file.name + ".tmp")
        fileSystem.sink(temporary).buffered().use { sink -> sink.writeString(text) }
        fileSystem.atomicMove(temporary, file)
    }
}
