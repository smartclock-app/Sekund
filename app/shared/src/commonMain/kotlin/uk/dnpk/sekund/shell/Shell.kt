package uk.dnpk.sekund.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import uk.dnpk.sekund.runtime.config.Orientation
import uk.dnpk.sekund.runtime.instances.valueFor
import uk.dnpk.sekund.sdk.clock.ClockFace
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.ui.theme.Sekund

/**
 * The Sekund screen: a square main zone (clock face + main widgets) and a sidebar.
 *
 * ```
 * Landscape:                      Portrait:
 * +-----------+--------+          +-----------+
 * |           | alerts |          |   main    |
 * |   main    | widget |          |  (square) |
 * |  (square) | widget |          +-----------+
 * |           |  ...   |          |  sidebar  |
 * +-----------+--------+          +-----------+
 * ```
 *
 * Long-press anywhere opens the menu. Tapping a widget that has an expanded view opens it full screen.
 */
@Composable
fun Shell(
    state: ShellState,
    fallbackClockFace: ClockFace,
    onReload: () -> Unit,
    onSettings: () -> Unit,
    onAccounts: () -> Unit,
    onQuit: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var expandedInstanceId by remember { mutableStateOf<String?>(null) }

    val openMenu = { menuOpen = true }
    val expand = { instanceId: String -> expandedInstanceId = instanceId }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Sekund.theme.colors.background)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { openMenu() }) },
    ) {
        val clockFace = clockFaceFor(state, fallbackClockFace)
        // The main zone is a square as tall as the screen (landscape) or as wide as it (portrait).
        // Read these sizes here: inside Row/Column below, `maxWidth` isn't available.
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        if (state.config.orientation == Orientation.Portrait) {
            Column(modifier = Modifier.fillMaxSize()) {
                val mainModifier = if (state.hasSidebar) Modifier.fillMaxWidth().height(screenWidth) else Modifier.fillMaxSize()
                MainZone(state, clockFace, onExpand = expand, onLongPress = openMenu, modifier = mainModifier)
                if (state.hasSidebar) {
                    Sidebar(state, onExpand = expand, onLongPress = openMenu, modifier = Modifier.fillMaxWidth().weight(1f))
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                val mainModifier = if (state.hasSidebar) Modifier.fillMaxHeight().width(screenHeight) else Modifier.fillMaxSize()
                MainZone(state, clockFace, onExpand = expand, onLongPress = openMenu, modifier = mainModifier)
                if (state.hasSidebar) {
                    Sidebar(state, onExpand = expand, onLongPress = openMenu, modifier = Modifier.fillMaxHeight().weight(1f))
                }
            }
        }

        val expandedId = expandedInstanceId
        if (expandedId != null) {
            ExpandedView(state, instanceId = expandedId, onClose = { expandedInstanceId = null })
        }

        if (menuOpen) {
            LongPressMenu(
                onReload = onReload,
                onSettings = onSettings,
                onAccounts = onAccounts,
                onQuit = onQuit,
                onDismiss = { menuOpen = false },
            )
        }
    }
}

/** The configured clock face, or [fallback] if it's missing, broken, or not a clock face. */
private fun clockFaceFor(state: ShellState, fallback: ClockFace): ClockFace {
    val instance = state.instances[state.config.clockFace] ?: return fallback
    return instance.valueFor(ExtensionPoints.ClockFaces) ?: fallback
}
