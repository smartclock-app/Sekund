package uk.dnpk.sekund.settings

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reading and writing single values in an instance's settings JSON.
 *
 * The settings screens edit the JSON directly (not a Kotlin settings class), because they're generated
 * from descriptors and know nothing about any particular plugin. Every read falls back to a default if
 * the value is missing or has the wrong type, just like the plugin's own decoding does.
 */
object JsonFields {
    fun text(json: JsonObject, key: String, default: String): String {
        val value = json[key]
        if (value is JsonPrimitive && value.isString) return value.content
        return default
    }

    fun number(json: JsonObject, key: String, default: Int): Int {
        val value = json[key]
        if (value is JsonPrimitive && !value.isString) return value.intOrNull ?: default
        return default
    }

    fun toggle(json: JsonObject, key: String, default: Boolean): Boolean {
        val value = json[key]
        if (value is JsonPrimitive && !value.isString) return value.booleanOrNull ?: default
        return default
    }

    /** A text value that may also be null (e.g. an account that hasn't been picked yet). */
    fun optionalText(json: JsonObject, key: String): String? {
        val value = json[key]
        if (value is JsonPrimitive && value.isString) return value.content
        return null
    }

    fun textList(json: JsonObject, key: String, default: List<String>): List<String> {
        val value = json[key] as? JsonArray ?: return default
        val result = mutableListOf<String>()
        for (item in value) {
            if (item is JsonPrimitive && item.isString) result.add(item.content)
        }
        return result
    }

    fun objectList(json: JsonObject, key: String): List<JsonObject> {
        val value = json[key] as? JsonArray ?: return emptyList()
        val result = mutableListOf<JsonObject>()
        for (item in value) {
            if (item is JsonObject) result.add(item)
        }
        return result
    }

    /** A copy of [json] with [key] set to [value]. */
    fun with(json: JsonObject, key: String, value: JsonElement): JsonObject {
        val copy = json.toMutableMap()
        copy[key] = value
        return JsonObject(copy)
    }

    fun withText(json: JsonObject, key: String, value: String): JsonObject = with(json, key, JsonPrimitive(value))

    fun withNumber(json: JsonObject, key: String, value: Int): JsonObject = with(json, key, JsonPrimitive(value))

    fun withToggle(json: JsonObject, key: String, value: Boolean): JsonObject = with(json, key, JsonPrimitive(value))

    fun withOptionalText(json: JsonObject, key: String, value: String?): JsonObject {
        if (value == null) return with(json, key, JsonNull)
        return with(json, key, JsonPrimitive(value))
    }

    fun withTextList(json: JsonObject, key: String, value: List<String>): JsonObject =
        with(json, key, JsonArray(value.map { text -> JsonPrimitive(text) }))

    fun withObjectList(json: JsonObject, key: String, value: List<JsonObject>): JsonObject = with(json, key, JsonArray(value))
}
