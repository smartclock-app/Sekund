package uk.dnpk.sekund.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.dnpk.sekund.ui.theme.Sekund

/**
 * A card with a coloured bar, a title and an optional message. Used for errors ("Weather couldn't
 * start"), warnings and alerts.
 */
@Composable
fun MessageCard(
    title: String,
    message: String? = null,
    barColor: Color = Sekund.theme.colors.accent,
    modifier: Modifier = Modifier,
) {
    SekundCard(modifier = modifier) {
        Row {
            Box(modifier = Modifier.width(4.dp).height(40.dp).background(barColor))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = Sekund.theme.colors.onCardMuted)
                }
            }
        }
    }
}

/** A [MessageCard] in the error colour, for something that's broken. */
@Composable
fun ErrorCard(title: String, message: String? = null, modifier: Modifier = Modifier) {
    MessageCard(title = title, message = message, barColor = Sekund.theme.colors.error, modifier = modifier)
}
