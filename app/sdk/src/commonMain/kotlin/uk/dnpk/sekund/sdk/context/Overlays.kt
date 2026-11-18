package uk.dnpk.sekund.sdk.context

import androidx.compose.runtime.Composable

/**
 * Full-screen interruptions drawn on top of everything else, e.g. a doorbell camera or a ringing timer.
 *
 * Each instance has at most one overlay; showing a new one replaces the previous one. If several
 * instances show an overlay at once, the one with the highest [Overlay.priority] wins (ties: the most
 * recent). The others stay queued underneath and appear when it goes away.
 *
 * Overlays don't time out on their own: the plugin decides when to [clear] (e.g. a few seconds after
 * the doorbell stops). Sekund also clears an instance's overlay when the instance stops.
 */
interface Overlays {
    fun show(overlay: Overlay)

    fun clear()
}

/**
 * One overlay. [content] fills the whole screen, on a black background.
 *
 * @property priority higher wins when several instances show an overlay at once. Use [Overlay.NORMAL]
 *   unless you have a reason not to; [Overlay.URGENT] is for things like a ringing alarm.
 * @property dismissOnTap if true, tapping anywhere closes the overlay (and calls [onDismissed]).
 * @property onDismissed called when the user taps the overlay away, so the plugin can forget about it.
 *   Not called when the plugin itself calls [Overlays.clear].
 */
class Overlay(
    val priority: Int = NORMAL,
    val dismissOnTap: Boolean = true,
    val onDismissed: () -> Unit = {},
    val content: @Composable () -> Unit,
) {
    companion object {
        const val NORMAL = 0
        const val URGENT = 100
    }
}
