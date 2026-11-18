package uk.dnpk.sekund.runtime.services

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.dnpk.sekund.runtime.platform.NetworkState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Knows whether the device is online, for the offline indicator and to refresh everything when the
 * network comes back.
 *
 * Where the platform reports the [network]'s state (Android), it's trusted:
 * - [NetworkState.Verified]: online, nothing to check. Waits for the state to change.
 * - [NetworkState.None]: offline, nothing to check.
 * - [NetworkState.Connected]: the system couldn't confirm the internet works, so [internetWorks] decides.
 *   That covers both a real problem and systems that never confirm (e.g. where Google is blocked).
 *
 * Without a platform state (desktop), [internetWorks] runs every [ONLINE_INTERVAL] while online.
 *
 * Whenever checks are needed and fail, they get more frequent, backing off from 5 seconds up to a minute.
 * While offline, every third failed check calls [tryToReconnect] (on Android, as device owner, this asks
 * Wi-Fi to reconnect).
 *
 * @param internetWorks answers whether the internet works right now. Never throws.
 * @param network the platform's network state (see NetworkWatcher), or null if it has none.
 */
class ConnectivityMonitor(
    private val internetWorks: suspend () -> Boolean,
    private val scope: CoroutineScope,
    private val network: StateFlow<NetworkState>? = null,
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
        val worked = when (network?.value) {
            NetworkState.Verified -> true
            NetworkState.None -> false
            // The system isn't sure, or there's no system view at all: ask for ourselves.
            NetworkState.Connected, null -> internetWorks()
        }
        if (worked) {
            if (!online.value) log.i { "Back online" }
            failures = 0
            online.value = true
            return
        }
        failures += 1
        if (online.value) log.w { "Offline (network: ${network?.value ?: "unknown"})" }
        online.value = false
        if (failures % RECONNECT_EVERY == 0) {
            val tried = tryToReconnect()
            if (tried) log.i { "Asked the system to reconnect" }
        }
    }

    /** Sleeps until the next check is due, or until the platform's network state changes. */
    private suspend fun waitForNextCheck() {
        val states = network
        if (states == null) {
            delay(if (failures == 0) ONLINE_INTERVAL else backoff(failures))
            return
        }
        val before = states.value
        val wait = when {
            // The system will tell us when that stops being true: no timer needed.
            before == NetworkState.Verified -> Duration.INFINITE
            failures == 0 -> ONLINE_INTERVAL
            else -> backoff(failures)
        }
        val changed = withTimeoutOrNull(wait) { states.first { state -> state != before } } != null
        // Just connected: give the system a moment to verify it before checking for ourselves.
        if (changed && states.value == NetworkState.Connected) delay(SETTLE_TIME)
    }

    companion object {
        val ONLINE_INTERVAL: Duration = 1.minutes
        val SETTLE_TIME: Duration = 5.seconds
        const val RECONNECT_EVERY: Int = 3

        /** How long to wait after [failures] failed checks in a row: 5s, 10s, 20s, 40s, then 60s. */
        fun backoff(failures: Int): Duration {
            var wait = 5.seconds
            repeat(minOf(failures - 1, 4)) { wait *= 2 }
            return minOf(wait, 1.minutes)
        }
    }
}
