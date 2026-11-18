package uk.dnpk.sekund.sdk.settings

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import uk.dnpk.sekund.protocol.settings.FieldDescriptor
import uk.dnpk.sekund.protocol.settings.SettingsDescriptor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * A [SettingsSchema] made from a [SettingsDescriptor] at runtime, for plugins whose settings are only
 * known as data (declarative widgets read theirs from a JSON file).
 *
 * It declares one setting per descriptor field, so decoding works exactly as for compiled plugins: bad
 * values fall back to their defaults and are reported as issues. The decoded result is a [JsonObject]
 * with every field present and valid (e.g. a duration is always written like `"15m"`).
 */
class DescriptorSettingsSchema(descriptor: SettingsDescriptor) : SettingsSchema<JsonObject>() {

    /** A declared setting plus how to write its decoded value back as JSON. */
    private class Binding<V>(val setting: Setting<V>, val toJson: (V) -> JsonElement)

    private val bindings = mutableListOf<Binding<*>>()

    init {
        for (field in descriptor.fields) {
            declareField(field)
        }
    }

    override fun build(values: SettingsValues): JsonObject {
        val result = linkedMapOf<String, JsonElement>()
        for (binding in bindings) {
            result[binding.setting.key] = jsonOf(binding, values)
        }
        return JsonObject(result)
    }

    /** A separate function so the binding's value type `V` is the same for the setting and its converter. */
    private fun <V> jsonOf(binding: Binding<V>, values: SettingsValues): JsonElement = binding.toJson(values[binding.setting])

    private fun <V> bind(setting: Setting<V>, toJson: (V) -> JsonElement) {
        bindings.add(Binding(setting, toJson))
    }

    private fun declareField(field: FieldDescriptor) {
        when (field) {
            is FieldDescriptor.Text -> bind(
                text(field.key, field.label, field.default, field.description, field.required, field.placeholder, field.multiline),
            ) { value -> JsonPrimitive(value) }

            is FieldDescriptor.Number -> bind(
                number(field.key, field.label, field.default, field.description, field.min, field.max),
            ) { value -> JsonPrimitive(value) }

            is FieldDescriptor.Toggle -> bind(
                toggle(field.key, field.label, field.default, field.description),
            ) { value -> JsonPrimitive(value) }

            is FieldDescriptor.Choice -> bind(
                textChoice(field.key, field.label, field.options, field.default, field.description),
            ) { value -> JsonPrimitive(value) }

            is FieldDescriptor.Duration -> {
                // A broken default in a definition file shouldn't crash: fall back to 15 minutes.
                val default = Duration.parseOrNull(field.default) ?: 15.minutes
                val minText = field.min
                val min = if (minText == null) null else Duration.parseOrNull(minText)
                bind(duration(field.key, field.label, default, field.description, min)) { value -> JsonPrimitive(value.toString()) }
            }

            is FieldDescriptor.Color -> {
                val default = ColorHex.parse(field.default) ?: Color.White
                bind(color(field.key, field.label, default, field.description)) { value -> JsonPrimitive(ColorHex.format(value)) }
            }

            is FieldDescriptor.TextList -> bind(
                textList(field.key, field.label, field.default, field.description),
            ) { value -> JsonArray(value.map { text -> JsonPrimitive(text) }) }

            is FieldDescriptor.ObjectList -> bind(
                objectList(field.key, field.label, DescriptorSettingsSchema(field.item), field.description),
            ) { value -> JsonArray(value) }

            is FieldDescriptor.Account -> bind(
                account(field.key, field.label, field.provider, field.description, field.required),
            ) { value -> if (value == null) JsonNull else JsonPrimitive(value.accountId) }

            is FieldDescriptor.InstanceRefs -> bind(
                instanceRefs(field.key, field.label, field.extensionPoint, field.description),
            ) { value -> JsonArray(value.map { id -> JsonPrimitive(id) }) }
        }
    }
}
