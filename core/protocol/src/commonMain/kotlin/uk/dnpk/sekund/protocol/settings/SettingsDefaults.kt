package uk.dnpk.sekund.protocol.settings

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The settings JSON for something brand new, worked out from its [SettingsDescriptor] alone: every field
 * at its default. Used for new items in an object list, and for declarative widgets (which only have a
 * descriptor, no Kotlin schema).
 */
object SettingsDefaults {
    fun of(descriptor: SettingsDescriptor): JsonObject {
        val values = linkedMapOf<String, JsonElement>()
        for (field in descriptor.fields) {
            values[field.key] = defaultOf(field)
        }
        return JsonObject(values)
    }

    fun defaultOf(field: FieldDescriptor): JsonElement = when (field) {
        is FieldDescriptor.Text -> JsonPrimitive(field.default)
        is FieldDescriptor.Number -> JsonPrimitive(field.default)
        is FieldDescriptor.Toggle -> JsonPrimitive(field.default)
        is FieldDescriptor.Choice -> JsonPrimitive(field.default)
        is FieldDescriptor.Duration -> JsonPrimitive(field.default)
        is FieldDescriptor.Color -> JsonPrimitive(field.default)
        is FieldDescriptor.TextList -> JsonArray(field.default.map { text -> JsonPrimitive(text) })
        is FieldDescriptor.ObjectList -> JsonArray(emptyList())
        is FieldDescriptor.Account -> JsonNull
        is FieldDescriptor.InstanceRefs -> JsonArray(emptyList())
    }
}
