package uk.dnpk.sekund.runtime.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import uk.dnpk.sekund.runtime.platform.NetworkWatcher

/**
 * Tells the offline check when Android's network changes, and reconnects Wi-Fi when it's stuck.
 *
 * Reconnecting only works as device owner (Android stopped letting ordinary apps do it in Android 10),
 * and only while Wi-Fi is switched on: if someone turned Wi-Fi off on purpose, it stays off.
 */
class AndroidNetworkWatcher(context: Context, private val owner: DeviceOwner) : NetworkWatcher {
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // A shared flow fed by one callback registered for the app's lifetime. (Registering per listener would
    // report the current network straight away each time, which looks like a change.)
    private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val changes: Flow<Unit> = changed

    init {
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    changed.tryEmit(Unit)
                }

                override fun onLost(network: Network) {
                    changed.tryEmit(Unit)
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
}
