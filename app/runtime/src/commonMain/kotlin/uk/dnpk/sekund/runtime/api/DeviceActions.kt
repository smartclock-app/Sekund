package uk.dnpk.sekund.runtime.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import uk.dnpk.sekund.protocol.api.ActionInfo
import uk.dnpk.sekund.protocol.api.ActionResult
import uk.dnpk.sekund.runtime.instances.InstanceManager
import uk.dnpk.sekund.runtime.instances.RunningInstance
import uk.dnpk.sekund.runtime.platform.PlatformServices
import uk.dnpk.sekund.runtime.updates.AppUpdater
import uk.dnpk.sekund.sdk.actions.HasActions
import uk.dnpk.sekund.sdk.actions.PluginAction

/**
 * The actions a companion can run with `POST /v1/actions/{name}`:
 *
 * - `device.reload`: recreate every instance (the long-press menu's "Reload"),
 * - `device.refresh`: ask every instance to fetch its data again,
 * - `update.check`: look for a new version of Sekund now,
 * - `update.install`: download and install the new version, if there is one,
 * - `display.on` / `display.off`: turn the screen on or off (where the platform can),
 * - `kiosk.enter` / `kiosk.exit`: lock the device to Sekund, or step out of that (where the platform can),
 * - `<instanceId>.refresh`: ask one instance to fetch its data again,
 * - `<instanceId>.<name>`: an action the instance's plugin offers (see [HasActions]), e.g. `photos.skip`.
 */
class DeviceActions(
    private val instances: InstanceManager,
    private val updater: AppUpdater,
    private val platform: PlatformServices,
) {
    private val finished = MutableSharedFlow<ActionResult>(extraBufferCapacity = 16)

    /** Every action result, for the events stream. */
    val results: SharedFlow<ActionResult> = finished

    fun list(): List<ActionInfo> {
        val result = mutableListOf(
            ActionInfo("device.reload", "Restart every widget and source"),
            ActionInfo("device.refresh", "Fetch fresh data for everything now"),
            ActionInfo("update.check", "Look for a new version of Sekund now"),
            ActionInfo("update.install", "Download and install the new version of Sekund, if there is one"),
        )
        if (platform.display != null) {
            result.add(ActionInfo("display.on", "Turn the screen on"))
            result.add(ActionInfo("display.off", "Turn the screen off"))
        }
        if (platform.kiosk != null) {
            result.add(ActionInfo("kiosk.enter", "Lock the device to Sekund"))
            result.add(ActionInfo("kiosk.exit", "Unlock the device from Sekund until it restarts"))
        }
        for (instance in instances.instances.value.values) {
            if (instance is RunningInstance.Ready) {
                result.add(ActionInfo("${instance.id}.refresh", "Fetch fresh data for '${instance.id}' now"))
                for (action in pluginActionsOf(instance)) {
                    result.add(ActionInfo("${instance.id}.${action.name}", action.description))
                }
            }
        }
        return result
    }

    suspend fun run(name: String): ActionResult {
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
            name == "update.check" -> {
                updater.checkNow()
                val problem = updater.status.value.checkProblem
                ActionResult(name, ok = problem == null, message = problem)
            }
            name == "update.install" -> {
                val started = updater.installNow()
                ActionResult(name, ok = started, message = if (started) null else "No update is available")
            }
            name == "display.on" || name == "display.off" -> switchDisplay(name, turnOn = name == "display.on")
            name == "kiosk.enter" || name == "kiosk.exit" -> switchKiosk(name, enter = name == "kiosk.enter")
            name.endsWith(".refresh") -> refreshOne(name, name.removeSuffix(".refresh"))
            else -> runPluginAction(name)
        }
        finished.tryEmit(result)
        return result
    }

    private suspend fun switchDisplay(name: String, turnOn: Boolean): ActionResult {
        val display = platform.display ?: throw ApiProblem.notFound("This device can't turn its screen on and off")
        try {
            if (turnOn) display.turnOn() else display.turnOff()
            return ActionResult(name, ok = true)
        } catch (error: IllegalStateException) {
            return ActionResult(name, ok = false, message = error.message ?: "Failed")
        }
    }

    private fun switchKiosk(name: String, enter: Boolean): ActionResult {
        val kiosk = platform.kiosk ?: throw ApiProblem.notFound("This device has no kiosk mode")
        if (enter) kiosk.enter() else kiosk.exit()
        return ActionResult(name, ok = true)
    }

    /** Runs `<instanceId>.<action>`. Instance ids may contain dots, so the action name is after the last one. */
    private suspend fun runPluginAction(name: String): ActionResult {
        val instanceId = name.substringBeforeLast('.', missingDelimiterValue = "")
        val actionName = name.substringAfterLast('.')
        val instance = instances.instances.value[instanceId] ?: throw ApiProblem.notFound("No action called '$name'")
        val action = pluginActionsOf(instance).firstOrNull { candidate -> candidate.name == actionName }
            ?: throw ApiProblem.notFound("No action called '$name'")
        try {
            action.run()
            return ActionResult(name, ok = true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return ActionResult(name, ok = false, message = error.message ?: "Failed")
        }
    }

    private fun pluginActionsOf(instance: RunningInstance): List<PluginAction> {
        if (instance !is RunningInstance.Ready) return emptyList()
        val value = instance.value
        if (value !is HasActions) return emptyList()
        return value.actions
    }

    private fun refreshOne(name: String, instanceId: String): ActionResult {
        val instance = instances.instances.value[instanceId] ?: throw ApiProblem.notFound("No action called '$name'")
        if (instance !is RunningInstance.Ready) return ActionResult(name, ok = false, message = "'$instanceId' isn't running")
        instance.refreshNow()
        return ActionResult(name, ok = true)
    }
}
