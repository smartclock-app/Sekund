package uk.dnpk.sekund.runtime.auth

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import uk.dnpk.sekund.protocol.auth.AuthDefinitionParser
import uk.dnpk.sekund.protocol.auth.AuthProviderDefinition

/**
 * Reads every auth provider definition: the built-in ones, then the user's own `*.auth.json` files in
 * `<config>/auth-providers/`. A user file with the same id as a built-in one replaces it, so a provider
 * can be fixed without waiting for an app update.
 *
 * Broken files are skipped and reported in [LoadedProviders.problems].
 */
class AuthProviderLoader(
    private val userFolder: Path,
    private val fileSystem: FileSystem = SystemFileSystem,
    private val readBuiltIn: suspend (String) -> String = { name -> BuiltInAuthProviders.read(name) },
    private val builtInNames: List<String> = BuiltInAuthProviders.FILE_NAMES,
) {
    suspend fun load(): LoadedProviders {
        val providers = linkedMapOf<String, AuthProviderDefinition>()
        val problems = mutableListOf<String>()
        val userFiles = mutableMapOf<String, Path>()

        for (name in builtInNames) {
            add("built-in $name", readBuiltIn(name), providers, problems)
        }

        if (fileSystem.exists(userFolder)) {
            for (file in fileSystem.list(userFolder)) {
                if (!file.name.endsWith(".auth.json")) continue
                val text = fileSystem.source(file).buffered().use { source -> source.readString() }
                val id = add(file.toString(), text, providers, problems)
                if (id != null) userFiles[id] = file
            }
        }
        return LoadedProviders(providers, problems, userFiles)
    }

    /** Adds the definition in [text] if it's valid. Returns its id, or null if it wasn't added. */
    private fun add(source: String, text: String, into: MutableMap<String, AuthProviderDefinition>, problems: MutableList<String>): String? {
        val parsed = AuthDefinitionParser.parse(text)
        val definition = parsed.definition
        if (definition == null) {
            for (issue in parsed.issues) {
                problems.add("$source: ${issue.path} ${issue.message}".replace(":  ", ": "))
            }
            return null
        }
        into[definition.id] = definition
        return definition.id
    }
}

/**
 * Usable definitions by id, plus a readable line for each file or field that was wrong.
 *
 * @property userFiles the user's own file for each provider that came from one (rather than built in).
 */
data class LoadedProviders(
    val providers: Map<String, AuthProviderDefinition>,
    val problems: List<String>,
    val userFiles: Map<String, Path> = emptyMap(),
)
