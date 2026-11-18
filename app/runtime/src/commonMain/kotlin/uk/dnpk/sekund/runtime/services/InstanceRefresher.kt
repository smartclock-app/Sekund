package uk.dnpk.sekund.runtime.services

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.context.RefreshPolicy
import uk.dnpk.sekund.sdk.context.Refresher
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The [Refresher] given to one plugin instance. See the SDK's Refresher for what plugins can expect.
 *
 * Each `poll` runs its own loop:
 * 1. wait until the display is on,
 * 2. fetch, and publish the result (or keep the old data, marked stale, if it failed),
 * 3. wait for the interval to pass, or for something that should refresh sooner (a wake-up or a
 *    manual refresh), then go back to 1.
 *
 * @param intervalOverride the instance's `refreshInterval` from config.json, if set.
 * @param wakeUps app-wide reasons to refresh immediately (network came back, display turned on).
 * @param displayIsOn while false, nothing is fetched.
 * @param jitterFraction how much to randomise intervals (0.1 = ±10%), so instances don't all fetch at once.
 */
class InstanceRefresher(
    private val scope: CoroutineScope,
    private val instanceId: String,
    private val intervalOverride: Duration?,
    private val wakeUps: Flow<Unit>,
    private val displayIsOn: StateFlow<Boolean>,
    private val clock: Clock = Clock.System,
    private val jitterFraction: Double = 0.1,
    private val random: Random = Random,
) : Refresher {
    private val log = Logger.withTag("Refresh:$instanceId")
    private val manualRefreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun <T> poll(policy: RefreshPolicy, fetch: suspend () -> T): StateFlow<Loadable<T>> {
        val state = MutableStateFlow<Loadable<T>>(Loadable.Loading)
        val interval = effectiveInterval(policy)
        scope.launch { pollForever(state, interval, fetch) }
        return state
    }

    override fun refreshNow() {
        manualRefreshes.tryEmit(Unit)
    }

    /** The configured interval, but never below the plugin's minimum. */
    private fun effectiveInterval(policy: RefreshPolicy): Duration {
        val wanted = intervalOverride ?: policy.every
        if (wanted < policy.minimum) return policy.minimum
        return wanted
    }

    private suspend fun <T> pollForever(
        state: MutableStateFlow<Loadable<T>>,
        interval: Duration,
        fetch: suspend () -> T,
    ) {
        var failuresInARow = 0
        while (true) {
            displayIsOn.first { isOn -> isOn }

            val succeeded = fetchOnce(state, fetch)
            if (succeeded) {
                failuresInARow = 0
                waitForNextRun(withJitter(interval))
            } else {
                failuresInARow += 1
                waitForNextRun(retryDelay(failuresInARow, interval))
            }
        }
    }

    /** Runs [fetch] once and publishes the result. Returns false if it failed. */
    private suspend fun <T> fetchOnce(state: MutableStateFlow<Loadable<T>>, fetch: suspend () -> T): Boolean {
        try {
            val data = fetch()
            state.value = Loadable.Ready(data = data, updatedAt = clock.now())
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled // the instance is being removed: stop, don't report an error
        } catch (error: Exception) {
            val message = error.message ?: error::class.simpleName ?: "Unknown error"
            log.w(error) { "Refresh failed: $message" }

            val previous = state.value
            if (previous is Loadable.Ready) {
                state.value = previous.copy(refreshError = message) // keep showing the old data
            } else {
                state.value = Loadable.Failed(message)
            }
            return false
        }
    }

    /** Returns after [delay], or earlier if a wake-up or manual refresh arrives. */
    private suspend fun waitForNextRun(delay: Duration) {
        withTimeoutOrNull(delay) {
            merge(wakeUps, manualRefreshes).first()
        }
    }

    private fun withJitter(interval: Duration): Duration {
        val factor = 1.0 - jitterFraction + random.nextDouble() * 2 * jitterFraction
        return interval * factor
    }

    /** After failures, retry sooner than the normal interval: 30s, 1m, 2m, 4m... but never longer than [interval]. */
    private fun retryDelay(failuresInARow: Int, interval: Duration): Duration {
        var delay = 30.seconds
        repeat(failuresInARow - 1) { delay *= 2 }
        if (delay > interval) return interval
        return delay
    }
}
