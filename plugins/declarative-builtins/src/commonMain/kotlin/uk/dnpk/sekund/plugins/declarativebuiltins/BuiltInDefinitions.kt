package uk.dnpk.sekund.plugins.declarativebuiltins

import org.jetbrains.compose.resources.ExperimentalResourceApi
import uk.dnpk.sekund.plugins.declarativebuiltins.resources.Res

/**
 * The declarative widget definitions shipped with Sekund, in `composeResources/files/definitions/`.
 *
 * Resources can't be listed at runtime, so every file is named here. Adding one = adding the JSON file
 * and its name below. (A test checks every listed file parses and validates.)
 *
 * They load exactly like a user's own definitions, which keeps the format honest.
 */
object BuiltInDefinitions {
    val FILE_NAMES: List<String> = listOf(
        "weather.widget.json",
        "actualbudget.widget.json",
    )

    /** The text of one file. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun read(fileName: String): String = Res.readBytes("files/definitions/$fileName").decodeToString()

    /** The text of every file, for AppGraph's `builtInDefinitions`. */
    suspend fun readAll(): List<String> = FILE_NAMES.map { name -> read(name) }
}
