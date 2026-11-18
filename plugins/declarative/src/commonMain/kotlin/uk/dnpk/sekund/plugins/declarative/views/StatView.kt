package uk.dnpk.sekund.plugins.declarative.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.dnpk.sekund.protocol.declarative.ViewDefinition
import uk.dnpk.sekund.ui.components.SekundCard
import uk.dnpk.sekund.ui.theme.Sekund

/** A big value (e.g. the temperature) with an icon and label, and smaller lines underneath. */
@Composable
internal fun StatView(view: ViewDefinition.Stat, context: ViewContext, large: Boolean) {
    val valueSize = if (large) 72.sp else 40.sp
    SekundCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val icon = context.text(view.icon)
                if (!icon.isNullOrEmpty()) Text(icon, fontSize = valueSize)
                Column {
                    Text(context.text(view.value) ?: "", fontSize = valueSize, fontWeight = FontWeight.Bold)
                    val label = context.text(view.label)
                    if (!label.isNullOrEmpty()) Text(label, color = Sekund.theme.colors.onCardMuted)
                }
            }
            for (line in view.secondary) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(context.text(line.label) ?: "", color = Sekund.theme.colors.onCardMuted)
                    Text(context.text(line.value) ?: "", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
