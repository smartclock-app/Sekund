package uk.dnpk.sekund

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import uk.dnpk.sekund.accounts.AccountsScreen
import uk.dnpk.sekund.plugins.clock.DigitalClockFace
import uk.dnpk.sekund.runtime.AppGraph
import uk.dnpk.sekund.settings.SettingsScreen
import uk.dnpk.sekund.shell.OverlayLayer
import uk.dnpk.sekund.shell.Shell
import uk.dnpk.sekund.shell.ShellState
import uk.dnpk.sekund.ui.theme.SekundTheme

/**
 * The root of the UI, shown by both the Android and Desktop apps.
 *
 * It reads the app's state from [graph] (each `collectAsState()` redraws the UI when that state changes)
 * and hands it to the [Shell], which does the actual layout.
 *
 * @param onQuit closes the app. Null on platforms where an app shouldn't quit itself (Android).
 */
@Composable
fun App(graph: AppGraph, onQuit: (() -> Unit)? = null) {
    val config by graph.config.config.collectAsState()
    val instances by graph.instances.instances.collectAsState()
    val alerts by graph.alerts.alerts.collectAsState()
    val overlays by graph.overlays.entries.collectAsState()
    val configProblem by graph.config.loadProblem.collectAsState()
    val authProblems by graph.authProblems.collectAsState()
    var accountsOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    // Shown if the configured clock face is missing or broken, so the main square is never empty.
    val fallbackClockFace = remember(graph) { DigitalClockFace(graph.time) }

    val state = ShellState(
        config = config,
        instances = instances,
        alerts = alerts,
        configProblem = configProblem,
        registry = graph.registry,
    )

    SekundTheme {
        Shell(
            state = state,
            fallbackClockFace = fallbackClockFace,
            onReload = { graph.instances.reloadAll() },
            onSettings = { settingsOpen = true },
            onAccounts = { accountsOpen = true },
            onQuit = onQuit,
        )
        if (settingsOpen) {
            SettingsScreen(graph = graph, onClose = { settingsOpen = false })
        }
        if (accountsOpen) {
            AccountsScreen(
                auth = graph.auth,
                alerts = graph.alerts,
                problems = authProblems,
                onClose = { accountsOpen = false },
            )
        }
        // Last, so it covers everything: overlays are interruptions such as a doorbell camera.
        OverlayLayer(entries = overlays, onDismiss = { owner -> graph.overlays.dismiss(owner) })
    }
}
