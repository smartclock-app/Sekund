package uk.dnpk.sekund

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import uk.dnpk.sekund.plugins.declarativebuiltins.BuiltInDefinitions
import uk.dnpk.sekund.runtime.updates.DesktopUpdateInstaller
import uk.dnpk.sekund.runtime.AppGraph
import kotlinx.io.files.Path
import uk.dnpk.sekund.runtime.DesktopPaths
import uk.dnpk.sekund.runtime.api.PlatformInfo
import uk.dnpk.sekund.runtime.discovery.JmDnsAdvertiser
import uk.dnpk.sekund.runtime.auth.secrets.DesktopSecretCipher

/**
 * Desktop and Raspberry Pi entry point.
 *
 * Options:
 * - `--fullscreen` fills the screen with no window frame (for a dedicated display).
 * - `--data-dir=<folder>` keeps all files in one folder (see DesktopPaths).
 */
fun main(args: Array<String>) {
    val fullscreen = args.contains("--fullscreen")

    val paths = DesktopPaths.resolve(args)
    val graph = AppGraph(
        paths = paths,
        plugins = BuiltInPlugins,
        declarativePlugin = ::declarativePlugin,
        builtInDefinitions = { BuiltInDefinitions.readAll() },
        defaultConfig = DefaultConfig.create(),
        // The key lives in the OS keychain, or in this owner-only file where there is no keychain.
        secretCipher = DesktopSecretCipher(keyFile = Path(paths.secretsDir, "secrets.key")),
        // Packaged apps get their version from jpackage; `gradlew run` doesn't have one.
        platform = PlatformInfo(name = "desktop", appVersion = System.getProperty("jpackage.app-version") ?: "dev"),
        advertiser = JmDnsAdvertiser(),
        // Opens a verified update's installer for the user to confirm.
        updateInstaller = DesktopUpdateInstaller(),
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
