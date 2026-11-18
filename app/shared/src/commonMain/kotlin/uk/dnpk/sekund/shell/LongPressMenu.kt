package uk.dnpk.sekund.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/** How long the menu stays open if nothing is pressed. */
private val AUTO_HIDE_AFTER = 8.seconds

/**
 * The menu opened by long-pressing the screen. Tapping outside the buttons closes it, and it closes
 * by itself after a few seconds. More entries (Settings, display control, kiosk) arrive in later phases.
 */
@Composable
fun LongPressMenu(
    onReload: () -> Unit,
    onQuit: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(AUTO_HIDE_AFTER)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { onReload(); onDismiss() }) { Text("Reload") }
            if (onQuit != null) {
                Button(onClick = onQuit) { Text("Quit") }
            }
        }
    }
}
