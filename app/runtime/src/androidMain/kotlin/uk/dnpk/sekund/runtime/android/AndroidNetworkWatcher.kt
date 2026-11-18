package uk.dnpk.sekund.runtime.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.dnpk.sekund.runtime.platform.NetworkState
import uk.dnpk.sekund.runtime.platform.NetworkWatcher

/**
 * Android's own view of the network, so Sekund doesn't have to keep checking the internet itself.
 *
 * Android checks every network it connects to (the same `generate_204` request Sekund would make) and
 * marks it `VALIDATED` when the internet works. That flag on the default network gives [state]:
 * no network → None, validated → Verified, anything else → Connected (Sekund then checks for itself).
 *
 * [tryToReconnect] only works as device owner (Android stopped letting ordinary apps do it in Android 10),
 * and only while Wi-Fi is switched on: if someone turned Wi-Fi off on purpose, it stays off.
 */
class AndroidNetworkWatcher(context: Context, private val owner: DeviceOwner) : NetworkWatcher {
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val current = MutableStateFlow(stateNow())

    override val state: StateFlow<NetworkState> = current

    init {
        // Called for the default network: when one appears, when its flags change (e.g. it becomes
        // validated), and when it's lost. Registered once, for the app's lifetime.
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    current.value = stateOf(capabilities)
                }

                override fun onLost(network: Network) {
                    current.value = NetworkState.None
                }
            },
        )
    }

    @Suppress("DEPRECATION") // reconnect() is deprecated for apps, but still allowed for the device owner.
    override fun tryToReconnect(): Boolean {
        if (!owner.isDeviceOwner()) return false
        if (!wifi.isWifiEnabled) return false
        return wifi.reconnect()
    }

    /** The state before the first callback arrives. */
    private fun stateNow(): NetworkState {
        val network = connectivity.activeNetwork ?: return NetworkState.None
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return NetworkState.None
        return stateOf(capabilities)
    }

    private fun stateOf(capabilities: NetworkCapabilities): NetworkState {
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return NetworkState.Verified
        return NetworkState.Connected
    }
}
