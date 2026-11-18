package uk.dnpk.sekund.plugins.slideshow

import uk.dnpk.sekund.sdk.photos.Photo
import uk.dnpk.sekund.sdk.photos.PhotoSource
import kotlin.random.Random

/**
 * One photo and where it came from.
 *
 * @property source the source to download it from, or null for a photo read back from the disk cache.
 * @property key unique across sources, e.g. "immich/3f2a..."; also the cache file's name.
 */
class SlideshowPhoto(
    val sourceId: String,
    val photo: Photo,
    val source: PhotoSource?,
    val key: String = cacheNameOf("$sourceId/${photo.id}"),
) {
    companion object {
        /** A plain file name made from [text]: anything but letters, digits, `-`, `_` and `.` becomes `_`. */
        fun cacheNameOf(text: String): String =
            text.map { char -> if (char.isLetterOrDigit() || char == '-' || char == '_' || char == '.') char else '_' }.joinToString("")
    }
}

/**
 * The order photos are shown in, and where we are in it. Pure logic, no timing.
 *
 * When the set of photos changes (a source refreshed), the order is rebuilt but the slideshow carries on
 * from the photo it was showing, so a refresh doesn't restart it from the first photo.
 */
class Playlist(private val random: Random = Random.Default) {
    private var order: List<SlideshowPhoto> = emptyList()
    private var position = -1

    val size: Int
        get() = order.size

    /** Takes the latest photos. Does nothing if they're the same photos as before. */
    fun update(photos: List<SlideshowPhoto>, shuffle: Boolean) {
        val newKeys = photos.map { photo -> photo.key }
        if (newKeys.toSet() == order.map { photo -> photo.key }.toSet()) return

        val currentKey = order.getOrNull(position)?.key
        order = if (shuffle) photos.shuffled(random) else photos
        position = order.indexOfFirst { photo -> photo.key == currentKey } // -1 if it's gone: start from the top
    }

    /** The next photo, going round in a circle. Null if there are no photos. */
    fun next(): SlideshowPhoto? {
        if (order.isEmpty()) return null
        position = (position + 1) % order.size
        return order[position]
    }
}
