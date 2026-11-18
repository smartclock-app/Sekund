package uk.dnpk.sekund.runtime.platform

import uk.dnpk.sekund.protocol.plugin.Capability
import uk.dnpk.sekund.runtime.updates.UpdateInstaller

/**
 * What this platform can do beyond the basics, passed to AppGraph by each entry point.
 *
 * Every service is optional: null means "this platform can't", and the app hides that feature
 * (no menu button, no device action, no capability for the companion). So the shared code never
 * asks "am I on Android?"; it asks "is there a [display] control?".
 *
 * @property display turns the screen on and off.
 * @property kiosk locks the device to Sekund (Android device owner).
 * @property network tells the app when the network changes, and can try to reconnect.
 * @property connectivityCheckUrl an address that answers `204 No Content` when the internet works.
 *   The offline indicator probes it. Null turns the indicator off (tests do this).
 * @property updateInstaller installs verified app updates.
 * @property openBrowser opens a web browser (a long-press menu button).
 * @property openSystemSettings opens the system's settings app (a long-press menu button).
 */
class PlatformServices(
    val display: DisplayControl? = null,
    val kiosk: KioskControl? = null,
    val network: NetworkWatcher? = null,
    val connectivityCheckUrl: String? = null,
    val updateInstaller: UpdateInstaller? = null,
    val openBrowser: (() -> Unit)? = null,
    val openSystemSettings: (() -> Unit)? = null,
) {
    /** What the companion is told this device can do (see `GET /v1/device`). */
    fun capabilities(): List<Capability> {
        val result = mutableListOf<Capability>()
        if (display != null) result.add(Capability.DisplayControl)
        if (kiosk != null) result.add(Capability.Kiosk)
        if (updateInstaller != null) result.add(Capability.SelfUpdate)
        if (openBrowser != null) result.add(Capability.OpenBrowser)
        return result
    }

    companion object {
        /** Google's connectivity check, the one Android itself uses. */
        const val GOOGLE_CONNECTIVITY_CHECK: String = "http://connectivitycheck.gstatic.com/generate_204"
    }
}
