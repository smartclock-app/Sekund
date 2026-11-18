package uk.dnpk.sekund.sdk.clock

import androidx.compose.runtime.Composable

/**
 * What fills the main square: the default digital clock, a photo slideshow with the time on top, etc.
 *
 * Exactly one clock face is shown at a time. If it fails, Sekund falls back to the default clock face,
 * so the main zone is never empty. Read the time from `context.time`.
 */
interface ClockFace {
    @Composable
    fun Content()
}
