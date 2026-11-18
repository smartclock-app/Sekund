package uk.dnpk.sekund.runtime.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import uk.dnpk.sekund.sdk.context.Overlay
import uk.dnpk.sekund.sdk.context.Overlays

/**
 * Every overlay that instances currently want shown, at most one per owner (an instance id).
 * The shell draws only [top]; the rest wait underneath. Works like [AlertCenter].
 */
class OverlayCenter {
    /** An overlay plus the order it arrived in, so equal priorities can be ordered "newest first". */
    class Entry(val owner: String, val overlay: Overlay, val arrival: Long)

    private val current = MutableStateFlow<Map<String, Entry>>(emptyMap())
    private var arrivals = 0L

    /** All overlays waiting to be shown, keyed by owner. */
    val entries: StateFlow<Map<String, Entry>> = current

    fun show(owner: String, overlay: Overlay) {
        arrivals += 1
        val entry = Entry(owner, overlay, arrivals)
        current.update { entries -> entries + (owner to entry) }
    }

    fun clear(owner: String) {
        current.update { entries -> entries - owner }
    }

    /** The user tapped [owner]'s overlay away: remove it and tell the plugin. */
    fun dismiss(owner: String) {
        val entry = current.value[owner] ?: return
        clear(owner)
        entry.overlay.onDismissed()
    }

    /** The [Overlays] handle given to one instance, which can only touch its own overlay. */
    fun forOwner(owner: String): Overlays = OwnerOverlays(owner)

    private inner class OwnerOverlays(private val owner: String) : Overlays {
        override fun show(overlay: Overlay) = show(owner, overlay)

        override fun clear() = clear(owner)
    }

    companion object {
        /** The overlay to draw: highest priority, and the newest of those. Null when there are none. */
        fun top(entries: Map<String, Entry>): Entry? {
            var best: Entry? = null
            for (entry in entries.values) {
                if (best == null || isAbove(entry, best)) best = entry
            }
            return best
        }

        private fun isAbove(entry: Entry, other: Entry): Boolean {
            if (entry.overlay.priority != other.overlay.priority) return entry.overlay.priority > other.overlay.priority
            return entry.arrival > other.arrival
        }
    }
}
