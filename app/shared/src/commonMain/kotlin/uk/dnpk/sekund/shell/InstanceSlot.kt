package uk.dnpk.sekund.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import uk.dnpk.sekund.runtime.instances.RunningInstance
import uk.dnpk.sekund.runtime.instances.valueFor
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.widget.WidgetLocation
import uk.dnpk.sekund.ui.components.ErrorCard

/**
 * Draws the widget instance [instanceId] at [location], or an error card explaining why it can't be
 * shown. Nothing a single widget does wrong in config.json can blank the screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InstanceSlot(
    state: ShellState,
    instanceId: String,
    location: WidgetLocation,
    onExpand: (String) -> Unit,
    onLongPress: () -> Unit,
) {
    val instance = state.instances[instanceId]
    if (instance == null) {
        ErrorCard(title = "Unknown widget '$instanceId'", message = "The layout mentions it, but no instance has that id.")
        return
    }
    if (instance is RunningInstance.Failed) {
        ErrorCard(title = "'$instanceId' couldn't start", message = instance.reason)
        return
    }

    val widget = instance.valueFor(ExtensionPoints.Widgets)
    if (widget == null) {
        ErrorCard(title = "'$instanceId' isn't a widget", message = "It can't be placed in the layout.")
        return
    }

    val allowedLocations = state.registry.contribution(instance.config.type)?.widgetLocations.orEmpty()
    if (location !in allowedLocations) {
        val where = location.name.lowercase()
        ErrorCard(title = "'$instanceId' can't go in the $where", message = "Move it in the layout settings.")
        return
    }

    // Tap opens the expanded view (if the widget has one); long-press opens the menu, as everywhere else.
    val tapModifier = Modifier.combinedClickable(
        onClick = { if (widget.hasExpandedView) onExpand(instanceId) },
        onLongClick = onLongPress,
    )
    Box(modifier = Modifier.fillMaxWidth().then(tapModifier)) {
        widget.Content(location)
    }
}
