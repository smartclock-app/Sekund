package uk.dnpk.sekund.sdk.context

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Runs a fetch function on a schedule and keeps its latest result. Most plugins load all their data this way:
 *
 * ```kotlin
 * val weather: StateFlow<Loadable<Weather>> =
 *     context.refresher.poll(RefreshPolicy(every = 15.minutes)) { api.currentWeather() }
 * ```
 *
 * Sekund handles the rest. It:
 * - runs [poll]'s function straight away, then every `policy.every`, plus or minus a little random
 *   jitter so instances don't all fetch at the same moment,
 * - also runs it immediately when the network comes back, the display wakes, or someone presses
 *   "refresh" (see [refreshNow]),
 * - pauses while the display is off,
 * - if a fetch fails, keeps the last good data (marked stale) and retries with increasing delays,
 * - lets people override the interval per instance in settings.
 */
interface Refresher {
    /** Starts polling [fetch] according to [policy]. Call it once per data set, typically in a constructor. */
    fun <T> poll(policy: RefreshPolicy, fetch: suspend () -> T): StateFlow<Loadable<T>>

    /** Asks every poll of this instance to run again now (e.g. after a webhook said "data changed"). */
    fun refreshNow()
}

/**
 * How often to refresh some data.
 *
 * Pick [every] from how fast the data really changes, not as fast as possible. As a guide: weather
 * 15 min, calendars 5 min, TV schedules 30 min, update checks 6 h. Without a good reason, use 15 minutes.
 *
 * @property every how long to wait between fetches.
 * @property minimum the shortest interval people may set in settings, to protect APIs from abuse.
 */
data class RefreshPolicy(
    val every: Duration = 15.minutes,
    val minimum: Duration = 1.minutes,
)
