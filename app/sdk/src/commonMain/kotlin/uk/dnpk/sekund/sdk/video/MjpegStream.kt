package uk.dnpk.sekund.sdk.video

import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Live pictures from an MJPEG camera stream, e.g. Home Assistant's `/api/camera_proxy_stream/<entity>`.
 *
 * Pass a signed-in client (`context.auth.client(account)`) if the stream needs a login. The stream is
 * opened when you start collecting and closed when you stop. If it drops, it reconnects by itself
 * (waiting a little longer after each failure, up to 30 seconds).
 *
 * If decoding can't keep up, old frames are skipped rather than queued, so the picture never lags behind.
 * Show the frames with [VideoFrames].
 *
 * @param onError told about each failed connection, e.g. to log it. The stream keeps retrying regardless.
 */
fun mjpegFrames(client: HttpClient, url: String, onError: (Throwable) -> Unit = {}): Flow<ImageBitmap> {
    // channelFlow (not flow) because the frames are sent from inside Ktor's `execute { }` block.
    val jpegs = channelFlow {
        var failures = 0
        while (true) {
            try {
                client.prepareGet(url).execute { response ->
                    val body = response.bodyAsChannel()
                    val splitter = MjpegSplitter()
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val read = body.readAvailable(chunk, 0, chunk.size)
                        if (read < 0) break // the server closed the stream
                        for (frame in splitter.feed(chunk, read)) {
                            failures = 0
                            send(frame)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failures += 1
                onError(error)
            }
            delay(retryDelay(failures))
        }
    }
    // conflate(): if decoding is slower than the camera, keep only the newest undecoded frame.
    return jpegs
        .conflate()
        .mapNotNull { bytes -> decodeOrNull(bytes) }
        .flowOn(Dispatchers.Default)
}

/** 1s, 2s, 4s, 8s... capped at 30s. Zero failures (the stream simply ended) waits 1s. */
internal fun retryDelay(failures: Int): Duration {
    if (failures <= 1) return 1.seconds
    val exponent = minOf(failures - 1, 5)
    var seconds = 1
    repeat(exponent) { seconds *= 2 }
    return minOf(seconds, 30).seconds
}

/** A frame that was cut wrongly or is corrupt is skipped instead of stopping the stream. */
private fun decodeOrNull(bytes: ByteArray): ImageBitmap? {
    try {
        return bytes.decodeToImageBitmap()
    } catch (error: Exception) {
        return null
    }
}
