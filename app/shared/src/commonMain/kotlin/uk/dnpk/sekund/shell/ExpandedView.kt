package uk.dnpk.sekund.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import uk.dnpk.sekund.runtime.instances.valueFor
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.ui.theme.Sekund
import kotlin.time.Duration.Companion.minutes

/** How long an expanded view stays open with nobody touching it before returning to the normal screen. */
private val AUTO_CLOSE_AFTER = 2.minutes

/** A widget's full-screen view, opened by tapping it. Closes by itself so the display doesn't get stuck. */
@Composable
fun ExpandedView(state: ShellState, instanceId: String, onClose: () -> Unit) {
    val widget = state.instances[instanceId]?.valueFor(ExtensionPoints.Widgets)
    if (widget == null) {
        // The instance was removed while open.
        LaunchedEffect(instanceId) { onClose() }
        return
    }

    LaunchedEffect(instanceId) {
        delay(AUTO_CLOSE_AFTER)
        onClose()
    }

    Box(modifier = Modifier.fillMaxSize().background(Sekund.theme.colors.background)) {
        Box(modifier = Modifier.fillMaxSize().padding(Sekund.theme.spacing)) {
            widget.ExpandedContent()
        }
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
            Text("Close")
        }
    }
}
