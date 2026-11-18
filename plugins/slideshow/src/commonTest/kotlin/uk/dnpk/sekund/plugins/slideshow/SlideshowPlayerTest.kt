package uk.dnpk.sekund.plugins.slideshow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.photos.Photo
import uk.dnpk.sekund.sdk.photos.PhotoSource
import uk.dnpk.sekund.sdk.testing.FakeInstanceContext
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

// The test scheduler's clock is virtual: advanceTimeBy(30.seconds) takes no real time.
class SlideshowPlayerTest {

    /** A photo source whose photos the test sets. Downloading gives the photo id's bytes; ids starting "bad" fail. */
    private class FakePhotoSource(vararg ids: String) : PhotoSource {
        val downloads = mutableListOf<String>()
        override val photos = MutableStateFlow<Loadable<List<Photo>>>(ready(*ids))

        override suspend fun download(photo: Photo): ByteArray {
            downloads.add(photo.id)
            if (photo.id.startsWith("bad")) error("HTTP 500")
            return photo.id.encodeToByteArray()
        }

        companion object {
            fun ready(vararg ids: String): Loadable<List<Photo>> =
                Loadable.Ready(ids.map { id -> Photo(id = id, url = "https://photos.example/$id") }, Clock.System.now())
        }
    }

    private val settings = SlideshowSettings(sources = listOf("immich"), rotateEvery = 30.seconds, cacheToDisk = false, shuffle = false)

    private fun TestScope.player(source: FakePhotoSource?, settings: SlideshowSettings = this@SlideshowPlayerTest.settings): Pair<SlideshowPlayer, FakeInstanceContext<SlideshowSettings>> {
        val context = FakeInstanceContext(settings, backgroundScope)
        context.instances.set("immich", source)
        val player = SlideshowPlayer(context, Random(1))
        runCurrent()
        return player to context
    }

    private fun SlideshowPlayer.showing(): String? = shown.value?.bytes?.decodeToString()

    @Test
    fun photosRotateInOrderAndSkipMovesOnStraightAway() = runTest {
        val (player, _) = player(FakePhotoSource("a", "b", "c"))
        assertEquals("a", player.showing())

        advanceTimeBy(30.seconds + 1.seconds)
        assertEquals("b", player.showing())

        player.skip()
        runCurrent()
        assertEquals("c", player.showing())

        advanceTimeBy(31.seconds)
        assertEquals("a", player.showing()) // round again
    }

    @Test
    fun photosThatFailToDownloadAreSkipped() = runTest {
        val (player, _) = player(FakePhotoSource("bad1", "b"))

        assertEquals("b", player.showing())
    }

    @Test
    fun aMissingSourceIsReported() = runTest {
        val (player, _) = player(source = null)

        assertNull(player.shown.value)
        assertEquals("immich: No instance called 'immich'", player.problem.value)
    }

    @Test
    fun withCachingPhotosAreSavedAndShownWhenTheSourceFails() = runTest {
        val source = FakePhotoSource("a", "b")
        val (player, context) = player(source, settings.copy(cacheToDisk = true))
        advanceTimeBy(31.seconds) // shows a, then b: both downloaded and saved
        assertEquals(setOf("immich_a", "immich_b"), context.files.files.keys)

        // The source breaks (e.g. no network after a restart): the saved photos keep the slideshow going.
        context.instances.set("immich", null)
        runCurrent()
        advanceTimeBy(31.seconds)
        assertEquals(listOf("a", "b"), source.downloads) // never downloaded twice
        assertEquals(true, player.showing() == "a" || player.showing() == "b")
    }

    @Test
    fun savedPhotosNoLongerInTheSourceAreDeleted() = runTest {
        val source = FakePhotoSource("a")
        val (_, context) = player(source, settings.copy(cacheToDisk = true))
        context.files.files["immich_old"] = byteArrayOf(1)

        advanceTimeBy(31.seconds)

        assertEquals(setOf("immich_a"), context.files.files.keys)
    }

    @Test
    fun aRefreshDoesNotRestartFromTheFirstPhoto() {
        val playlist = Playlist()
        fun photos(vararg ids: String) = ids.map { id -> SlideshowPhoto("s", Photo(id, "u"), source = null) }
        playlist.update(photos("a", "b", "c"), shuffle = false)
        playlist.next()
        playlist.next() // showing b

        playlist.update(photos("a", "b", "c", "d"), shuffle = false)

        assertEquals("s_c", playlist.next()?.key)
    }
}
