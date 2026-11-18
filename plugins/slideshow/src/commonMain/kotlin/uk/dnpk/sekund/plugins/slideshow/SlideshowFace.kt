package uk.dnpk.sekund.plugins.slideshow

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import uk.dnpk.sekund.sdk.actions.HasActions
import uk.dnpk.sekund.sdk.actions.PluginAction
import uk.dnpk.sekund.sdk.clock.ClockFace
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.time.TimeFormat
import uk.dnpk.sekund.ui.theme.Sekund

/**
 * Photos filling the main square, with the time and date on a dark gradient along the bottom. Photos
 * crossfade into each other. Offers a "skip" action (`<instanceId>.skip`).
 */
class SlideshowFace(private val context: InstanceContext<SlideshowSettings>) : ClockFace, HasActions {
    private val player = SlideshowPlayer(context)

    override val actions = listOf(PluginAction("skip", "Show the next photo") { player.skip() })

    @Composable
    override fun Content() {
        val shown by player.shown.collectAsState()
        val problem by player.problem.collectAsState()
        val picture = decoded(shown)

        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
            // Crossfade ≈ a CSS opacity transition between the old and new photo.
            Crossfade(targetState = picture, animationSpec = tween(durationMillis = 1200)) { image ->
                if (image != null) {
                    Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }

            val note = problem
            if (picture == null && note != null) {
                Text(note, color = Color(0xFFBBBBBB), modifier = Modifier.align(Alignment.Center).padding(maxWidth * 0.05f))
            }

            TimeOverlay(width = maxWidth, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    /**
     * Turns the shown photo's bytes into a picture, off the main thread. Keeps the previous picture
     * while the next decodes, and if a photo can't be decoded.
     */
    @Composable
    private fun decoded(shown: ShownPhoto?): ImageBitmap? {
        val state = produceState<ImageBitmap?>(initialValue = null, shown) {
            if (shown == null) return@produceState
            try {
                value = withContext(Dispatchers.Default) { shown.bytes.decodeToImageBitmap() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                context.log.warn("Couldn't decode photo ${shown.key}", error)
            }
        }
        return state.value
    }

    /** The time and date, white on a gradient that fades from black at the bottom to clear. */
    @Composable
    private fun TimeOverlay(width: Dp, modifier: Modifier) {
        val preferences by context.time.preferences.collectAsState()
        val nowFlow = if (preferences.showSeconds) context.time.everySecond else context.time.everyMinute
        val now by nowFlow.collectAsState()
        val clock = TimeFormat.clock(now, preferences)
        val date = TimeFormat.longDate(now.date)
        val scale = Sekund.theme.clockScale

        val gradient = Brush.verticalGradient(listOf(Color.Transparent, Color(0x9B000000), Color.Black))
        Column(
            modifier = modifier.fillMaxWidth().background(gradient).padding(top = width * 0.08f, bottom = width * 0.04f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(clock.hoursAndMinutes, fontSize = textSize(width * 0.2f * scale), fontFamily = FontFamily.Monospace, color = Color.White)
                Column(modifier = Modifier.padding(start = width * 0.02f), verticalArrangement = Arrangement.Center) {
                    val seconds = clock.seconds
                    if (seconds != null) Text(seconds, fontSize = textSize(width * 0.07f * scale), fontFamily = FontFamily.Monospace, color = Color.White)
                    val amPm = clock.amPm
                    if (amPm != null) Text(amPm, fontSize = textSize(width * 0.07f * scale), fontFamily = FontFamily.Monospace, color = Color.White)
                }
            }
            val dateText = buildAnnotatedString {
                append("${date.weekday} ${date.day}")
                withStyle(SpanStyle(fontSize = 0.6.em, baselineShift = BaselineShift(0.9f))) { append(date.ordinal) }
                append(" ${date.month} ${date.year}")
            }
            Box(modifier = Modifier.padding(top = width * 0.01f)) {
                Text(dateText, fontSize = textSize(width * 0.045f * scale), color = Color.White)
            }
        }
    }

    @Composable
    private fun textSize(size: Dp): TextUnit = with(LocalDensity.current) { size.toSp() }
}
