package uk.dnpk.sekund

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import uk.dnpk.sekund.plugins.clock.DigitalClockFace
import uk.dnpk.sekund.runtime.AppGraph
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
    val configProblem by graph.config.loadProblem.collectAsState()

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
            onQuit = onQuit,
        )
    }
}
