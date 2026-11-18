package uk.dnpk.sekund.plugins.declarative

import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import uk.dnpk.sekund.plugins.declarative.views.ImageResult

/**
 * Downloads pictures for the `image` view, with the same host check and sign-in as the widget's source.
 * (A real image cache comes with the slideshow plugin; this is enough for one picture per widget.)
 */
class ImageLoader(private val runner: SourceRunner) {
    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(url: String): ImageResult {
        if (url.isEmpty()) return ImageResult.Failed("No picture")
        try {
            val client = runner.clientForImage(url)
            val response = client.get(url)
            if (response.status.value !in 200..299) return ImageResult.Failed("Picture: HTTP ${response.status.value}")
            return ImageResult.Loaded(response.readRawBytes().decodeToImageBitmap())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return ImageResult.Failed(error.message ?: "Couldn't load the picture")
        }
    }
}
