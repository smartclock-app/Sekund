package uk.dnpk.sekund.plugins.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.dnpk.sekund.sdk.calendar.CalendarEvent
import uk.dnpk.sekund.ui.theme.Sekund

/**
 * One event: a coloured bar on the left (the event's colour, or the theme accent), the title (one line
 * per title line, e.g. show + episode for Trakt), then when it happens.
 */
@Composable
internal fun EventRow(event: CalendarEvent, timeText: EventWhen) {
    val barColor = event.color ?: Sekund.theme.colors.accent

    // IntrinsicSize.Min lets the bar stretch to the height of the text next to it.
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Spacer(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(barColor),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            for (line in event.titleLines) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(timeText.firstLine, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val secondLine = timeText.secondLine
            if (secondLine != null) {
                Text(secondLine, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
