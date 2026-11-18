package uk.dnpk.sekund.sdk.photos

import kotlinx.coroutines.flow.StateFlow
import uk.dnpk.sekund.sdk.context.Loadable
import kotlin.time.Instant

/** Provides photos to the slideshow clock face (e.g. an Immich album). */
interface PhotoSource {
    /** The photos to show, refreshed on the source's own schedule. */
    val photos: StateFlow<Loadable<List<Photo>>>

    /**
     * Downloads [photo]'s picture (JPEG, PNG or WebP bytes). The source does this rather than the slideshow
     * because only the source holds the account that the picture's server wants.
     *
     * @throws Exception with a readable message if it can't be downloaded.
     */
    suspend fun download(photo: Photo): ByteArray
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
