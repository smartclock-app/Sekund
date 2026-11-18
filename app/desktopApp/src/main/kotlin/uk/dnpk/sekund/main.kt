package uk.dnpk.sekund

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import uk.dnpk.sekund.runtime.AppGraph
import uk.dnpk.sekund.runtime.DesktopPaths

/**
 * Desktop and Raspberry Pi entry point.
 *
 * Options:
 * - `--fullscreen` fills the screen with no window frame (for a dedicated display).
 * - `--data-dir=<folder>` keeps all files in one folder (see DesktopPaths).
 */
fun main(args: Array<String>) {
    val fullscreen = args.contains("--fullscreen")

    val graph = AppGraph(
        paths = DesktopPaths.resolve(args),
        plugins = BuiltInPlugins,
        defaultConfig = DefaultConfig.create(),
    )
    graph.start()

    application {
        val windowState = rememberWindowState(
            placement = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating,
        )
        Window(
            onCloseRequest = ::exitApplication,
            title = "Sekund",
            state = windowState,
            undecorated = fullscreen,
        ) {
            App(graph = graph, onQuit = ::exitApplication)
        }
    }
}
