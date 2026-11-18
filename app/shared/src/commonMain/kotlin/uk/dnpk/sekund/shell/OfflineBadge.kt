package uk.dnpk.sekund.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.dnpk.sekund.ui.theme.Sekund

/** A small "Offline" label in the corner of the screen while the internet doesn't work. */
@Composable
fun OfflineBadge(modifier: Modifier = Modifier) {
    Text(
        text = "Offline",
        style = MaterialTheme.typography.labelLarge,
        color = Sekund.theme.colors.background,
        modifier = modifier
            .background(Sekund.theme.colors.warning, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
