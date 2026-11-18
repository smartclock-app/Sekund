package uk.dnpk.sekund.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import uk.dnpk.sekund.runtime.AppGraph
import uk.dnpk.sekund.runtime.config.AppConfig
import uk.dnpk.sekund.runtime.config.ConfigEdits
import uk.dnpk.sekund.runtime.config.InstanceConfig
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.widget.WidgetLocation
import uk.dnpk.sekund.ui.components.SekundCard
import uk.dnpk.sekund.ui.theme.Sekund

/**
 * Lists everything the installed plugins can provide, grouped by kind (widgets, clock faces, calendar
 * sources...). Picking one adds an instance with default settings and opens its editor.
 *
 * New widgets are placed in the sidebar if they're allowed there, otherwise in the main square.
 */
@Composable
internal fun AddInstancePage(
    graph: AppGraph,
    edit: ((AppConfig) -> AppConfig) -> Unit,
    onAdded: (instanceId: String) -> Unit,
    onBack: () -> Unit,
) {
    val config by graph.config.config.collectAsState()
    val colors = Sekund.theme.colors
    val contributions = graph.registry.allContributions()

    PageTitle("Add")
    OutlinedButton(onClick = onBack) { Text("Back") }

    // Group by extension point, keeping the order in which points first appear.
    val points = contributions.map { contribution -> contribution.point }.distinct()
    for (point in points) {
        SectionTitle(point.name)
        for (contribution in contributions.filter { candidate -> candidate.point == point }) {
            val unavailable = graph.registry.unavailableReason(contribution.type, graph.auth.providers.value.keys)
            SekundCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(contribution.name, color = colors.onCard, fontWeight = FontWeight.Bold)
                        val description = contribution.description
                        if (description != null) Text(description, color = colors.onCardMuted)
                        if (unavailable != null) Text(unavailable, color = colors.warning)
                    }
                    TextButton(onClick = {
                        val id = ConfigEdits.newInstanceId(config, contribution.type)
                        val instance = InstanceConfig(id = id, type = contribution.type, settings = contribution.settings.defaults())
                        edit { current -> ConfigEdits.addInstance(current, instance, placementFor(contribution)) }
                        onAdded(id)
                    }) { Text("Add") }
                }
            }
        }
    }
}

/** Where a newly added contribution goes in the layout: widgets get placed, everything else doesn't. */
private fun placementFor(contribution: Contribution<*, *>): WidgetLocation? {
    if (contribution.point != ExtensionPoints.Widgets) return null
    if (WidgetLocation.Sidebar in contribution.widgetLocations) return WidgetLocation.Sidebar
    return WidgetLocation.Main
}
