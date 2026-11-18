package uk.dnpk.sekund.runtime.config

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * JSON Merge Patch (RFC 7386), used by `PATCH /v1/config`: send only what changes.
 *
 * - an object in the patch is merged into the same object in the target, key by key,
 * - `null` removes a key,
 * - anything else (text, numbers, lists) replaces the target's value.
 *
 * Example: target `{"clock": {"use24Hour": false, "showSeconds": true}}` patched with
 * `{"clock": {"use24Hour": true}}` gives `{"clock": {"use24Hour": true, "showSeconds": true}}`.
 */
object JsonMergePatch {
    fun apply(target: JsonElement, patch: JsonElement): JsonElement {
        if (patch !is JsonObject) return patch
        val result = if (target is JsonObject) target.toMutableMap() else mutableMapOf()
        for ((key, value) in patch) {
            if (value is JsonNull) {
                result.remove(key)
            } else {
                result[key] = apply(result[key] ?: JsonNull, value)
            }
        }
        return JsonObject(result)
    }
}
