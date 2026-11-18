package uk.dnpk.sekund.sdk.settings

/**
 * The decoded value of every setting in a [SettingsSchema], handed to [SettingsSchema.build].
 *
 * Read a value with `values[mySetting]`. The result already has the right type, because each
 * [Setting] knows its own type `V`, so no casting is needed in plugin code.
 */
class SettingsValues internal constructor() {
    private val values = mutableMapOf<String, Any?>()

    internal fun <V> put(setting: Setting<V>, value: V) {
        values[setting.key] = value
    }

    /** The value of [setting]. Every declared setting always has a value (at worst its default). */
    operator fun <V> get(setting: Setting<V>): V {
        if (!values.containsKey(setting.key)) {
            error("Setting '${setting.key}' doesn't belong to this schema")
        }
        // Safe: put() only ever stores a V under a Setting<V>'s key.
        @Suppress("UNCHECKED_CAST")
        return values[setting.key] as V
    }
}
