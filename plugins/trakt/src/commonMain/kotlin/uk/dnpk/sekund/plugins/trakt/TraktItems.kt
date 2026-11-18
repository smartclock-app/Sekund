package uk.dnpk.sekund.plugins.trakt

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Whether an item is a film or a show; they're looked up differently. */
enum class TraktKind { Movie, Show }

/**
 * One film or show to follow, e.g. `TraktItem(Show, "severance")`.
 *
 * @property key stable id used in the cache, e.g. "show:severance".
 */
data class TraktItem(val kind: TraktKind, val slug: String) {
    val key: String
        get() = "${kind.name.lowercase()}:$slug"
}

/**
 * What's remembered about an item between fetches (and restarts), so details are fetched at most daily.
 *
 * @property airDate when it next airs (or aired today): a UTC timestamp for episodes, a date for films.
 *   Null if nothing is scheduled.
 */
@Serializable
data class CachedItem(
    val key: String,
    val name: String,
    val airDate: String?,
    val fetchedAt: Instant,
)

object TraktItems {
    /**
     * The films and shows to follow, from list and watchlist entries. Each appears once.
     * Seasons and people are ignored; episodes count as their show only with [episodesAsShow].
     */
    fun from(entries: List<TraktListEntry>, episodesAsShow: Boolean): List<TraktItem> {
        val items = LinkedHashSet<TraktItem>() // keeps the first occurrence, in order
        for (entry in entries) {
            val item = when (entry.type) {
                "movie" -> itemOf(TraktKind.Movie, entry.movie)
                "show" -> itemOf(TraktKind.Show, entry.show)
                "episode" -> if (episodesAsShow) itemOf(TraktKind.Show, entry.show) else null
                else -> null
            }
            if (item != null) items.add(item)
        }
        return items.toList()
    }

    private fun itemOf(kind: TraktKind, title: TraktTitle?): TraktItem? {
        val slug = title?.ids?.slug ?: return null
        return TraktItem(kind, slug)
    }
}
