package uk.dnpk.sekund.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
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
 * The menu opened by long-pressing the screen: one button per [items] entry. Tapping outside the
 * buttons closes it, and it closes by itself after a few seconds.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LongPressMenu(items: List<MenuItem>, onDismiss: () -> Unit) {
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
        // FlowRow wraps onto a new line when the buttons don't fit across (e.g. a portrait tablet).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            for (item in items) {
                Button(onClick = { item.onClick(); onDismiss() }) { Text(item.label) }
            }
        }
    }
}
