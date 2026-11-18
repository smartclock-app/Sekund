package uk.dnpk.sekund.runtime.legacy

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Forgiving reads from the old app's config: every function returns null (or empty) for anything
 * missing or of the wrong type, because old files can be in any state.
 */
internal object LegacyJson {
    fun obj(json: JsonElement?, key: String): JsonObject? = (json as? JsonObject)?.get(key) as? JsonObject

    fun array(json: JsonElement?, key: String): List<JsonElement> = ((json as? JsonObject)?.get(key) as? JsonArray) ?: emptyList()

    /** A non-empty string, or null. Empty strings meant "not set" in the old app. */
    fun text(json: JsonElement?, key: String): String? {
        val value = (json as? JsonObject)?.get(key) as? JsonPrimitive ?: return null
        if (!value.isString) return null
        return value.content.trim().ifEmpty { null }
    }

    fun number(json: JsonElement?, key: String): Double? {
        val value = (json as? JsonObject)?.get(key) as? JsonPrimitive ?: return null
        if (value.isString) return null
        return value.doubleOrNull
    }

    fun bool(json: JsonElement?, key: String): Boolean? {
        val value = (json as? JsonObject)?.get(key) as? JsonPrimitive ?: return null
        if (value.isString) return null
        return value.booleanOrNull
    }

    fun texts(json: JsonElement?, key: String): List<String> {
        val result = mutableListOf<String>()
        for (item in array(json, key)) {
            val primitive = item as? JsonPrimitive ?: continue
            if (primitive.isString && primitive.content.isNotBlank()) result.add(primitive.content)
        }
        return result
    }
}
