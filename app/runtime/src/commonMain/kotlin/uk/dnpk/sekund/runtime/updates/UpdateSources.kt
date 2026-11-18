package uk.dnpk.sekund.runtime.updates

import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import uk.dnpk.sekund.protocol.update.UpdateSource
import uk.dnpk.sekund.runtime.resources.Res

/** Reads the update source built into the app: `composeResources/files/updates/update-source.json`. */
object UpdateSources {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun builtIn(): UpdateSource {
        val text = Res.readBytes("files/updates/update-source.json").decodeToString()
        return json.decodeFromString<UpdateSource>(text)
    }
}
