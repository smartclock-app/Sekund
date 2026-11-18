package uk.dnpk.sekund.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.serialization.json.JsonObject
import uk.dnpk.sekund.runtime.AppGraph
import uk.dnpk.sekund.runtime.auth.ConnectedAccount
import uk.dnpk.sekund.runtime.config.AppConfig
import uk.dnpk.sekund.runtime.config.ConfigEdits
import uk.dnpk.sekund.runtime.instances.RunningInstance
import uk.dnpk.sekund.runtime.plugins.PluginRegistry
import uk.dnpk.sekund.ui.components.ErrorCard
import uk.dnpk.sekund.ui.theme.Sekund

/**
 * Edits one instance's settings with a form generated from its plugin's settings descriptor.
 *
 * Changes are kept in a draft until "Save", because saving recreates the instance (and so reloads its
 * data). The refresh interval is a core setting every instance has.
 */
@Composable
internal fun InstanceEditor(
    graph: AppGraph,
    instanceId: String,
    edit: ((AppConfig) -> AppConfig) -> Unit,
    onBack: () -> Unit,
) {
    val config by graph.config.config.collectAsState()
    val instances by graph.instances.instances.collectAsState()
    val accounts by graph.auth.accounts.collectAsState()
    val colors = Sekund.theme.colors

    val saved = config.instances.firstOrNull { instance -> instance.id == instanceId }
    if (saved == null) {
        Text("'$instanceId' no longer exists.", color = colors.onBackground)
        OutlinedButton(onClick = onBack) { Text("Back") }
        return
    }

    val contribution = graph.registry.contribution(saved.type)
    // Start from the saved settings, with defaults filled in for anything missing, so every field shows a value.
    val defaults = contribution?.settings?.defaults() ?: JsonObject(emptyMap())
    var draft by remember(instanceId) { mutableStateOf(JsonObject(defaults + saved.settings)) }
    var interval by remember(instanceId) { mutableStateOf(saved.refreshInterval ?: "") }
    var confirmDelete by remember(instanceId) { mutableStateOf(false) }

    PageTitle(instanceId)
    Text(contribution?.name ?: saved.type, color = colors.onBackground)

    val running = instances[instanceId]
    if (running is RunningInstance.Failed) {
        ErrorCard(title = "Not running", message = running.reason)
    }

    if (contribution == null) {
        Text("No installed plugin provides '${saved.type}', so its settings can't be shown.", color = colors.warning)
    } else {
        SettingsForm(
            descriptor = contribution.settings.describe(),
            value = draft,
            onChange = { changed -> draft = changed },
            choices = formChoices(accounts, instances, graph.registry),
            issues = if (running is RunningInstance.Ready) running.issues else emptyList(),
        )
    }

    val intervalProblem = if (interval.isBlank()) null else durationProblem(interval, min = null)
    OutlinedTextField(
        value = interval,
        onValueChange = { text -> interval = text },
        label = { Text("Refresh every") },
        placeholder = { Text("Plugin default") },
        isError = intervalProblem != null,
        supportingText = { Text(intervalProblem ?: "Leave empty for the plugin's default, e.g. 30m") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(Sekund.theme.spacing)) {
        Button(
            enabled = intervalProblem == null,
            onClick = {
                val newInterval = if (interval.isBlank()) null else interval.trim()
                edit { current -> ConfigEdits.setSettings(current, instanceId, draft, newInterval) }
                onBack()
            },
        ) { Text("Save") }
        OutlinedButton(onClick = onBack) { Text("Cancel") }
        if (confirmDelete) {
            Button(onClick = {
                edit { current -> ConfigEdits.removeInstance(current, instanceId) }
                onBack()
            }) { Text("Really delete") }
        } else {
            TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
        }
    }
}

/** What the form's account and instance pickers can choose from. */
internal fun formChoices(
    accounts: List<ConnectedAccount>,
    instances: Map<String, RunningInstance>,
    registry: PluginRegistry,
): FormChoices {
    val accountsByProvider = mutableMapOf<String, MutableList<PickOption>>()
    for (account in accounts) {
        accountsByProvider.getOrPut(account.provider) { mutableListOf() }.add(PickOption(account.id, account.label))
    }

    val instancesByPoint = mutableMapOf<String, MutableList<PickOption>>()
    for (instance in instances.values) {
        val point = registry.contribution(instance.config.type)?.point ?: continue
        instancesByPoint.getOrPut(point.id) { mutableListOf() }.add(PickOption(instance.id, instance.id))
    }
    return FormChoices(accountsByProvider, instancesByPoint)
}
