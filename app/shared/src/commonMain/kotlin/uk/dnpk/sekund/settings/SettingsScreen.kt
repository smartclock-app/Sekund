package uk.dnpk.sekund.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uk.dnpk.sekund.runtime.AppGraph
import uk.dnpk.sekund.runtime.config.AppConfig
import uk.dnpk.sekund.runtime.instances.RunningInstance
import uk.dnpk.sekund.ui.components.ErrorCard
import uk.dnpk.sekund.ui.components.SekundCard
import uk.dnpk.sekund.ui.theme.Sekund

/** Which page of the settings screen is showing. */
private sealed class SettingsPage {
    data object Home : SettingsPage()

    data object Add : SettingsPage()

    data class Edit(val instanceId: String) : SettingsPage()
}

/**
 * Sekund's settings, opened from the long-press menu. Everything here is generated: plugin settings come
 * from their descriptors, so a new plugin needs no settings UI code.
 *
 * Every change is saved to config.json straight away (or when "Save" is pressed in an instance's form),
 * and the screen behind updates live.
 */
@Composable
fun SettingsScreen(graph: AppGraph, onClose: () -> Unit) {
    var page by remember { mutableStateOf<SettingsPage>(SettingsPage.Home) }
    var saveProblem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val colors = Sekund.theme.colors

    /** Applies [change] to the config and saves it. Shows why if it couldn't be saved. */
    val edit: (change: (AppConfig) -> AppConfig) -> Unit = { change ->
        scope.launch {
            try {
                val saved = graph.config.update(change)
                saveProblem = if (saved) null else "Not saved: config.json couldn't be read. Fix or delete the file first."
            } catch (refused: IllegalArgumentException) {
                // ConfigEdits refuses impossible changes (e.g. a duplicate id) with this exception.
                saveProblem = refused.message
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sekund.theme.spacing * 2),
            verticalArrangement = Arrangement.spacedBy(Sekund.theme.spacing),
        ) {
            val problem = saveProblem
            if (problem != null) ErrorCard(title = "Couldn't save", message = problem)

            when (val current = page) {
                SettingsPage.Home -> SettingsHome(
                    graph = graph,
                    edit = edit,
                    onAdd = { page = SettingsPage.Add },
                    onEdit = { instanceId -> page = SettingsPage.Edit(instanceId) },
                )
                SettingsPage.Add -> AddInstancePage(
                    graph = graph,
                    edit = edit,
                    onAdded = { instanceId -> page = SettingsPage.Edit(instanceId) },
                    onBack = { page = SettingsPage.Home },
                )
                is SettingsPage.Edit -> InstanceEditor(
                    graph = graph,
                    instanceId = current.instanceId,
                    edit = edit,
                    onBack = { page = SettingsPage.Home },
                )
            }
        }
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
            Text("Close")
        }
    }
}

/** The first page: general settings, the layout, and every configured instance. */
@Composable
private fun SettingsHome(
    graph: AppGraph,
    edit: ((AppConfig) -> AppConfig) -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val config by graph.config.config.collectAsState()
    val instances by graph.instances.instances.collectAsState()
    val loadProblem by graph.config.loadProblem.collectAsState()

    PageTitle("Settings")
    val problem = loadProblem
    if (problem != null) ErrorCard(title = "config.json has a problem", message = problem)
    for (registryProblem in graph.registry.problems) {
        ErrorCard(title = "Plugin problem", message = registryProblem)
    }

    SectionTitle("General")
    GeneralSection(config = config, instances = instances, registry = graph.registry, edit = edit)

    SectionTitle("Layout")
    LayoutSection(config = config, instances = instances, registry = graph.registry, edit = edit)

    SectionTitle("Widgets and sources")
    for (instance in instances.values) {
        InstanceRow(instance = instance, typeName = graph.registry.contribution(instance.config.type)?.name, onEdit = { onEdit(instance.id) })
    }
    OutlinedButton(onClick = onAdd) { Text("Add…") }

    SectionTitle("Files")
    Text("Config folder: ${graph.paths.configDir}", color = Sekund.theme.colors.onBackground)
    Text("Logs: ${graph.paths.logDir}", color = Sekund.theme.colors.onBackground)
}

@Composable
private fun InstanceRow(instance: RunningInstance, typeName: String?, onEdit: () -> Unit) {
    val colors = Sekund.theme.colors
    SekundCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(instance.id, color = colors.onCard, fontWeight = FontWeight.Bold)
                Text(typeName ?: instance.config.type, color = colors.onCardMuted)
                when (instance) {
                    is RunningInstance.Failed -> Text(instance.reason, color = colors.error)
                    is RunningInstance.Ready -> {
                        if (instance.issues.isNotEmpty()) {
                            Text("${instance.issues.size} setting(s) need attention", color = colors.warning)
                        }
                    }
                }
            }
            TextButton(onClick = onEdit) { Text("Edit") }
        }
    }
}

@Composable
internal fun PageTitle(text: String) {
    Text(text, color = Sekund.theme.colors.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, color = Sekund.theme.colors.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
}
