package uk.dnpk.sekund.runtime.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import java.awt.Desktop
import java.io.File

/**
 * Desktop: opens the verified installer (`.deb`, `.dmg`, `.msi`) with the system, which asks the user to
 * confirm. Silent installs on the Raspberry Pi come with the desktop platform work.
 */
class DesktopUpdateInstaller : UpdateInstaller {
    override suspend fun install(file: Path): InstallResult {
        val canOpen = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
        if (!canOpen) return InstallResult.NotSupported("This desktop can't open installers; install ${file.name} by hand")
        // Desktop.open blocks while the system starts the installer, so keep it off the UI thread.
        withContext(Dispatchers.IO) { Desktop.getDesktop().open(File(file.toString())) }
        return InstallResult.WaitingForUser
    }
}
