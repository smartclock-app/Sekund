package uk.dnpk.sekund.sdk.video

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class MjpegSplitterTest {
    /** A pretend JPEG: start marker, some bytes, end marker. */
    private fun jpeg(vararg middle: Int): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + middle.map { it.toByte() }.toByteArray() + byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    private fun header(): ByteArray = "--frame\r\nContent-Type: image/jpeg\r\n\r\n".encodeToByteArray()

    @Test
    fun `frames between multipart headers are cut out`() {
        val stream = header() + jpeg(1, 2, 3) + "\r\n".encodeToByteArray() + header() + jpeg(4, 5)

        val frames = MjpegSplitter().feed(stream)

        assertEquals(2, frames.size)
        assertContentEquals(jpeg(1, 2, 3), frames[0])
        assertContentEquals(jpeg(4, 5), frames[1])
    }

    @Test
    fun `frames and markers split across chunks are put back together`() {
        val stream = header() + jpeg(1, 2, 3) + header() + jpeg(9)
        val splitter = MjpegSplitter()
        val frames = mutableListOf<ByteArray>()

        // One byte at a time: every marker is split across two chunks somewhere.
        for (byte in stream) {
            frames.addAll(splitter.feed(byteArrayOf(byte)))
        }

        assertEquals(2, frames.size)
        assertContentEquals(jpeg(1, 2, 3), frames[0])
        assertContentEquals(jpeg(9), frames[1])
    }

    @Test
    fun `a frame that never ends is dropped and the next one still arrives`() {
        val splitter = MjpegSplitter(maxFrameBytes = 100)
        splitter.feed(byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(200))

        val frames = splitter.feed(jpeg(7))

        assertEquals(1, frames.size)
        assertContentEquals(jpeg(7), frames[0])
    }

    @Test
    fun `reconnects wait longer after each failure, up to 30 seconds`() {
        assertEquals(1.seconds, retryDelay(0))
        assertEquals(1.seconds, retryDelay(1))
        assertEquals(2.seconds, retryDelay(2))
        assertEquals(16.seconds, retryDelay(5))
        assertEquals(30.seconds, retryDelay(9))
    }
}
