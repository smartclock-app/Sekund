package uk.dnpk.sekund.plugins.slideshow

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.context.LinkedInstance
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.photos.Photo
import uk.dnpk.sekund.sdk.photos.PhotoSource
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import kotlin.random.Random

/** The photo on screen: its key (to tell photos apart) and its picture as downloaded. */
class ShownPhoto(val key: String, val bytes: ByteArray)

/**
 * Runs the slideshow, without any UI: follows the photo sources, picks the next photo every
 * `rotateEvery` (or straight away on [skip]), downloads it and publishes it as [shown].
 *
 * - A photo that can't be downloaded is skipped (up to 5 in a row, then it waits for the next turn).
 * - With `cacheToDisk`, each photo is saved after downloading and read from disk next time. If no source
 *   has photos (e.g. no network after a restart), the saved photos are shown instead. Saved photos that
 *   are no longer in any source are deleted, but only when every source loaded fine.
 */
class SlideshowPlayer(
    private val context: InstanceContext<SlideshowSettings>,
    random: Random = Random.Default,
) {
    private val settings = context.settings
    private val playlist = Playlist(random)
    private val skips = Channel<Unit>(Channel.CONFLATED)

    private val current = MutableStateFlow<ShownPhoto?>(null)
    private val status = MutableStateFlow<String?>(null)

    /** The photo to show, or null before the first one has loaded. */
    val shown: StateFlow<ShownPhoto?> = current

    /** Why nothing (new) is showing, for a small note on screen; null when all is well. */
    val problem: StateFlow<String?> = status

    /** What the sources currently offer. */
    private val offered: StateFlow<Offered> = followSources()

    init {
        context.scope.launch { play() }
    }

    /** Shows the next photo now, instead of waiting for the timer. */
    fun skip() {
        skips.trySend(Unit)
    }

    private suspend fun play() {
        while (true) {
            val photos = photosToPlay()
            playlist.update(photos, settings.shuffle)

            if (photos.isEmpty()) {
                // Wait for a source to deliver photos (or retry the disk cache after a while), keeping the
                // on-screen note up to date as the sources report in.
                withTimeoutOrNull(settings.rotateEvery) {
                    offered.first { offer ->
                        status.value = noPhotosMessage(offer)
                        offer.photos.isNotEmpty()
                    }
                }
                continue
            }

            showNext()
            // Wait for the next turn, or for someone to press "skip".
            withTimeoutOrNull(settings.rotateEvery) { skips.receive() }
        }
    }

    /** The sources' photos, or the saved ones if the sources have none. Tidies the disk cache on the way. */
    private suspend fun photosToPlay(): List<SlideshowPhoto> {
        val offer = offered.value
        if (offer.photos.isNotEmpty()) {
            if (settings.cacheToDisk && offer.complete) removeUnusedCachedPhotos(offer.photos)
            return offer.photos
        }
        if (!settings.cacheToDisk) return emptyList()
        return context.files.names().map { name -> SlideshowPhoto(CACHE, Photo(id = name, url = ""), source = null, key = name) }
    }

    /** Downloads and shows the next photo that works, trying at most 5. */
    private suspend fun showNext() {
        val attempts = minOf(playlist.size, 5)
        repeat(attempts) {
            val next = playlist.next() ?: return
            val bytes = load(next)
            if (bytes != null) {
                current.value = ShownPhoto(next.key, bytes)
                status.value = null
                return
            }
        }
        status.value = "Couldn't load the photos"
    }

    /** The photo's bytes: from the disk cache if saved there, otherwise downloaded. Null if it failed. */
    private suspend fun load(photo: SlideshowPhoto): ByteArray? {
        try {
            if (settings.cacheToDisk || photo.source == null) {
                val saved = context.files.read(photo.key)
                if (saved != null) return saved
            }
            val source = photo.source ?: return null
            val bytes = source.download(photo.photo)
            if (settings.cacheToDisk) context.files.write(photo.key, bytes)
            return bytes
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            context.log.warn("Couldn't load photo ${photo.key}", error)
            return null
        }
    }

    private suspend fun removeUnusedCachedPhotos(photos: List<SlideshowPhoto>) {
        val wanted = photos.map { photo -> photo.key }.toSet()
        for (name in context.files.names()) {
            if (name !in wanted) context.files.remove(name)
        }
    }

    private fun noPhotosMessage(offer: Offered): String {
        if (settings.sources.isEmpty()) return "No photo sources chosen"
        if (offer.problems.isNotEmpty()) return offer.problems.joinToString("\n")
        if (offer.stillLoading) return "Loading photos…"
        return "No photos to show"
    }

    // ---- Following the sources (like the calendar follows its sources) -----------------------

    /**
     * What all sources offer right now.
     *
     * @property complete true if every source loaded without problems (so the photo list is the whole truth).
     */
    private class Offered(
        val photos: List<SlideshowPhoto>,
        val complete: Boolean,
        val stillLoading: Boolean,
        val problems: List<String>,
    )

    private class SourceState(val sourceId: String, val source: PhotoSource?, val photos: Loadable<List<Photo>>)

    private fun followSources(): StateFlow<Offered> {
        val initial = Offered(emptyList(), complete = false, stillLoading = settings.sources.isNotEmpty(), problems = emptyList())
        if (settings.sources.isEmpty()) return MutableStateFlow(initial)

        val perSource = settings.sources.map { sourceId -> followSource(sourceId) }
        return combine(perSource) { states -> offeredBy(states.toList()) }
            .stateIn(context.scope, SharingStarted.Eagerly, initial)
    }

    @OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest: see CalendarWidget for why it's fine
    private fun followSource(sourceId: String): Flow<SourceState> {
        return context.instances.watch(ExtensionPoints.PhotoSources, sourceId).flatMapLatest { linked ->
            when (linked) {
                is LinkedInstance.Running -> linked.value.photos.map { photos -> SourceState(sourceId, linked.value, photos) }
                is LinkedInstance.Unavailable -> flowOf(SourceState(sourceId, null, Loadable.Failed(linked.reason)))
            }
        }
    }

    private fun offeredBy(states: List<SourceState>): Offered {
        val photos = mutableListOf<SlideshowPhoto>()
        val problems = mutableListOf<String>()
        var complete = true
        var stillLoading = false
        for (state in states) {
            when (val loadable = state.photos) {
                is Loadable.Loading -> {
                    stillLoading = true
                    complete = false
                }
                is Loadable.Failed -> {
                    problems.add("${state.sourceId}: ${loadable.message}")
                    complete = false
                }
                is Loadable.Ready -> {
                    for (photo in loadable.data) photos.add(SlideshowPhoto(state.sourceId, photo, state.source))
                    if (loadable.isStale) complete = false
                }
            }
        }
        return Offered(photos, complete, stillLoading, problems)
    }

    private companion object {
        /** The pretend source id of photos read back from the disk cache. */
        const val CACHE = "cache"
    }
}
