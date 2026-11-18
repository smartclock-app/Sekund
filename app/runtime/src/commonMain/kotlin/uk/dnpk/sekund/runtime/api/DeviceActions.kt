package uk.dnpk.sekund.runtime.api

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import uk.dnpk.sekund.protocol.api.ActionInfo
import uk.dnpk.sekund.protocol.api.ActionResult
import uk.dnpk.sekund.runtime.instances.InstanceManager
import uk.dnpk.sekund.runtime.instances.RunningInstance

/**
 * The actions a companion can run with `POST /v1/actions/{name}`:
 *
 * - `device.reload`: recreate every instance (the long-press menu's "Reload"),
 * - `device.refresh`: ask every instance to fetch its data again,
 * - `<instanceId>.refresh`: ask one instance to fetch its data again.
 *
 * Display and kiosk actions arrive with the platform phases; plugin-defined actions with the plugins
 * that need them.
 */
class DeviceActions(private val instances: InstanceManager) {
    private val finished = MutableSharedFlow<ActionResult>(extraBufferCapacity = 16)

    /** Every action result, for the events stream. */
    val results: SharedFlow<ActionResult> = finished

    fun list(): List<ActionInfo> {
        val result = mutableListOf(
            ActionInfo("device.reload", "Restart every widget and source"),
            ActionInfo("device.refresh", "Fetch fresh data for everything now"),
        )
        for (instance in instances.instances.value.values) {
            if (instance is RunningInstance.Ready) {
                result.add(ActionInfo("${instance.id}.refresh", "Fetch fresh data for '${instance.id}' now"))
            }
        }
        return result
    }

    fun run(name: String): ActionResult {
        val result = when {
            name == "device.reload" -> {
                instances.reloadAll()
                ActionResult(name, ok = true)
            }
            name == "device.refresh" -> {
                for (instance in instances.instances.value.values) {
                    if (instance is RunningInstance.Ready) instance.refreshNow()
                }
                ActionResult(name, ok = true)
            }
            name.endsWith(".refresh") -> refreshOne(name, name.removeSuffix(".refresh"))
            else -> throw ApiProblem.notFound("No action called '$name'")
        }
        finished.tryEmit(result)
        return result
    }

    private fun refreshOne(name: String, instanceId: String): ActionResult {
        val instance = instances.instances.value[instanceId] ?: throw ApiProblem.notFound("No action called '$name'")
        if (instance !is RunningInstance.Ready) return ActionResult(name, ok = false, message = "'$instanceId' isn't running")
        instance.refreshNow()
        return ActionResult(name, ok = true)
    }
}
