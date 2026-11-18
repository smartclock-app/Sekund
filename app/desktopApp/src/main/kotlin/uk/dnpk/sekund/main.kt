package uk.dnpk.sekund

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.io.files.Path
import org.jetbrains.skia.Image
import uk.dnpk.sekund.plugins.declarativebuiltins.BuiltInDefinitions
import uk.dnpk.sekund.runtime.AppGraph
import uk.dnpk.sekund.runtime.DesktopPaths
import uk.dnpk.sekund.runtime.api.PlatformInfo
import uk.dnpk.sekund.runtime.auth.secrets.DesktopSecretCipher
import uk.dnpk.sekund.runtime.desktop.CommandDisplayControl
import uk.dnpk.sekund.runtime.desktop.DesktopKiosk
import uk.dnpk.sekund.runtime.desktop.DesktopShortcuts
import uk.dnpk.sekund.runtime.discovery.JmDnsAdvertiser
import uk.dnpk.sekund.runtime.platform.PlatformServices
import uk.dnpk.sekund.runtime.updates.DesktopUpdateInstaller

/**
 * Desktop and Raspberry Pi entry point.
 *
 * Options:
 * - `--kiosk` starts in kiosk mode: the window fills the screen with no frame, above other windows.
 *   The long-press menu can leave it and enter it again. (`--fullscreen` is the old name and still works.)
 * - `--data-dir=<folder>` keeps all files in one folder (see DesktopPaths).
 *
 * See docs/desktop.md for running it on a Raspberry Pi.
 */
fun main(args: Array<String>) {
    val kiosk = DesktopKiosk(startLocked = args.contains("--kiosk") || args.contains("--fullscreen"))
    val shortcuts = DesktopShortcuts(kiosk)

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
        services = PlatformServices(
            // Screen on/off with the commands in <config>/display.json, when there is one.
            display = CommandDisplayControl.fromConfigFolder(paths.configDir),
            kiosk = kiosk,
            // Shows an "Offline" badge while this address doesn't answer.
            connectivityCheckUrl = PlatformServices.GOOGLE_CONNECTIVITY_CHECK,
            // Opens a verified update's installer for the user to confirm.
            updateInstaller = DesktopUpdateInstaller(),
            openBrowser = if (shortcuts.canOpenBrowser()) shortcuts::openBrowser else null,
        ),
    )
    graph.start()

    application {
        SekundWindow(graph, kiosk)
    }
}

/**
 * The app's one window, shaped by kiosk mode.
 *
 * A window's frame can't be added or removed once it's shown, so switching kiosk mode builds a new
 * window: `key(locked)` throws the old one away when `locked` changes (≈ a React `key` prop). The app's
 * state lives in the AppGraph, so nothing is lost but what's open on screen (e.g. the settings).
 */
@Composable
private fun ApplicationScope.SekundWindow(graph: AppGraph, kiosk: DesktopKiosk) {
    val locked by kiosk.locked.collectAsState()
    key(locked) {
        val windowState = rememberWindowState(
            placement = if (locked) WindowPlacement.Fullscreen else WindowPlacement.Floating,
        )
        Window(
            onCloseRequest = ::exitApplication,
            title = "Sekund",
            icon = remember { loadWindowIcon() },
            state = windowState,
            undecorated = locked,
            alwaysOnTop = locked,
        ) {
            App(graph = graph, onQuit = ::exitApplication)
        }
    }
}

/**
 * The window's icon (taskbar, dock, title bar), from src/main/resources/sekund-icon.png.
 * The packages get their own icon files (see build.gradle.kts); this one is for the running window.
 */
private fun loadWindowIcon(): Painter? {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("sekund-icon.png") ?: return null
    val bytes = stream.use { input -> input.readAllBytes() }
    return BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}
