package uk.dnpk.sekund.plugins.declarative.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.dnpk.sekund.protocol.declarative.ViewDefinition
import uk.dnpk.sekund.ui.components.SekundCard
import uk.dnpk.sekund.ui.theme.Sekund

/** One line per row (e.g. train departures), at most `maxRows`. */
@Composable
internal fun ListRowsView(view: ViewDefinition.ListView, context: ViewContext) {
    SekundCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val title = context.text(view.title)
            if (!title.isNullOrEmpty()) Text(title, fontWeight = FontWeight.Bold)
            if (context.data.rows.isEmpty()) Text("Nothing to show", color = Sekund.theme.colors.onCardMuted)

            for (row in context.data.rows.take(view.maxRows)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val icon = context.rowText(view.rowIcon, row)
                    if (!icon.isNullOrEmpty()) Text(icon)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(context.rowText(view.rowTitle, row) ?: "", fontWeight = FontWeight.SemiBold)
                        val subtitle = context.rowText(view.rowSubtitle, row)
                        if (!subtitle.isNullOrEmpty()) Text(subtitle, color = Sekund.theme.colors.onCardMuted)
                    }
                    val trailing = context.rowText(view.rowTrailing, row)
                    if (!trailing.isNullOrEmpty()) Text(trailing)
                }
            }
        }
    }
}
