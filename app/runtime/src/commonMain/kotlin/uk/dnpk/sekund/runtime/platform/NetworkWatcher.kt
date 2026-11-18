package uk.dnpk.sekund.runtime.platform

import kotlinx.coroutines.flow.StateFlow

/** What the operating system knows about the current network. */
enum class NetworkState {
    /** No network at all (Wi-Fi off or out of range, cable unplugged...). */
    None,

    /** Connected, but the system hasn't confirmed the internet works (no internet, a captive portal, or a
     * system that can't check, e.g. where Google is blocked). The app checks for itself. */
    Connected,

    /** Connected, and the system has checked that the internet works. */
    Verified,
}

/** The platform's view of the network, so the app doesn't have to keep checking for itself. */
interface NetworkWatcher {
    /** The current network's state. Updates live. */
    val state: StateFlow<NetworkState>

    /**
     * Tries to get back online, e.g. by asking Wi-Fi to reconnect to a saved network.
     * Called now and then while offline. Returns false if there was nothing it could try.
     */
    fun tryToReconnect(): Boolean
}
