package uk.dnpk.sekund.sdk.video

/**
 * Cuts an MJPEG stream into single JPEG pictures.
 *
 * An MJPEG stream (`multipart/x-mixed-replace`) is just JPEG files one after another, with small text
 * headers in between. Every JPEG starts with the bytes `FF D8` and ends with `FF D9`, so instead of
 * parsing the multipart headers we look for those two markers. This works for every camera we've seen and
 * is much simpler; a frame that gets cut wrongly just fails to decode and is skipped.
 *
 * Feed it bytes as they arrive with [feed]; it returns the frames completed by that chunk.
 */
class MjpegSplitter(private val maxFrameBytes: Int = 8 * 1024 * 1024) {
    private var buffer = ByteArray(64 * 1024)

    /** How many bytes of [buffer] are in use. */
    private var length = 0

    /** Where the current frame's `FF D8` is in [buffer], or -1 while we're still looking for one. */
    private var frameStart = -1

    /** Where to continue scanning from, so bytes are only looked at once. */
    private var scanFrom = 0

    fun feed(chunk: ByteArray, count: Int = chunk.size): List<ByteArray> {
        append(chunk, count)
        val frames = mutableListOf<ByteArray>()

        // Stop one byte early: each marker is two bytes.
        while (scanFrom < length - 1) {
            val first = buffer[scanFrom]
            val second = buffer[scanFrom + 1]
            val lookingForStart = frameStart < 0
            when {
                lookingForStart && first == MARKER && second == START -> {
                    frameStart = scanFrom
                    scanFrom += 2
                }
                !lookingForStart && first == MARKER && second == END -> {
                    val frameEnd = scanFrom + 2
                    frames.add(buffer.copyOfRange(frameStart, frameEnd))
                    frameStart = -1
                    scanFrom = frameEnd
                }
                else -> scanFrom += 1
            }
        }

        dropUsedBytes()
        if (length > maxFrameBytes) {
            // No end marker for far too long: the stream is broken. Start again from the next picture.
            reset()
        }
        return frames
    }

    private fun append(chunk: ByteArray, count: Int) {
        if (length + count > buffer.size) {
            var newSize = buffer.size * 2
            while (newSize < length + count) newSize *= 2
            buffer = buffer.copyOf(newSize)
        }
        chunk.copyInto(buffer, destinationOffset = length, startIndex = 0, endIndex = count)
        length += count
    }

    /** Moves the unfinished part to the front of [buffer], forgetting everything before it. */
    private fun dropUsedBytes() {
        // Keep from the current frame's start, or else just the last byte (it may be the first half of a marker).
        val keepFrom = if (frameStart >= 0) frameStart else maxOf(scanFrom, 0)
        if (keepFrom == 0) return
        buffer.copyInto(buffer, destinationOffset = 0, startIndex = keepFrom, endIndex = length)
        length -= keepFrom
        scanFrom -= keepFrom
        if (frameStart >= 0) frameStart -= keepFrom
    }

    private fun reset() {
        length = 0
        frameStart = -1
        scanFrom = 0
    }

    private companion object {
        const val MARKER = 0xFF.toByte()
        const val START = 0xD8.toByte()
        const val END = 0xD9.toByte()
    }
}
