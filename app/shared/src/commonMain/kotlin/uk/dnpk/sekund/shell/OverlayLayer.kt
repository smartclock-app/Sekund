package uk.dnpk.sekund.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import uk.dnpk.sekund.runtime.services.OverlayCenter

/**
 * Draws the most important overlay (if any) over the whole screen. Overlays sit above expanded views
 * and the long-press menu, because they're interruptions the user should see straight away.
 *
 * @param onDismiss called with the owner's id when the user taps a `dismissOnTap` overlay.
 */
@Composable
fun OverlayLayer(entries: Map<String, OverlayCenter.Entry>, onDismiss: (owner: String) -> Unit) {
    val top = OverlayCenter.top(entries) ?: return

    // key(): a different owner gets a fresh composition, so one overlay's state never leaks into the next.
    key(top.owner) {
        var modifier = Modifier.fillMaxSize().background(Color.Black)
        if (top.overlay.dismissOnTap) {
            // The pointerInput key makes the tap handler follow the current owner.
            modifier = modifier.pointerInput(top.owner) { detectTapGestures(onTap = { onDismiss(top.owner) }) }
        } else {
            // Still swallow taps, so they don't reach the widgets underneath.
            modifier = modifier.pointerInput(Unit) { detectTapGestures() }
        }
        Box(modifier = modifier) {
            top.overlay.content()
        }
    }
}
