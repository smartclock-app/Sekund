package uk.dnpk.sekund.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.serialization.json.JsonObject
import uk.dnpk.sekund.protocol.settings.FieldDescriptor
import uk.dnpk.sekund.protocol.settings.SettingsDescriptor
import uk.dnpk.sekund.protocol.settings.SettingsIssue
import uk.dnpk.sekund.ui.theme.Sekund

/** One thing a picker field can choose: an account, or another instance. */
data class PickOption(val id: String, val label: String)

/**
 * What the pickers in a form can offer, worked out by the screen that shows the form.
 *
 * @property accountsByProvider connected accounts per provider id, for `account` fields.
 * @property instancesByPoint configured instances per extension point id, for `instanceRefs` fields.
 */
data class FormChoices(
    val accountsByProvider: Map<String, List<PickOption>> = emptyMap(),
    val instancesByPoint: Map<String, List<PickOption>> = emptyMap(),
)

/**
 * A settings form generated from a [SettingsDescriptor]: one editor per field, in order.
 *
 * It edits the settings JSON directly: every change calls [onChange] with a new [JsonObject] (the
 * caller decides when to save). Problems Sekund found in the saved settings ([issues]) are shown
 * under the field they belong to. The same descriptors drive the companion app's forms, so a plugin
 * gets both UIs for free.
 *
 * @param pathPrefix put in front of field keys when matching [issues], for forms nested in a list
 *   (e.g. `"cameras[1]"`).
 */
@Composable
fun SettingsForm(
    descriptor: SettingsDescriptor,
    value: JsonObject,
    onChange: (JsonObject) -> Unit,
    choices: FormChoices,
    issues: List<SettingsIssue> = emptyList(),
    pathPrefix: String = "",
) {
    Column(verticalArrangement = Arrangement.spacedBy(Sekund.theme.spacing)) {
        if (descriptor.fields.isEmpty()) {
            Text("This has no settings.", color = Sekund.theme.colors.onBackground)
        }
        for (field in descriptor.fields) {
            val path = if (pathPrefix.isEmpty()) field.key else "$pathPrefix.${field.key}"
            Column {
                FieldEditor(field, value, onChange, choices, issues, path)
                for (issue in issuesFor(issues, path)) {
                    Text(issue.message, color = Sekund.theme.colors.error)
                }
            }
        }
    }
}

/** Picks the right editor for the field's kind. Adding a field kind means adding a branch here. */
@Composable
private fun FieldEditor(
    field: FieldDescriptor,
    value: JsonObject,
    onChange: (JsonObject) -> Unit,
    choices: FormChoices,
    issues: List<SettingsIssue>,
    path: String,
) {
    when (field) {
        is FieldDescriptor.Text -> TextEditor(field, value, onChange)
        is FieldDescriptor.Number -> NumberEditor(field, value, onChange)
        is FieldDescriptor.Toggle -> ToggleEditor(field, value, onChange)
        is FieldDescriptor.Choice -> ChoiceEditor(field, value, onChange)
        is FieldDescriptor.Duration -> DurationEditor(field, value, onChange)
        is FieldDescriptor.Color -> ColorEditor(field, value, onChange)
        is FieldDescriptor.TextList -> TextListEditor(field, value, onChange)
        is FieldDescriptor.ObjectList -> ObjectListEditor(field, value, onChange, choices, issues, path)
        is FieldDescriptor.Account -> AccountEditor(field, value, onChange, choices.accountsByProvider[field.provider].orEmpty())
        is FieldDescriptor.InstanceRefs -> InstanceRefsEditor(field, value, onChange, choices.instancesByPoint[field.extensionPoint].orEmpty())
    }
}

/**
 * The issues for the field at [path]. Issues inside an object list (`"cameras[0].id"`) are left to the
 * nested form, which shows them next to the right item.
 */
private fun issuesFor(issues: List<SettingsIssue>, path: String): List<SettingsIssue> =
    issues.filter { issue -> issue.path == path }

/** The label shown above a field, with a `*` for required fields. */
internal fun labelOf(field: FieldDescriptor): String = if (field.required) "${field.label} *" else field.label
