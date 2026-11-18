package uk.dnpk.sekund.plugins.trakt

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.toLocalDateTime
import uk.dnpk.sekund.sdk.calendar.CalendarEvent
import uk.dnpk.sekund.sdk.calendar.CalendarSource
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.context.RefreshPolicy
import uk.dnpk.sekund.sdk.context.get
import uk.dnpk.sekund.sdk.context.now
import uk.dnpk.sekund.sdk.context.put
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Air dates from a Trakt list and/or the watchlist.
 *
 * Every 30 minutes it re-reads the list (cheap: one or two requests). Each film's or show's details
 * (name and next air date, two or three requests) are cached in storage and only refetched once they're
 * a day old, so a long watchlist doesn't hammer Trakt. If Trakt can't be reached, the cached items are
 * shown, so the calendar still has something after a restart without network.
 */
class TraktCalendarSource(private val context: InstanceContext<TraktSettings>) : CalendarSource {
    private val settings = context.settings

    override val events: StateFlow<Loadable<List<CalendarEvent>>> =
        context.refresher.poll(RefreshPolicy(every = 30.minutes, minimum = 5.minutes)) { refresh() }

    private suspend fun refresh(): List<CalendarEvent> {
        val account = settings.account ?: throw IllegalStateException("Pick a Trakt account in this source's settings")
        if (settings.listId.isEmpty() && !settings.includeWatchlist) {
            throw IllegalStateException("Choose a list or turn on the watchlist in this source's settings")
        }

        val cache: List<CachedItem> = context.storage.get<List<CachedItem>>(CACHE_KEY) ?: emptyList()
        val items = try {
            fetchItems(TraktApi(context.auth.client(account)), cache)
        } catch (error: CancellationException) {
            throw error // the instance is being stopped: not a failure
        } catch (error: Exception) {
            if (cache.isEmpty()) throw error
            // Offline or Trakt is down: show what we know. The next poll tries again.
            context.log.warn("Couldn't reach Trakt, showing cached items", error)
            cache
        }
        return TraktEventBuilder.build(items, context.time.now(), context.time.timeZone.value, settings)
    }

    /** Reads the list (and watchlist), refreshes stale details, saves the cache and returns it. */
    private suspend fun fetchItems(api: TraktApi, cache: List<CachedItem>): List<CachedItem> {
        val entries = mutableListOf<TraktListEntry>()
        if (settings.listId.isNotEmpty()) entries.addAll(api.listEntries(settings.listId))
        if (settings.includeWatchlist) entries.addAll(api.watchlist())
        val wanted = TraktItems.from(entries, settings.episodesAsShow)

        val cachedByKey = cache.associateBy { cached -> cached.key }
        val now = context.time.now()
        // At most 4 items at once, to stay well inside Trakt's rate limit.
        val permits = Semaphore(4)

        val fresh = coroutineScope {
            wanted.map { item ->
                async {
                    val cached = cachedByKey[item.key]
                    if (cached != null && now - cached.fetchedAt < DETAILS_MAX_AGE) {
                        cached
                    } else {
                        permits.withPermit { fetchDetails(api, item, now, fallback = cached) }
                    }
                }
            }.awaitAll()
        }

        // Items that failed with nothing cached are null: leave them out this time.
        val result = fresh.filterNotNull()
        context.storage.put(CACHE_KEY, result)
        return result
    }

    /** Name and air date of one item. On failure, the old cached entry (if any) is kept. */
    private suspend fun fetchDetails(api: TraktApi, item: TraktItem, now: Instant, fallback: CachedItem?): CachedItem? {
        try {
            return when (item.kind) {
                TraktKind.Movie -> {
                    val movie = api.movie(item.slug)
                    CachedItem(item.key, movie.title, movie.released, now)
                }
                TraktKind.Show -> {
                    val show = api.show(item.slug)
                    CachedItem(item.key, show.title, showAirDate(api, item.slug), now)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            context.log.warn("Couldn't fetch details of ${item.key}", error)
            return fallback
        }
    }

    /** The episode airing today if there is one (so it stays on today's list), otherwise the next one. */
    private suspend fun showAirDate(api: TraktApi, slug: String): String? {
        val last = api.lastEpisode(slug)?.firstAired
        if (last != null && airsToday(last)) return last
        return api.nextEpisode(slug)?.firstAired
    }

    private fun airsToday(timestamp: String): Boolean {
        val timeZone = context.time.timeZone.value
        val aired = try {
            Instant.parse(timestamp)
        } catch (error: IllegalArgumentException) {
            return false
        }
        return aired.toLocalDateTime(timeZone).date == context.time.now().toLocalDateTime(timeZone).date
    }

    private companion object {
        const val CACHE_KEY = "items"
        val DETAILS_MAX_AGE = 24.hours
    }
}
