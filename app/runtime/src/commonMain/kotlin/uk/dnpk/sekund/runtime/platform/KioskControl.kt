package uk.dnpk.sekund.runtime.platform

/**
 * Kiosk mode: Sekund is locked to the screen, so the device can't be used for anything else
 * (except the browser and settings, reachable from the long-press menu).
 *
 * The platform enters kiosk mode by itself when the app starts; this lets someone step out of it
 * (to fix something on the device) and back in.
 */
interface KioskControl {
    fun isLocked(): Boolean

    fun enter()

    /** Leaves kiosk mode until [enter] is called or the app restarts. */
    fun exit()
}
