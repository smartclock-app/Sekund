package uk.dnpk.sekund.sdk.photos

import kotlinx.coroutines.flow.StateFlow
import uk.dnpk.sekund.sdk.context.Loadable
import kotlin.time.Instant

/** Provides photos to the slideshow clock face (e.g. an Immich album). */
interface PhotoSource {
    val photos: StateFlow<Loadable<List<Photo>>>
}

/**
 * One photo. [url] is fetched with the source's account credentials if it has one,
 * so it can point at a private server.
 */
data class Photo(
    val id: String,
    val url: String,
    val caption: String? = null,
    val takenAt: Instant? = null,
)
