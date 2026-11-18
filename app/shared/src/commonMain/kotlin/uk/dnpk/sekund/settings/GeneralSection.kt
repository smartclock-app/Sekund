package uk.dnpk.sekund.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.protocol.config.Orientation
import uk.dnpk.sekund.runtime.instances.RunningInstance
import uk.dnpk.sekund.runtime.plugins.PluginRegistry
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.ui.theme.Sekund

/** Orientation, how times are shown, which clock face fills the main square, and updates. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GeneralSection(
    config: AppConfig,
    instances: Map<String, RunningInstance>,
    registry: PluginRegistry,
    edit: ((AppConfig) -> AppConfig) -> Unit,
) {
    val colors = Sekund.theme.colors
    Column {
        Text("Orientation", color = colors.onBackground)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (orientation in Orientation.entries) {
                FilterChip(
                    selected = config.orientation == orientation,
                    onClick = { edit { current -> current.copy(orientation = orientation) } },
                    label = { Text(orientation.name) },
                )
            }
        }
    }

    SwitchRow("24-hour clock", config.clock.use24Hour) { on ->
        edit { current -> current.copy(clock = current.clock.copy(use24Hour = on)) }
    }
    SwitchRow("Show seconds", config.clock.showSeconds) { on ->
        edit { current -> current.copy(clock = current.clock.copy(showSeconds = on)) }
    }

    // Any configured instance whose type is a clock face can fill the main square.
    val clockFaces = instances.values.filter { instance -> registry.contribution(instance.config.type)?.point == ExtensionPoints.ClockFaces }
    Column {
        Text("Clock face", color = colors.onBackground)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (face in clockFaces) {
                FilterChip(
                    selected = config.clockFace == face.id,
                    onClick = { edit { current -> current.copy(clockFace = face.id) } },
                    label = { Text(face.id) },
                )
            }
        }
    }

    // Sekund always checks for new versions; this only decides whether to wait for a tap.
    SwitchRow("Install updates by themselves", config.updates.installAutomatically) { on ->
        edit { current -> current.copy(updates = current.updates.copy(installAutomatically = on)) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Sekund.theme.colors.onBackground, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
