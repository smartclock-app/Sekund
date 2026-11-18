package uk.dnpk.sekund.sdk.context

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterIsInstance

/**
 * Messages between plugins, and events from Sekund itself. (Like MagicMirror's notifications.)
 *
 * Events are ordinary classes implementing [SekundEvent]. Listen for one kind with [on]:
 *
 * ```kotlin
 * context.scope.launch {
 *     context.events.on<DisplayChanged>().collect { event -> if (event.isOn) refresh() }
 * }
 * ```
 */
interface EventBus {
    /** Every event, from every plugin and from Sekund. */
    val all: SharedFlow<SekundEvent>

    /** Sends [event] to everyone listening. */
    suspend fun publish(event: SekundEvent)
}

/** Only the events of type [T]. */
inline fun <reified T : SekundEvent> EventBus.on(): Flow<T> = all.filterIsInstance<T>()

/** Implemented by every event class. */
interface SekundEvent

// Events published by Sekund itself.

/** Someone is near the display (e.g. reported by a Home Assistant motion sensor). Sekund turns the display on. */
data class PresenceDetected(val source: String) : SekundEvent

/** The display was turned on or off. */
data class DisplayChanged(val isOn: Boolean) : SekundEvent

/** Internet access was lost or came back. */
data class NetworkChanged(val isOnline: Boolean) : SekundEvent
