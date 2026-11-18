package uk.dnpk.sekund.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import uk.dnpk.sekund.protocol.settings.FieldDescriptor
import uk.dnpk.sekund.protocol.settings.SettingsDefaults
import uk.dnpk.sekund.protocol.settings.SettingsIssue
import uk.dnpk.sekund.sdk.settings.ColorHex
import uk.dnpk.sekund.ui.components.SekundCard
import uk.dnpk.sekund.ui.theme.Sekund
import kotlin.time.Duration

// One small editor per field kind. Each reads its value from the settings JSON and reports changes
// by calling onChange with an updated copy. SettingsForm decides which editor a field gets.

@Composable
internal fun TextEditor(field: FieldDescriptor.Text, value: JsonObject, onChange: (JsonObject) -> Unit) {
    OutlinedTextField(
        value = JsonFields.text(value, field.key, field.default),
        onValueChange = { text -> onChange(JsonFields.withText(value, field.key, text)) },
        label = { Text(labelOf(field)) },
        placeholder = { Text(field.placeholder ?: "") },
        supportingText = helpText(field),
        singleLine = !field.multiline,
        minLines = if (field.multiline) 3 else 1,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A whole number. What's typed is kept as text while editing (so the box can be empty, or hold "-"),
 * and only written to the settings when it's a valid number in range.
 */
@Composable
internal fun NumberEditor(field: FieldDescriptor.Number, value: JsonObject, onChange: (JsonObject) -> Unit) {
    val saved = JsonFields.number(value, field.key, field.default)
    var typed by remember(field.key) { mutableStateOf(saved.toString()) }
    val problem = numberProblem(typed, field)

    OutlinedTextField(
        value = typed,
        onValueChange = { text ->
            typed = text
            val number = text.trim().toIntOrNull()
            if (number != null && numberProblem(text, field) == null) {
                onChange(JsonFields.withNumber(value, field.key, number))
            }
        },
        label = { Text(labelOf(field)) },
        isError = problem != null,
        supportingText = messageOr(problem, field.description),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun numberProblem(text: String, field: FieldDescriptor.Number): String? {
    val number = text.trim().toIntOrNull() ?: return "Enter a whole number"
    val min = field.min
    val max = field.max
    if (min != null && number < min) return "Must be at least $min"
    if (max != null && number > max) return "Must be at most $max"
    return null
}

@Composable
internal fun ToggleEditor(field: FieldDescriptor.Toggle, value: JsonObject, onChange: (JsonObject) -> Unit) {
    val checked = JsonFields.toggle(value, field.key, field.default)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(labelOf(field), color = Sekund.theme.colors.onBackground)
            val description = field.description
            if (description != null) Text(description, color = Sekund.theme.colors.onBackground.copy(alpha = 0.7f))
        }
        Switch(checked = checked, onCheckedChange = { on -> onChange(JsonFields.withToggle(value, field.key, on)) })
    }
}

/** One chip per option; the selected one is highlighted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChoiceEditor(field: FieldDescriptor.Choice, value: JsonObject, onChange: (JsonObject) -> Unit) {
    val selected = JsonFields.text(value, field.key, field.default)
    Column {
        FieldTitle(labelOf(field), field.description)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in field.options) {
                // Stored values are matched ignoring case, the same way plugins decode them.
                val isSelected = option.value.equals(selected, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onChange(JsonFields.withText(value, field.key, option.value)) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

/** A duration typed as text, e.g. `15m` or `1h 30m`. Saved only once it reads as a duration. */
@Composable
internal fun DurationEditor(field: FieldDescriptor.Duration, value: JsonObject, onChange: (JsonObject) -> Unit) {
    val saved = JsonFields.text(value, field.key, field.default)
    var typed by remember(field.key) { mutableStateOf(saved) }
    val problem = durationProblem(typed, field.min)

    OutlinedTextField(
        value = typed,
        onValueChange = { text ->
            typed = text
            if (durationProblem(text, field.min) == null) onChange(JsonFields.withText(value, field.key, text.trim()))
        },
        label = { Text(labelOf(field)) },
        placeholder = { Text("e.g. 15m, 1h 30m") },
        isError = problem != null,
        supportingText = messageOr(problem, field.description),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Why [text] isn't an acceptable duration, or null if it is. Shared with the refresh interval box. */
internal fun durationProblem(text: String, min: String?): String? {
    val duration = Duration.parseOrNull(text.trim()) ?: return "Use a duration such as 15m or 1h 30m"
    val minimum = if (min == null) null else Duration.parseOrNull(min)
    if (minimum != null && duration < minimum) return "Must be at least $minimum"
    return null
}

/** A colour typed as `#RRGGBB`, with a swatch showing it. */
@Composable
internal fun ColorEditor(field: FieldDescriptor.Color, value: JsonObject, onChange: (JsonObject) -> Unit) {
    val saved = JsonFields.text(value, field.key, field.default)
    var typed by remember(field.key) { mutableStateOf(saved) }
    val parsed = ColorHex.parse(typed.trim())

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = typed,
            onValueChange = { text ->
                typed = text
                if (ColorHex.parse(text.trim()) != null) onChange(JsonFields.withText(value, field.key, text.trim().uppercase()))
            },
            label = { Text(labelOf(field)) },
            placeholder = { Text("#FF8800") },
            isError = parsed == null,
            supportingText = messageOr(if (parsed == null) "Use #RRGGBB" else null, field.description),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        if (parsed != null) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(parsed))
        }
    }
}

/** A list of text, edited as one entry per line. Empty lines are dropped. */
@Composable
internal fun TextListEditor(field: FieldDescriptor.TextList, value: JsonObject, onChange: (JsonObject) -> Unit) {
    val saved = JsonFields.textList(value, field.key, field.default)
    var typed by remember(field.key) { mutableStateOf(saved.joinToString("\n")) }

    OutlinedTextField(
        value = typed,
        onValueChange = { text ->
            typed = text
            val lines = text.split('\n').map { line -> line.trim() }.filter { line -> line.isNotEmpty() }
            onChange(JsonFields.withTextList(value, field.key, lines))
        },
        label = { Text(labelOf(field)) },
        supportingText = messageOr("One per line", field.description),
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A list of small objects, each edited with a nested [SettingsForm]. */
@Composable
internal fun ObjectListEditor(
    field: FieldDescriptor.ObjectList,
    value: JsonObject,
    onChange: (JsonObject) -> Unit,
    choices: FormChoices,
    issues: List<SettingsIssue>,
    path: String,
) {
    val items = JsonFields.objectList(value, field.key)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldTitle(labelOf(field), field.description)
        for ((index, item) in items.withIndex()) {
            SekundCard {
                Column {
                    SettingsForm(
                        descriptor = field.item,
                        value = item,
                        onChange = { changed -> onChange(JsonFields.withObjectList(value, field.key, replaced(items, index, changed))) },
                        choices = choices,
                        issues = issues,
                        pathPrefix = "$path[$index]",
                    )
                    TextButton(onClick = { onChange(JsonFields.withObjectList(value, field.key, removed(items, index))) }) {
                        Text("Remove")
                    }
                }
            }
        }
        OutlinedButton(onClick = { onChange(JsonFields.withObjectList(value, field.key, items + SettingsDefaults.of(field.item))) }) {
            Text("Add")
        }
    }
}

/** Pick one connected account of the field's provider, or none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountEditor(field: FieldDescriptor.Account, value: JsonObject, onChange: (JsonObject) -> Unit, accounts: List<PickOption>) {
    val selected = JsonFields.optionalText(value, field.key)
    Column {
        FieldTitle(labelOf(field), field.description)
        if (accounts.isEmpty()) {
            Text("No ${field.provider} account is connected yet. Connect one in Accounts first.", color = Sekund.theme.colors.warning)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (account in accounts) {
                FilterChip(
                    selected = account.id == selected,
                    onClick = { onChange(JsonFields.withOptionalText(value, field.key, account.id)) },
                    label = { Text(account.label) },
                )
            }
            if (selected != null && accounts.none { account -> account.id == selected }) {
                // Saved, but that account has since been removed.
                FilterChip(selected = true, onClick = {}, label = { Text("$selected (missing)") })
            }
        }
    }
}

/** Tick any number of instances that contribute to the field's extension point. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InstanceRefsEditor(field: FieldDescriptor.InstanceRefs, value: JsonObject, onChange: (JsonObject) -> Unit, instances: List<PickOption>) {
    val selected = JsonFields.textList(value, field.key, emptyList())
    Column {
        FieldTitle(labelOf(field), field.description)
        if (instances.isEmpty()) {
            Text("Nothing to choose yet: add one first.", color = Sekund.theme.colors.warning)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (instance in instances) {
                val isSelected = instance.id in selected
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val updated = if (isSelected) selected - instance.id else selected + instance.id
                        onChange(JsonFields.withTextList(value, field.key, updated))
                    },
                    label = { Text(instance.label) },
                )
            }
        }
    }
}

// ---- Small shared pieces ------------------------------------------------------------------------

@Composable
private fun FieldTitle(label: String, description: String?) {
    Text(label, color = Sekund.theme.colors.onBackground, fontWeight = FontWeight.SemiBold)
    if (description != null) {
        Text(description, color = Sekund.theme.colors.onBackground.copy(alpha = 0.7f))
    }
}

/** The help line under a text box, or null for none. */
private fun helpText(field: FieldDescriptor): (@Composable () -> Unit)? {
    val description = field.description ?: return null
    return { Text(description) }
}

/** An error message if there is one, otherwise the help text, otherwise nothing. */
private fun messageOr(problem: String?, help: String?): (@Composable () -> Unit)? {
    val message = problem ?: help ?: return null
    return { Text(message) }
}

private fun replaced(items: List<JsonObject>, index: Int, item: JsonObject): List<JsonObject> {
    val copy = items.toMutableList()
    copy[index] = item
    return copy
}

private fun removed(items: List<JsonObject>, index: Int): List<JsonObject> {
    val copy = items.toMutableList()
    copy.removeAt(index)
    return copy
}
