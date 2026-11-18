package uk.dnpk.sekund.plugins.homeassistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

/**
 * Decides which cameras should be on screen as sensors change.
 *
 * - A sensor turns `on`: its cameras appear straight away.
 * - It turns anything else (`off`, `unavailable`...): its cameras disappear [showFor] later, unless it
 *   turns `on` again first.
 *
 * Every change calls [onChange] with the cameras to show (empty = hide the overlay).
 * Delays run in [scope], so they stop when the instance stops.
 */
class ActiveCameras(
    private val scope: CoroutineScope,
    private val cameras: List<CameraSettings>,
    private val showFor: Duration,
    private val onChange: (List<CameraSettings>) -> Unit,
) {
    /** Changes come from the connection and from timers, possibly on different threads: one at a time. */
    private val lock = Mutex()
    private val shown = mutableListOf<CameraSettings>()

    /** Timers that will hide a sensor's cameras, by sensor. */
    private val pendingHides = mutableMapOf<String, Job>()

    suspend fun sensorChanged(trigger: String, state: String) {
        lock.withLock {
            if (state == "on") {
                pendingHides.remove(trigger)?.cancel()
                show(trigger)
            } else {
                scheduleHide(trigger)
            }
        }
    }

    /** The user tapped the overlay away: forget everything until a sensor turns on again. */
    suspend fun dismissAll() {
        lock.withLock {
            for (job in pendingHides.values) job.cancel()
            pendingHides.clear()
            shown.clear()
        }
    }

    private fun show(trigger: String) {
        var changed = false
        for (camera in cameras) {
            if (camera.trigger == trigger && camera !in shown) {
                shown.add(camera)
                changed = true
            }
        }
        if (changed) onChange(shown.toList())
    }

    private fun scheduleHide(trigger: String) {
        val isShown = shown.any { camera -> camera.trigger == trigger }
        if (!isShown || pendingHides.containsKey(trigger)) return
        pendingHides[trigger] = scope.launch {
            delay(showFor)
            lock.withLock {
                pendingHides.remove(trigger)
                shown.removeAll { camera -> camera.trigger == trigger }
                onChange(shown.toList())
            }
        }
    }
}
