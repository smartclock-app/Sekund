package uk.dnpk.sekund.plugins.declarative.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import uk.dnpk.sekund.protocol.declarative.ViewDefinition
import uk.dnpk.sekund.ui.components.SekundCard
import uk.dnpk.sekund.ui.theme.Sekund

/** A picture with an optional caption. The picture is downloaded in the background. */
@Composable
internal fun ImageView(view: ViewDefinition.Image, context: ViewContext) {
    val url = context.text(view.url) ?: ""
    // produceState ≈ a React useEffect that sets state: runs the block again whenever `url` changes.
    val result by produceState<ImageResult>(ImageResult.Loading, url) {
        value = context.images.load(url)
    }

    SekundCard(padded = false) {
        Column {
            when (val current = result) {
                ImageResult.Loading -> Text("Loading…", modifier = Modifier.padding(Sekund.theme.cardPadding))
                is ImageResult.Failed -> Text(current.message, color = Sekund.theme.colors.error, modifier = Modifier.padding(Sekund.theme.cardPadding))
                is ImageResult.Loaded -> Image(
                    bitmap = current.bitmap,
                    contentDescription = context.text(view.caption),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val caption = context.text(view.caption)
            if (!caption.isNullOrEmpty()) Text(caption, modifier = Modifier.padding(Sekund.theme.cardPadding))
        }
    }
}

/** Where an image download has got to. */
sealed class ImageResult {
    data object Loading : ImageResult()

    data class Loaded(val bitmap: ImageBitmap) : ImageResult()

    data class Failed(val message: String) : ImageResult()
}
