package uk.dnpk.sekund.sdk.context

import kotlinx.coroutines.flow.StateFlow
import uk.dnpk.sekund.sdk.plugin.ExtensionPoint

/**
 * Finds *other* instances by id, e.g. the calendar sources a calendar widget's settings point to
 * (see `SettingsSchema.instanceRefs`).
 *
 * Instances come and go: someone can change a source's settings (which recreates it) or delete it,
 * while the calendar keeps running. So instead of a one-off lookup you get a [StateFlow] that always
 * holds the instance's current state.
 */
interface InstanceLookup {
    /**
     * Follows instance [instanceId], which should belong to [point].
     *
     * Collect the result (or use `flatMapLatest`) rather than reading `.value` once, so you notice
     * when the instance is recreated.
     */
    fun <T : Any> watch(point: ExtensionPoint<T>, instanceId: String): StateFlow<LinkedInstance<T>>
}

/** What [InstanceLookup.watch] reports about one instance. */
sealed interface LinkedInstance<out T> {
    /** The instance is running; [value] is what its plugin created (e.g. a `CalendarSource`). */
    data class Running<T>(val value: T) : LinkedInstance<T>

    /**
     * The instance can't be used. [reason] is a short sentence for people, e.g.
     * "No instance called 'google'" or "'google' couldn't start: Not signed in".
     */
    data class Unavailable(val reason: String) : LinkedInstance<Nothing>
}
