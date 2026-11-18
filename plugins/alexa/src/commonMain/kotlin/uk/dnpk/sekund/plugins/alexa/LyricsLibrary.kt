package uk.dnpk.sekund.plugins.alexa

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uk.dnpk.sekund.sdk.context.FileCache
import uk.dnpk.sekund.sdk.context.PluginLog

/**
 * Finds synced lyrics on lrclib.net (free, no account) and keeps every answer in the instance's file cache,
 * including "this song has none", so each song is only looked up once.
 */
class LyricsLibrary(
    private val http: HttpClient,
    private val files: FileCache,
    private val log: PluginLog,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Track(val syncedLyrics: String? = null)

    /** The song's synced lyrics, or null if there are none (or lrclib can't be reached right now). */
    suspend fun find(title: String, artist: String?): Lyrics? {
        val name = cacheName(title, artist)
        val cached = files.read(name)
        if (cached != null) return Lyrics.parse(cached.decodeToString()) // an empty file means "none"

        try {
            val response = http.get("https://lrclib.net/api/search") {
                parameter("track_name", title)
                if (!artist.isNullOrBlank()) parameter("artist_name", artist)
                // lrclib asks apps to say who they are.
                header(HttpHeaders.UserAgent, "Sekund wall display")
            }
            if (response.status.value !in 200..299) return null // try again next time the song plays
            val tracks = json.decodeFromString<List<Track>>(response.bodyAsText())
            val synced = tracks.firstOrNull { track -> !track.syncedLyrics.isNullOrBlank() }?.syncedLyrics ?: ""
            files.write(name, synced.encodeToByteArray())
            return Lyrics.parse(synced)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.warn("Couldn't look up lyrics for '$title'", error)
            return null
        }
    }

    private fun cacheName(title: String, artist: String?): String {
        val text = "lyrics-$title-${artist ?: ""}"
        return text.map { char -> if (char.isLetterOrDigit()) char.lowercaseChar() else '_' }.joinToString("").take(120) + ".lrc"
    }
}
