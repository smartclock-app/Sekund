package uk.dnpk.sekund.sdk.context

import kotlin.time.Instant

/**
 * The state of some data that's loaded in the background. A widget usually draws something different
 * for each case:
 *
 * ```kotlin
 * when (val state = weather.collectAsState().value) {
 *     is Loadable.Loading -> LoadingCard()
 *     is Loadable.Failed -> ErrorCard(state.message)
 *     is Loadable.Ready -> WeatherCard(state.data, stale = state.isStale)
 * }
 * ```
 */
sealed interface Loadable<out T> {
    /** Nothing loaded yet. */
    data object Loading : Loadable<Nothing>

    /**
     * Data is available.
     *
     * @property updatedAt when [data] was fetched.
     * @property refreshError set if the latest refresh failed. [data] is then the last good data.
     */
    data class Ready<T>(
        val data: T,
        val updatedAt: Instant,
        val refreshError: String? = null,
    ) : Loadable<T> {
        /** True if the latest refresh failed, so [data] may be out of date. */
        val isStale: Boolean
            get() = refreshError != null
    }

    /** Loading failed and there's no earlier data to fall back on. */
    data class Failed(val message: String) : Loadable<Nothing>
}

/** The data if it's loaded (even if stale), otherwise null. */
fun <T> Loadable<T>.dataOrNull(): T? {
    if (this is Loadable.Ready) return data
    return null
}
