package uk.dnpk.sekund.shell

import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.runtime.instances.RunningInstance
import uk.dnpk.sekund.runtime.plugins.PluginRegistry
import uk.dnpk.sekund.sdk.context.Alert

/**
 * Everything the shell needs to draw one frame, gathered in one place.
 *
 * @property instances every running instance, by id.
 * @property alerts alert cards for the top of the sidebar, by owner.
 * @property configProblem why config.json couldn't be read, if it couldn't.
 */
data class ShellState(
    val config: AppConfig,
    val instances: Map<String, RunningInstance>,
    val alerts: Map<String, Alert>,
    val configProblem: String?,
    val registry: PluginRegistry,
) {
    /** The sidebar is hidden when it has nothing to show, and the main zone takes the whole screen. */
    val hasSidebar: Boolean
        get() = config.layout.sidebar.isNotEmpty() || alerts.isNotEmpty() || configProblem != null
}
