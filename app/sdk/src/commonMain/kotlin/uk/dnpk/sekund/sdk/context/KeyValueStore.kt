package uk.dnpk.sekund.sdk.context

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import uk.dnpk.sekund.protocol.SekundJson

/**
 * A small, persistent key/value store private to one instance, for caching data so a widget has
 * something to show straight after a restart (e.g. the last fetched watchlist or song lyrics).
 *
 * Values are stored as JSON. Use the typed helpers [get] and [put] with any `@Serializable` class.
 * Not for secrets: credentials belong to the auth registry.
 */
interface KeyValueStore {
    suspend fun getJson(key: String): JsonElement?

    suspend fun putJson(key: String, value: JsonElement)

    suspend fun remove(key: String)
}

/** Reads a value of type [T], or null if nothing is stored under [key] (or it no longer fits [T]). */
suspend inline fun <reified T> KeyValueStore.get(key: String): T? {
    val json = getJson(key) ?: return null
    return try {
        SekundJson.decodeFromJsonElement<T>(json)
    } catch (_: IllegalArgumentException) {
        // The stored value was written by an older version with a different shape. Treat it as missing.
        null
    }
}

/** Stores [value] (any `@Serializable` class) under [key]. */
suspend inline fun <reified T> KeyValueStore.put(key: String, value: T) {
    putJson(key, SekundJson.encodeToJsonElement(value))
}
