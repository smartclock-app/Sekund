package uk.dnpk.sekund.runtime.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Turns the screen on and off (the `display.on` / `display.off` actions).
 *
 * Android needs a one-time permission first (device admin), which [isReady] and [requestSetup] are for.
 */
interface DisplayControl {
    /** Whether the screen is on now. Changes however the screen was switched: by us, a timeout, the power button. */
    val isOn: StateFlow<Boolean>

    /** False while the platform still needs a permission before [turnOff] can work. */
    fun isReady(): Boolean

    /** Opens the system screen that grants the permission. Someone has to accept it on the device. */
    fun requestSetup()

    suspend fun turnOn()

    /** Throws [IllegalStateException] with a readable message if it can't (e.g. not [isReady]). */
    suspend fun turnOff()
}
