package uk.dnpk.sekund.runtime.desktop

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.dnpk.sekund.runtime.platform.KioskControl

/**
 * Kiosk mode on desktop: the window fills the screen, with no frame, above every other window.
 *
 * This class only holds the on/off state; the desktop entry point watches [locked] and shapes the window
 * to match (≈ a store the window component subscribes to). Exiting gives an ordinary window, e.g. to get
 * at the desktop behind it.
 *
 * It can't stop someone with a keyboard switching apps; for a real lock-down on the Raspberry Pi, run Sekund
 * as the only app in the `cage` compositor (see docs/desktop.md).
 *
 * @param startLocked whether the app starts in kiosk mode (the `--kiosk` option).
 */
class DesktopKiosk(startLocked: Boolean) : KioskControl {
    private val log = Logger.withTag("Kiosk")
    private val state = MutableStateFlow(startLocked)

    /** Whether the window should be in kiosk mode now. */
    val locked: StateFlow<Boolean> = state

    override fun isLocked(): Boolean = state.value

    override fun enter() {
        state.value = true
        log.i { "Entered kiosk mode" }
    }

    override fun exit() {
        state.value = false
        log.i { "Left kiosk mode" }
    }
}
