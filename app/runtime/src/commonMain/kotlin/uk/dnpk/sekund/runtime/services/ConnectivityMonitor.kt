package uk.dnpk.sekund.runtime.services

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Knows whether the device is online, for the offline indicator and to refresh everything when the
 * network comes back.
 *
 * It runs [internetWorks] (normally an [InternetCheck]) every [ONLINE_INTERVAL] while online. Once a check
 * fails it checks more often, backing off from 5 seconds up to a minute. A [networkChanges] signal from
 * the platform triggers a check straight away. While offline, every third failed check calls
 * [tryToReconnect] (on Android, as device owner, this asks Wi-Fi to reconnect).
 *
 * @param internetWorks answers whether the internet works right now. Never throws.
 * @param networkChanges the platform's network-change signal (see NetworkWatcher), or null if it has none.
 */
class ConnectivityMonitor(
    private val internetWorks: suspend () -> Boolean,
    private val scope: CoroutineScope,
    private val networkChanges: Flow<Unit>? = null,
    private val tryToReconnect: () -> Boolean = { false },
) {
    private val log = Logger.withTag("Network")

    // Starts as online, so the indicator doesn't flash "offline" while the first check runs.
    private val online = MutableStateFlow(true)

    /** True while the internet works. */
    val isOnline: StateFlow<Boolean> = online

    /** Failed checks in a row. */
    private var failures = 0

    fun start() {
        scope.launch {
            while (true) {
                checkOnce()
                waitForNextCheck()
            }
        }
    }

    private suspend fun checkOnce() {
        val worked = internetWorks()
        if (worked) {
            if (!online.value) log.i { "Back online" }
            failures = 0
            online.value = true
            return
        }
        failures += 1
        if (online.value) log.w { "Offline: the connectivity check failed" }
        online.value = false
        if (failures % RECONNECT_EVERY == 0) {
            val tried = tryToReconnect()
            if (tried) log.i { "Asked the system to reconnect" }
        }
    }

    /** Sleeps until the next check is due, or until the platform reports a network change. */
    private suspend fun waitForNextCheck() {
        val wait = if (failures == 0) ONLINE_INTERVAL else backoff(failures)
        val changes = networkChanges
        if (changes == null) {
            delay(wait)
            return
        }
        val changed = withTimeoutOrNull(wait) { changes.first() } != null
        // After a change, give the network a moment to settle (DHCP, DNS) before checking.
        if (changed) delay(SETTLE_TIME)
    }

    companion object {
        val ONLINE_INTERVAL: Duration = 1.minutes
        val SETTLE_TIME: Duration = 2.seconds
        const val RECONNECT_EVERY: Int = 3

        /** How long to wait after [failures] failed checks in a row: 5s, 10s, 20s, 40s, then 60s. */
        fun backoff(failures: Int): Duration {
            var wait = 5.seconds
            repeat(minOf(failures - 1, 4)) { wait *= 2 }
            return minOf(wait, 1.minutes)
        }
    }
}
