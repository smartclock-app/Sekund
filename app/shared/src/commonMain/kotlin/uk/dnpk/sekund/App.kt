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
import uk.dnpk.sekund.shell.MenuItem
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
    val update by graph.updater.status.collectAsState()
    val configProblem by graph.config.loadProblem.collectAsState()
    val authProblems by graph.authProblems.collectAsState()
    val isOnline by graph.isOnline.collectAsState()
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
        update = update,
        isOnline = isOnline,
    )

    SekundTheme {
        Shell(
            state = state,
            fallbackClockFace = fallbackClockFace,
            menuItems = {
                menuItems(
                    graph = graph,
                    openSettings = { settingsOpen = true },
                    openAccounts = { accountsOpen = true },
                    onQuit = onQuit,
                )
            },
            onInstallUpdate = { graph.updater.installNow() },
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

/**
 * The long-press menu's buttons. Platform buttons (browser, display, kiosk) only appear where
 * the platform provides them (see PlatformServices).
 */
private fun menuItems(
    graph: AppGraph,
    openSettings: () -> Unit,
    openAccounts: () -> Unit,
    onQuit: (() -> Unit)?,
): List<MenuItem> {
    val services = graph.services
    val items = mutableListOf(
        MenuItem("Reload") { graph.instances.reloadAll() },
        MenuItem("Settings", openSettings),
        MenuItem("Accounts", openAccounts),
    )
    val openBrowser = services.openBrowser
    if (openBrowser != null) items.add(MenuItem("Browser", openBrowser))
    val openSystemSettings = services.openSystemSettings
    if (openSystemSettings != null) items.add(MenuItem("System settings", openSystemSettings))
    val display = services.display
    if (display != null && !display.isReady()) {
        items.add(MenuItem("Enable display control") { display.requestSetup() })
    }
    val kiosk = services.kiosk
    if (kiosk != null) {
        if (kiosk.isLocked()) {
            items.add(MenuItem("Exit kiosk") { kiosk.exit() })
        } else {
            items.add(MenuItem("Enter kiosk") { kiosk.enter() })
        }
    }
    if (onQuit != null) items.add(MenuItem("Quit", onQuit))
    return items
}
