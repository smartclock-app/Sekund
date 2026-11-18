package uk.dnpk.sekund.runtime.platform

import kotlinx.coroutines.flow.Flow

/** The platform's view of the network, which helps the offline check react quickly. */
interface NetworkWatcher {
    /** Emits whenever the system reports a network change (connected, lost, switched Wi-Fi). */
    val changes: Flow<Unit>

    /**
     * Tries to get back online, e.g. by asking Wi-Fi to reconnect to a saved network.
     * Called now and then while offline. Returns false if there was nothing it could try.
     */
    fun tryToReconnect(): Boolean
}
