package uk.dnpk.sekund.sdk.settings

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import uk.dnpk.sekund.protocol.settings.ChoiceOption
import uk.dnpk.sekund.protocol.settings.FieldDescriptor
import uk.dnpk.sekund.protocol.settings.SettingsIssue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Every kind of setting a plugin can declare. Each class is small and follows the same pattern:
// decode (JSON -> value), defaultJson (the default as JSON), describe (-> settings screen field).
// Create them through SettingsSchema (text(...), toggle(...), ...), not directly.

/** Reads a JSON string, or returns null if [json] isn't one. */
private fun JsonElement.stringOrNull(): String? {
    if (this !is JsonPrimitive) return null
    if (!this.isString) return null
    return this.content
}

/** A piece of text. */
class TextSetting(
    key: String,
    label: String,
    description: String?,
    default: String,
    required: Boolean,
    private val placeholder: String?,
    private val multiline: Boolean,
) : Setting<String>(key, label, description, default, required) {

    override fun decode(json: JsonElement, path: String, issues: MutableList<SettingsIssue>): String {
        val text = json.stringOrNull() ?: return invalid(path, issues, "Expected text")
        return text
    }

    override fun defaultJson(): JsonElement = JsonPrimitive(default)

    override fun describe(): FieldDescriptor = FieldDescriptor.Text(
        key = key,
        label = label,
        description = description,
        required = required,
        default = default,
        placeholder = placeholder,
        multiline = multiline,
    )
}

/** A whole number, optionally between [min] and [max]. */
class NumberSetting(
    key: String,
    label: String,
    description: String?,
    default: Int,
    required: Boolean,
    private val min: Int?,
    private val max: Int?,
) : Setting<Int>(key, label, description, default, required) {

    override fun decode(json: JsonElement, path: String, issues: MutableList<SettingsIssue>): Int {
        if (json !is JsonPrimitive || json.isString) return invalid(path, issues, "Expected a whole number")
        val number = json.intOrNull ?: return invalid(path, issues, "Expected a whole number")
        if (min != null && number < min) return invalid(path, issues, "Must be at least $min")
        if (max != null && number > max) return invalid(path, issues, "Must be at most $max")
        return number
    }

    override fun defaultJson(): JsonElement = JsonPrimitive(default)

    override fun describe(): FieldDescriptor = FieldDescriptor.Number(
        key = key,
        label = label,
        description = description,
        required = required,
        default = default,
        min = min,
        max = max,
    )
}

/** On or off. */
class ToggleSetting(
    key: String,
    label: String,
    description: String?,
    default: Boolean,
) : Setting<Boolean>(key, label, description, default, required = false) {

    override fun decode(json: JsonElement, path: String, issues: MutableList<SettingsIssue>): Boolean {
        if (json !is JsonPrimitive || json.isString) return invalid(path, issues, "Expected true or false")
        val value = json.booleanOrNull ?: return invalid(path, issues, "Expected true or false")
        return value
    }

    override fun defaultJson(): JsonElement = JsonPrimitive(default)

    override fun describe(): FieldDescriptor = FieldDescriptor.Toggle(
        key = key,
        label = label,
        description = description,
        default = default,
    )
}

/**
 * One of a fixed set of enum values. Stored in JSON as the enum constant's name (e.g. `"Metric"`);
 * reading ignores upper/lower case, so `"metric"` works too.
 */
class ChoiceSetting<E : Enum<E>>(
    key: String,
    label: String,
    description: String?,
    default: E,
    private val options: List<E>,
    private val optionLabel: (E) -> String,
) : Setting<E>(key, label, description, default, required = false) {

    override fun decode(json: JsonElement, path: String, issues: MutableList<SettingsIssue>): E {
        val text = json.stringOrNull() ?: return invalid(path, issues, "Expected one of: ${optionNames()}")
        for (option in options) {
            if (option.name.equals(text, ignoreCase = true)) return option
        }
        return invalid(path, issues, "'$text' isn't valid. Expected one of: ${optionNames()}")
    }

    override fun defaultJson(): JsonElement = JsonPrimitive(default.name)

    override fun describe(): FieldDescriptor = FieldDescriptor.Choice(
        key = key,
        label = label,
        description = description,
        options = options.map { option -> ChoiceOption(value = option.name, label = optionLabel(option)) },
        default = default.name,
    )

    private fun optionNames(): String = options.joinToString(", ") { option -> option.name }
}

/**
 * One of a fixed list of text values, for when the options aren't known at compile time (declarative
 * widgets). Compiled plugins should prefer [ChoiceSetting] with an enum. Matching ignores case.
 */
class TextChoiceSetting(
    key: String,
    label: String,
    description: String?,
    default: String,
    private val options: List<ChoiceOption>,
) : Setting<String>(key, label, description, default, required = false) {

    override fun decode(json: JsonElement, path: String, issues: MutableList<SettingsIssue>): String {
        val text = json.stringOrNull() ?: return invalid(path, issues, "Expected one of: ${optionNames()}")
        for (option in options) {
            if (option.value.equals(text, ignoreCase = true)) return option.value
        }
        return invalid(path, issues, "'$text' isn't valid. Expected one of: ${optionNames()}")
    }

    override fun defaultJson(): JsonElement = JsonPrimitive(default)

    override fun describe(): FieldDescriptor = FieldDescriptor.Choice(
        key = key,
        label = label,
        description = description,
        options = options,
        default = default,
    )

    private fun optionNames(): String = options.joinToString(", ") { option -> option.value }
}

/**
 * A length of time. Accepts readable strings like `"15m"`, `"1h 30m"`, `"45s"`, or a plain number of seconds.
 * Always written back as a readable string.
 */
