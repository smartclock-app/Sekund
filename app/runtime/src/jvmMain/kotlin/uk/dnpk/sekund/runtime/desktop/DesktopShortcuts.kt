package uk.dnpk.sekund.runtime.desktop

import co.touchlab.kermit.Logger
import java.awt.Desktop
import java.io.IOException
import java.net.URI

/** The long-press menu's "Browser" button on desktop. */
class DesktopShortcuts(private val kiosk: DesktopKiosk) {
    private val log = Logger.withTag("Menu")

    /** False where Java can't open a browser (e.g. no desktop environment); then the button is hidden. */
    fun canOpenBrowser(): Boolean =
        Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)

    /**
     * Opens the default browser. The kiosk window stays above other windows, so kiosk mode is left first
     * (the long-press menu can enter it again).
     */
    fun openBrowser() {
        kiosk.exit()
        // Desktop.browse can take a moment while the system starts the browser; don't hold up the UI.
        val thread = Thread {
            try {
                Desktop.getDesktop().browse(URI(START_PAGE))
            } catch (error: IOException) {
                log.w(error) { "Couldn't open a browser" }
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private companion object {
        const val START_PAGE = "https://www.google.com"
    }
}
