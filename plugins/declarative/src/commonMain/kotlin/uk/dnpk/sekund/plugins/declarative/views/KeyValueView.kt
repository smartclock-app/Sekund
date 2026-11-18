package uk.dnpk.sekund.plugins.declarative.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.dnpk.sekund.protocol.declarative.ViewDefinition
import uk.dnpk.sekund.ui.components.SekundCard
import uk.dnpk.sekund.ui.theme.Sekund

/** "Label: value" lines: the fixed `items` first, then one per row if `rowLabel` is set. */
@Composable
internal fun KeyValueView(view: ViewDefinition.KeyValue, context: ViewContext) {
    SekundCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val title = context.text(view.title)
            if (!title.isNullOrEmpty()) Text(title, fontWeight = FontWeight.Bold)
            for (item in view.items) {
                LabelValueRow(context.text(item.label) ?: "", context.text(item.value) ?: "")
            }
            if (view.rowLabel != null) {
                for (row in context.data.rows) {
                    LabelValueRow(context.rowText(view.rowLabel, row) ?: "", context.rowText(view.rowValue, row) ?: "")
                }
            }
        }
    }
}

@Composable
private fun LabelValueRow(label: String, value: String) {
    Row {
        Text(label, color = Sekund.theme.colors.onCardMuted, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
