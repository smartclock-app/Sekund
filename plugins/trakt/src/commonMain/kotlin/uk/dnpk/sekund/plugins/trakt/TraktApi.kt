package uk.dnpk.sekund.plugins.trakt

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The parts of the Trakt API that Sekund uses. Docs: https://trakt.docs.apiary.io
// The `trakt-api-version` and `trakt-api-key` headers are added by the auth registry (trakt.auth.json).

/** One entry of a list or the watchlist. Only the part matching [type] is filled in. */
@Serializable
data class TraktListEntry(
    /** "movie", "show", "season", "episode" or "person". */
    val type: String,
    val movie: TraktTitle? = null,
    val show: TraktTitle? = null,
)

/** A film or show: its title and the slug used in API addresses. */
@Serializable
data class TraktTitle(val title: String = "", val ids: TraktIds = TraktIds())

@Serializable
data class TraktIds(val slug: String? = null)

/** A film's details. [released] is a date like "2025-07-04", or null if not known yet. */
@Serializable
data class TraktMovie(val title: String = "", val released: String? = null)

/** An episode. [firstAired] is a UTC timestamp like "2025-06-18T01:00:00.000Z". */
@Serializable
data class TraktEpisode(@SerialName("first_aired") val firstAired: String? = null)

/** Talks to Trakt. [client] is signed in as the account, so this class never sees a token. */
class TraktApi(private val client: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The entries of the signed-in user's list [listId]. */
    suspend fun listEntries(listId: String): List<TraktListEntry> =
        json.decodeFromString(textOf(client.get("$BASE/users/me/lists/${listId.encodeURLPathPart()}/items"), "list '$listId'"))

    /** Everything on the watchlist. */
    suspend fun watchlist(): List<TraktListEntry> =
        json.decodeFromString(textOf(client.get("$BASE/sync/watchlist/all/released/desc"), "the watchlist"))

    suspend fun show(slug: String): TraktTitle =
        json.decodeFromString(textOf(client.get("$BASE/shows/${slug.encodeURLPathPart()}"), "show '$slug'"))

    suspend fun movie(slug: String): TraktMovie =
        json.decodeFromString(textOf(client.get("$BASE/movies/${slug.encodeURLPathPart()}?extended=full"), "film '$slug'"))

    /** The show's next episode to air, or null if none is scheduled. */
    suspend fun nextEpisode(slug: String): TraktEpisode? = episode("$BASE/shows/${slug.encodeURLPathPart()}/next_episode?extended=full", slug)

    /** The show's most recently aired episode, or null if it has none. */
    suspend fun lastEpisode(slug: String): TraktEpisode? = episode("$BASE/shows/${slug.encodeURLPathPart()}/last_episode?extended=full", slug)

    private suspend fun episode(url: String, slug: String): TraktEpisode? {
        val response = client.get(url)
        // 204 No Content = there's no such episode.
        if (response.status == HttpStatusCode.NoContent) return null
        return json.decodeFromString(textOf(response, "episodes of '$slug'"))
    }

    private suspend fun textOf(response: HttpResponse, what: String): String {
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException("Trakt answered ${response.status.value} for $what")
        }
        return text
    }

    companion object {
        const val BASE = "https://api.trakt.tv"
    }
}
