package uk.dnpk.sekund.runtime.services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import uk.dnpk.sekund.runtime.platform.NetworkState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Virtual time again: `advanceTimeBy(1.minutes)` runs instantly. Our own check is the `internetUp`
// variable; the system's view (Android) is `systemState`.

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityMonitorTest {
    private var internetUp = true
    private var checks = 0
    private var reconnectAttempts = 0

    private fun TestScope.monitor(systemState: MutableStateFlow<NetworkState>? = null) = ConnectivityMonitor(
        internetWorks = { checks += 1; internetUp },
        scope = backgroundScope,
        network = systemState,
        tryToReconnect = { reconnectAttempts += 1; true },
    )

    // ---- Without a system view (desktop): our own check decides ----------------------------------

    @Test
    fun `checks once a minute while online`() = runTest {
        val monitor = monitor()
        monitor.start()
        runCurrent()
        assertEquals(1, checks)
        assertTrue(monitor.isOnline.value)

        advanceTimeBy(1.minutes + 1.seconds)
        assertEquals(2, checks)
    }

    @Test
    fun `goes offline when a check fails, and backs off`() = runTest {
        internetUp = false
        val monitor = monitor()
        monitor.start()
        runCurrent()
        assertFalse(monitor.isOnline.value)

        // 5s, then 10s, then 20s between checks.
        advanceTimeBy(5.seconds + 1.seconds)
        assertEquals(2, checks)
        advanceTimeBy(10.seconds)
        assertEquals(3, checks)
        advanceTimeBy(20.seconds)
        assertEquals(4, checks)
    }

    @Test
    fun `tries to reconnect every third failed check`() = runTest {
        internetUp = false
        monitor().start()
        runCurrent()
        assertEquals(0, reconnectAttempts)

        advanceTimeBy(5.seconds + 10.seconds + 1.seconds) // checks 2 and 3
        assertEquals(1, reconnectAttempts)
    }

    // ---- With a system view (Android) ------------------------------------------------------------

    @Test
    fun `a verified network is trusted without checking`() = runTest {
        val system = MutableStateFlow(NetworkState.Verified)
        val monitor = monitor(system)
        monitor.start()
        advanceTimeBy(5.hours)

        assertTrue(monitor.isOnline.value)
        assertEquals(0, checks)
    }

    @Test
    fun `losing the network goes offline straight away, without checking`() = runTest {
        val system = MutableStateFlow(NetworkState.Verified)
        val monitor = monitor(system)
        monitor.start()
        runCurrent()

        system.value = NetworkState.None
        runCurrent()

        assertFalse(monitor.isOnline.value)
        assertEquals(0, checks)
    }

    @Test
    fun `an unverified network is checked by us, after giving the system a moment`() = runTest {
        // E.g. a country where Google's check is blocked: the system never verifies, but the internet works.
        val system = MutableStateFlow(NetworkState.None)
        val monitor = monitor(system)
        monitor.start()
        runCurrent()
        assertFalse(monitor.isOnline.value)

        system.value = NetworkState.Connected
        advanceTimeBy(ConnectivityMonitor.SETTLE_TIME + 1.seconds)

        assertEquals(1, checks)
        assertTrue(monitor.isOnline.value)
    }

    @Test
    fun `an unverified network without internet stays offline`() = runTest {
        internetUp = false
        val system = MutableStateFlow(NetworkState.Connected)
        val monitor = monitor(system)
        monitor.start()
        runCurrent()

        assertFalse(monitor.isOnline.value)
        assertEquals(1, checks)
    }

    @Test
    fun `backoff tops out at a minute`() {
        assertEquals(5.seconds, ConnectivityMonitor.backoff(1))
        assertEquals(40.seconds, ConnectivityMonitor.backoff(4))
        assertEquals(1.minutes, ConnectivityMonitor.backoff(5))
        assertEquals(1.minutes, ConnectivityMonitor.backoff(500))
    }
}
