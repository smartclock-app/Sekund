package uk.dnpk.sekund.runtime.instances

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.dnpk.sekund.sdk.context.InstanceLookup
import uk.dnpk.sekund.sdk.context.LinkedInstance
import uk.dnpk.sekund.sdk.plugin.ExtensionPoint

/**
 * The real [InstanceLookup]: reads InstanceManager's map of running instances.
 *
 * @param running InstanceManager's live map of instances by id.
 * @param scope the *asking* instance's scope, so the watch stops when that instance is stopped.
 */
class RuntimeInstanceLookup(
    private val running: StateFlow<Map<String, RunningInstance>>,
    private val scope: CoroutineScope,
) : InstanceLookup {

    override fun <T : Any> watch(point: ExtensionPoint<T>, instanceId: String): StateFlow<LinkedInstance<T>> {
        val initial = describe(point, instanceId, running.value)
        // StateFlow only passes on *changes*, so editing some unrelated instance doesn't disturb the watcher.
        return running
            .map { instances -> describe(point, instanceId, instances) }
            .stateIn(scope, SharingStarted.Eagerly, initial)
    }

    private fun <T : Any> describe(point: ExtensionPoint<T>, instanceId: String, instances: Map<String, RunningInstance>): LinkedInstance<T> {
        val instance = instances[instanceId]
        if (instance == null) return LinkedInstance.Unavailable("No instance called '$instanceId'")
        if (instance is RunningInstance.Failed) return LinkedInstance.Unavailable("'$instanceId' couldn't start: ${instance.reason}")

        val value = instance.valueFor(point)
        if (value == null) return LinkedInstance.Unavailable("'$instanceId' isn't one of the ${point.name.lowercase()}")
        return LinkedInstance.Running(value)
    }
}
