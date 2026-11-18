package uk.dnpk.sekund.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.runtime.config.ConfigEdits
import uk.dnpk.sekund.runtime.instances.RunningInstance
import uk.dnpk.sekund.runtime.plugins.PluginRegistry
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.widget.WidgetLocation
import uk.dnpk.sekund.ui.components.SekundCard
import uk.dnpk.sekund.ui.theme.Sekund

/**
 * Which widgets appear in the main square and the sidebar, in order. Each placed widget can be moved
 * up, down or taken out; widgets that are allowed somewhere but not placed there are offered as chips.
 */
@Composable
internal fun LayoutSection(
    config: AppConfig,
    instances: Map<String, RunningInstance>,
    registry: PluginRegistry,
    edit: ((AppConfig) -> AppConfig) -> Unit,
) {
    for (location in WidgetLocation.entries) {
        LocationEditor(location, config, instances, registry, edit)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocationEditor(
    location: WidgetLocation,
    config: AppConfig,
    instances: Map<String, RunningInstance>,
    registry: PluginRegistry,
    edit: ((AppConfig) -> AppConfig) -> Unit,
) {
    val colors = Sekund.theme.colors
    val placed = ConfigEdits.idsAt(config.layout, location)

    SekundCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(locationName(location), color = colors.onCard, fontWeight = FontWeight.Bold)
            if (placed.isEmpty()) Text("Nothing here yet.", color = colors.onCardMuted)

            for ((index, instanceId) in placed.withIndex()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(instanceId, color = colors.onCard, modifier = Modifier.weight(1f))
                    TextButton(enabled = index > 0, onClick = { edit { current -> ConfigEdits.move(current, location, instanceId, -1) } }) { Text("Up") }
                    TextButton(enabled = index < placed.size - 1, onClick = { edit { current -> ConfigEdits.move(current, location, instanceId, 1) } }) { Text("Down") }
                    TextButton(onClick = { edit { current -> ConfigEdits.removeFromLayout(current, location, instanceId) } }) { Text("Remove") }
                }
            }

            val placeable = instances.values.filter { instance ->
                val contribution = registry.contribution(instance.config.type)
                contribution != null &&
                    contribution.point == ExtensionPoints.Widgets &&
                    location in contribution.widgetLocations &&
                    instance.id !in placed
            }
            if (placeable.isNotEmpty()) {
                Text("Add to the ${locationName(location).lowercase()}:", color = colors.onCardMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (instance in placeable) {
                        AssistChip(
                            onClick = { edit { current -> ConfigEdits.addToLayout(current, location, instance.id) } },
                            label = { Text(instance.id) },
                        )
                    }
                }
            }
        }
    }
}

internal fun locationName(location: WidgetLocation): String = when (location) {
    WidgetLocation.Main -> "Main square"
    WidgetLocation.Sidebar -> "Sidebar"
}
