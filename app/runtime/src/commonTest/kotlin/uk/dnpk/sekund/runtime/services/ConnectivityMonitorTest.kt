package uk.dnpk.sekund.runtime.services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Virtual time again: `advanceTimeBy(1.minutes)` runs instantly. The "internet" is the `internetUp` variable.

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityMonitorTest {
    private var internetUp = true
    private var checks = 0
    private var reconnectAttempts = 0
    private val networkChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun TestScope.monitor() = ConnectivityMonitor(
        internetWorks = { checks += 1; internetUp },
        scope = backgroundScope,
        networkChanges = networkChanges,
        tryToReconnect = { reconnectAttempts += 1; true },
    )

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
    fun `a network change triggers a check straight away`() = runTest {
        internetUp = false
        val monitor = monitor()
        monitor.start()
        runCurrent()
        assertFalse(monitor.isOnline.value)

        internetUp = true
        networkChanges.tryEmit(Unit)
        // Only the settle time, not the 5 second backoff.
        advanceTimeBy(ConnectivityMonitor.SETTLE_TIME + 1.seconds)
        assertTrue(monitor.isOnline.value)
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

    @Test
    fun `backoff tops out at a minute`() {
        assertEquals(5.seconds, ConnectivityMonitor.backoff(1))
        assertEquals(40.seconds, ConnectivityMonitor.backoff(4))
        assertEquals(1.minutes, ConnectivityMonitor.backoff(5))
        assertEquals(1.minutes, ConnectivityMonitor.backoff(500))
    }
}
