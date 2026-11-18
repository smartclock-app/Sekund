package uk.dnpk.sekund.runtime.auth

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The tiny subset of JSONPath that auth definitions use for account labels: `$.email`, `$.user.username`.
 * Only object keys, no arrays or wildcards.
 */
object JsonPath {
    /** The text at [path] in [json], or null if it isn't there or isn't a plain value. */
    fun text(json: JsonElement, path: String): String? {
        if (!path.startsWith("$.")) return null
        var current: JsonElement = json
        for (key in path.removePrefix("$.").split('.')) {
            if (current !is JsonObject) return null
            current = current[key] ?: return null
        }
        if (current !is JsonPrimitive) return null
        return current.content
    }
}