class DurationSetting(
    key: String,
    label: String,
    description: String?,
    default: Duration,
    private val min: Duration?,
) : Setting<Duration>(key, label, description, default, required = false) {

    override fun decode(json: JsonElement, path: String, issues: MutableList<SettingsIssue>): Duration {
        val duration = parse(json) ?: return invalid(path, issues, "Expected a duration such as \"15m\" or \"1h 30m\"")
        if (min != null && duration < min) return invalid(path, issues, "Must be at least $min")
        return duration
    }

    private fun parse(json: JsonElement): Duration? {
        if (json !is JsonPrimitive) return null
        if (!json.isString) {
            val seconds = json.longOrNull ?: return null
            return seconds.seconds
        }
        return Duration.parseOrNull(json.content)
    }

    override fun defaultJson(): JsonElement = JsonPrimitive(default.toString())

    override fun describe(): FieldDescriptor = FieldDescriptor.Duration(
        key = key,
        label = label,
        description = description,
        default = default.toString(),
        min = min?.toString(),
    )
}

/** A colour, stored as `"#RRGGBB"` or `"#AARRGGBB"`. */
class ColorSetting(
    key: String,
    label: String,
    description: String?,
    default: Color,
) : Setting<Color>(key, label, description, default, required = false) {

    override fun decode(json: JsonElement, path: String, issues: MutableList<SettingsIssue>): Color {
        val text = json.stringOrNull() ?: return invalid(path, issues, "Expected a colour such as \"#FF8800\"")
        val color = ColorHex.parse(text) ?: return invalid(path, issues, "'$text' isn't a colour like \"#FF8800\"")
        return color
    }

    override fun defaultJson(): JsonElement = JsonPrimitive(ColorHex.format(default))

    override fun describe(): FieldDescriptor = FieldDescriptor.Color(
        key = key,
        label = label,
        description = description,
        default = ColorHex.format(default),
    )
}

/** A list of strings. Non-string entries are skipped and reported. */
class TextListSetting(
    key: String,
    label: String,
    description: String?,
    default: List<String>,
) : Setting<List<String>>(key, label, description, default, required = false) {

    override fun decode(json: JsonElement, path: String, issues: MutableList<SettingsIssue>): List<String> {
        if (json !is JsonArray) return invalid(path, issues, "Expected a list of text")
        val result = mutableListOf<String>()
        for ((index, item) in json.withIndex()) {
            val text = item.stringOrNull()
            if (text == null) {
                issues.add(SettingsIssue("$path[$index]", "Expected text, skipped"))
            } else {
                result.add(text)
            }
        }
        return result
    }

    override fun defaultJson(): JsonElement = JsonArray(default.map { text -> JsonPrimitive(text) })

    override fun describe(): FieldDescriptor = FieldDescriptor.TextList(
        key = key,
        label = label,
        description = description,
        default = default,
    )
}

/**
 * A list of objects that all follow [itemSchema], e.g. a list of cameras.
 * Each item is read with the item schema, so a bad field in one item only affects that field.
 */
class ObjectListSetting<I>(
    key: String,
    label: String,
    description: String?,
    private val itemSchema: SettingsSchema<I>,
) : Setting<List<I>>(key, label, description, default = emptyList(), required = false) {

    override fun decode(json: JsonElement, path: String, issues: MutableList<SettingsIssue>): List<I> {
        if (json !is JsonArray) return invalid(path, issues, "Expected a list")
        val result = mutableListOf<I>()
        for ((index, item) in json.withIndex()) {
            val itemPath = "$path[$index]"
            if (item !is JsonObject) {
                issues.add(SettingsIssue(itemPath, "Expected an object, skipped"))
                continue
            }
            val decoded = itemSchema.decode(item, pathPrefix = itemPath)
            issues.addAll(decoded.issues)
            result.add(decoded.value)
        }
        return result
    }

    override fun defaultJson(): JsonElement = JsonArray(emptyList())

    override fun describe(): FieldDescriptor = FieldDescriptor.ObjectList(
        key = key,
        label = label,
        description = description,
        item = itemSchema.describe(),
    )
}

/**
 * A reference to a connected account in the auth registry, limited to one [provider] (e.g. `"google"`).
 * Stored as the account id. The value is null until someone picks an account.
 */
class AccountSetting(
    key: String,
    label: String,
    description: String?,
    required: Boolean,
    private val provider: String,
) : Setting<AccountRef?>(key, label, description, default = null, required = required) {

    override fun decode(json: JsonElement, path: String, issues: MutableList<SettingsIssue>): AccountRef? {
        if (json is JsonNull) return null
        val id = json.stringOrNull() ?: return invalid(path, issues, "Expected an account id")
        return AccountRef(provider = provider, accountId = id)
    }

    override fun defaultJson(): JsonElement = JsonNull

    override fun describe(): FieldDescriptor = FieldDescriptor.Account(
        key = key,
        label = label,
        description = description,
        required = required,
        provider = provider,
    )
}

/**
 * References to other instances that contribute to [extensionPoint], e.g. the calendar sources a calendar
 * widget shows. Stored as a list of instance ids.
 */
class InstanceRefsSetting(
    key: String,
    label: String,
    description: String?,
    private val extensionPoint: String,
) : Setting<List<String>>(key, label, description, default = emptyList(), required = false) {

    private val asTextList = TextListSetting(key, label, description, default = emptyList())

    override fun decode(json: JsonElement, path: String, issues: MutableList<SettingsIssue>): List<String> =
        asTextList.decode(json, path, issues)

    override fun defaultJson(): JsonElement = JsonArray(emptyList())

    override fun describe(): FieldDescriptor = FieldDescriptor.InstanceRefs(
        key = key,
        label = label,
        description = description,
        extensionPoint = extensionPoint,
    )
}
