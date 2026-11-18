package uk.dnpk.sekund.runtime.services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.context.RefreshPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// These tests use kotlinx-coroutines-test's virtual time: `advanceTimeBy(15.minutes)` runs instantly.

@OptIn(ExperimentalCoroutinesApi::class)
class InstanceRefresherTest {
    private val wakeUps = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val displayIsOn = MutableStateFlow(true)

    private fun TestScope.refresher(intervalOverride: Duration? = null) = InstanceRefresher(
        scope = backgroundScope,
        instanceId = "test",
        intervalOverride = intervalOverride,
        wakeUps = wakeUps,
        displayIsOn = displayIsOn,
        clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(testScheduler.currentTime)
        },
        jitterFraction = 0.0, // predictable timing in tests
    )

    @Test
    fun `fetches immediately, then once per interval`() = runTest {
        var calls = 0
        val state = refresher().poll(RefreshPolicy(every = 15.minutes)) { calls += 1; "data $calls" }

        runCurrent()
        assertEquals("data 1", (state.value as Loadable.Ready).data)

        advanceTimeBy(15.minutes - 1.seconds)
        assertEquals(1, calls)
        advanceTimeBy(2.seconds)
        assertEquals(2, calls)
    }

    @Test
    fun `a failed refresh keeps the old data and marks it stale`() = runTest {
        var shouldFail = false
        val state = refresher().poll(RefreshPolicy(every = 15.minutes)) {
            if (shouldFail) error("server down")
            "good data"
        }
        runCurrent()

        shouldFail = true
        advanceTimeBy(15.minutes + 1.seconds)

        val ready = assertIs<Loadable.Ready<String>>(state.value)
        assertEquals("good data", ready.data)
        assertTrue(ready.isStale)
        assertEquals("server down", ready.refreshError)
    }

    @Test
    fun `failures are retried sooner than the interval, backing off`() = runTest {
        var calls = 0
        val state = refresher().poll(RefreshPolicy(every = 15.minutes)) { calls += 1; error("nope") }

        runCurrent()
        assertIs<Loadable.Failed>(state.value)
        advanceTimeBy(30.seconds + 1.seconds) // first retry after 30s
        assertEquals(2, calls)
        advanceTimeBy(60.seconds) // second retry after another 1 minute
        assertEquals(3, calls)
    }

    @Test
    fun `a wake-up refreshes straight away`() = runTest {
        var calls = 0
        refresher().poll(RefreshPolicy(every = 15.minutes)) { calls += 1 }
        runCurrent()

        wakeUps.emit(Unit)
        runCurrent()

        assertEquals(2, calls)
    }

    @Test
    fun `nothing is fetched while the display is off`() = runTest {
        displayIsOn.value = false
        var calls = 0
        refresher().poll(RefreshPolicy(every = 1.minutes)) { calls += 1 }

        advanceTimeBy(10.minutes)
        assertEquals(0, calls)

        displayIsOn.value = true
        runCurrent()
        assertEquals(1, calls)
    }

    @Test
    fun `an interval override below the minimum is raised to the minimum`() = runTest {
        var calls = 0
        refresher(intervalOverride = 5.seconds).poll(RefreshPolicy(every = 15.minutes, minimum = 1.minutes)) { calls += 1 }
        runCurrent()

        advanceTimeBy(30.seconds)
        assertEquals(1, calls)
        advanceTimeBy(31.seconds)
        assertEquals(2, calls)
    }
}
