package uk.dnpk.sekund.plugins.declarative.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.dnpk.sekund.protocol.declarative.ViewDefinition
import uk.dnpk.sekund.ui.components.SekundCard
import uk.dnpk.sekund.ui.theme.Sekund

/** A label and a bar filled to value / max (e.g. budget spent). */
@Composable
internal fun ProgressView(view: ViewDefinition.Progress, context: ViewContext) {
    val value = context.number(view.value) ?: 0.0
    val max = context.number(view.max) ?: 1.0
    val fraction = if (max <= 0.0) 0f else (value / max).toFloat().coerceIn(0f, 1f)

    SekundCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(context.text(view.label) ?: "", fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            val detail = context.text(view.detail)
            if (!detail.isNullOrEmpty()) Text(detail, color = Sekund.theme.colors.onCardMuted)
        }
    }
}
