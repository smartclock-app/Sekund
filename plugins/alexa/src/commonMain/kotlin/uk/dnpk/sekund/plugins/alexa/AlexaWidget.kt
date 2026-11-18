package uk.dnpk.sekund.plugins.alexa

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.decodeToImageBitmap
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.time.TimeFormat
import uk.dnpk.sekund.sdk.widget.Widget
import uk.dnpk.sekund.sdk.widget.WidgetLocation
import uk.dnpk.sekund.ui.components.MessageCard
import uk.dnpk.sekund.ui.components.SekundCard
import uk.dnpk.sekund.ui.theme.Sekund
import kotlin.time.Duration.Companion.milliseconds

/**
 * Sidebar cards for Alexa: what's playing (art, progress, synced lyrics), then running timers (counting
 * down every second), then alarms and reminders due in the next 12 hours. Shows nothing when there's
 * nothing to show.
 */
class AlexaWidget(private val context: InstanceContext<AlexaSettings>) : Widget {
    private val data = AlexaData(context)

    @Composable
    override fun Content(location: WidgetLocation) {
        if (context.settings.account == null) {
            MessageCard(title = "Alexa isn't set up", message = "Pick an Amazon account in this widget's settings.")
            return
        }
        val nowPlaying by data.nowPlaying.collectAsState()
        val reminders by data.reminders.collectAsState()
        val lyrics by data.lyrics.collectAsState()
        val now by context.time.everySecond.collectAsState()
        val timeZone by context.time.timeZone.collectAsState()
        val nowInstant = now.toInstant(timeZone)

        Column(verticalArrangement = Arrangement.spacedBy(Sekund.theme.spacing)) {
            val problem = problemOf(nowPlaying, reminders)
            if (problem != null) MessageCard(title = "Alexa", message = problem, barColor = Sekund.theme.colors.warning)

            val playing = (nowPlaying as? Loadable.Ready)?.data
            if (playing != null) NowPlayingCard(playing, playing.progressAt(nowInstant), lyrics)

            val current = (reminders as? Loadable.Ready)?.data
            if (current != null) {
                for (timer in current.timers) {
                    ReminderCard(timer.label, countdown((timer.endsAt - nowInstant).inWholeMilliseconds))
                }
                val preferences by context.time.preferences.collectAsState()
                for (alarm in current.alarms) {
                    ReminderCard(alarm.label, TimeFormat.shortTime(alarm.at, preferences))
                }
            }
        }
    }

    /** One line if loading failed (and there's nothing older to show). */
    private fun problemOf(nowPlaying: Loadable<NowPlaying?>, reminders: Loadable<Reminders>): String? {
        if (nowPlaying is Loadable.Failed) return nowPlaying.message
        if (reminders is Loadable.Failed) return reminders.message
        return null
    }

    @Composable
    private fun NowPlayingCard(playing: NowPlaying, progressMs: Long, lyrics: Lyrics?) {
        val colors = Sekund.theme.colors
        SekundCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val art = albumArt(playing.artUrl)
                    if (art != null) {
                        Image(
                            bitmap = art,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(96.dp).aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(playing.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val artist = playing.artist
                        if (artist != null) Text(artist, color = colors.onCardMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!playing.isRadio && playing.lengthMs > 0) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progressMs.toFloat() / playing.lengthMs },
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.accent,
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(minutesAndSeconds(progressMs), color = colors.onCardMuted, style = MaterialTheme.typography.bodySmall)
                                Text(minutesAndSeconds(playing.lengthMs), color = colors.onCardMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                val window = lyrics?.windowAt(progressMs)
                if (window != null) LyricsLines(window)
            }
        }
    }

    @Composable
    private fun LyricsLines(window: LyricsWindow) {
        val muted = Sekund.theme.colors.onCardMuted
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(window.previous ?: "", color = muted, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(window.current, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(window.next ?: "", color = muted, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    @Composable
    private fun ReminderCard(label: String, value: String) {
        SekundCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }

    /** Downloads and decodes the album art (a public image), once per URL. Null while loading or if it fails. */
    @Composable
    private fun albumArt(url: String?): ImageBitmap? {
        val state = produceState<ImageBitmap?>(initialValue = null, url) {
            if (url == null) return@produceState
            try {
                val bytes = context.http.get(url).readRawBytes()
                value = withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                context.log.warn("Couldn't load album art", error)
            }
        }
        return state.value
    }

    private fun minutesAndSeconds(ms: Long): String {
        val totalSeconds = ms.milliseconds.inWholeSeconds
        return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
    }

    /** "4:05", "1:02:03", or "Done" once it has gone off. */
    private fun countdown(remainingMs: Long): String {
        if (remainingMs <= 0) return "Done"
        val totalSeconds = (remainingMs + 999) / 1000 // round up, like a kitchen timer
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = (totalSeconds % 60).toString().padStart(2, '0')
        if (hours > 0) return "$hours:${minutes.toString().padStart(2, '0')}:$seconds"
        return "$minutes:$seconds"
    }
}
