package uk.dnpk.sekund.runtime.auth

import org.jetbrains.compose.resources.ExperimentalResourceApi
import uk.dnpk.sekund.runtime.resources.Res

/**
 * The auth provider definitions shipped with the app, in `composeResources/files/auth-providers/`.
 *
 * Resources can't be listed at runtime, so every file is named here. Adding a provider = adding the
 * JSON file and its name below. (A test checks that every listed file parses and validates.)
 */
object BuiltInAuthProviders {
    val FILE_NAMES: List<String> = listOf(
        "google.auth.json",
        "trakt.auth.json",
        "immich.auth.json",
        "openweather.auth.json",
        "homeassistant.auth.json",
    )

    /** The text of one built-in file. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun read(fileName: String): String = Res.readBytes("files/auth-providers/$fileName").decodeToString()
}
