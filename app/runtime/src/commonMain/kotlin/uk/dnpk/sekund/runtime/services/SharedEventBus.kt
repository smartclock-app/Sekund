package uk.dnpk.sekund.runtime.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import uk.dnpk.sekund.sdk.context.EventBus
import uk.dnpk.sekund.sdk.context.SekundEvent

/** The one [EventBus] shared by the whole app. Events aren't stored: only current listeners receive them. */
class SharedEventBus : EventBus {
    private val events = MutableSharedFlow<SekundEvent>(extraBufferCapacity = 64)

    override val all: SharedFlow<SekundEvent> = events

    override suspend fun publish(event: SekundEvent) {
        events.emit(event)
    }
}
