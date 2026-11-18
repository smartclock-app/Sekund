package uk.dnpk.sekund.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import uk.dnpk.sekund.sdk.clock.ClockFace
import uk.dnpk.sekund.sdk.widget.WidgetLocation
import uk.dnpk.sekund.ui.components.ErrorCard
import uk.dnpk.sekund.ui.components.MessageCard
import uk.dnpk.sekund.ui.theme.Sekund

/** The main square: any main-zone widgets on top, the clock face filling the rest. */
@Composable
fun MainZone(
    state: ShellState,
    clockFace: ClockFace,
    onExpand: (String) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Sekund.theme.spacing
    Column(modifier = modifier.padding(spacing), verticalArrangement = Arrangement.spacedBy(spacing)) {
        for (instanceId in state.config.layout.main) {
            InstanceSlot(state, instanceId, WidgetLocation.Main, onExpand, onLongPress)
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            clockFace.Content()
        }
    }
}

/** The scrolling column: config problems, the update card and alerts first, then the sidebar widgets in order. */
@Composable
fun Sidebar(
    state: ShellState,
    onExpand: (String) -> Unit,
    onLongPress: () -> Unit,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Sekund.theme.spacing
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        val problem = state.configProblem
        if (problem != null) {
            ErrorCard(title = "config.json has a problem", message = problem)
        }
        val update = state.update.available()
        if (update != null) {
            UpdateCard(update, state.update.install, onInstall = onInstallUpdate)
        }
        for (alert in state.alerts.values) {
            MessageCard(title = alert.title, message = alert.subtitle)
        }
        for (instanceId in state.config.layout.sidebar) {
            InstanceSlot(state, instanceId, WidgetLocation.Sidebar, onExpand, onLongPress)
        }
    }
}
