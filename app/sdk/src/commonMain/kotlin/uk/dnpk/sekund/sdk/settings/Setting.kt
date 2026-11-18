package uk.dnpk.sekund.sdk.settings

import kotlinx.serialization.json.JsonElement
import uk.dnpk.sekund.protocol.settings.FieldDescriptor
import uk.dnpk.sekund.protocol.settings.SettingsIssue

/**
 * One setting of a plugin, e.g. "postcode" or "show seconds".
 *
 * You don't create these directly. Declare them inside a [SettingsSchema] with `text(...)`, `toggle(...)`
 * and friends. Each subclass (see SettingKinds.kt) knows three things about its kind of value:
 *
 * 1. how to read it from JSON ([decode]),
 * 2. what its default looks like in JSON ([defaultJson]), used to fill in new instances,
 * 3. how to describe it to a settings screen ([describe]).
 *
 * @param V the Kotlin type the plugin receives, e.g. `String`, `Boolean`, `Duration`.
 */
abstract class Setting<V>(
    /** The key in the settings JSON object. */
    val key: String,
    /** Short human label for settings screens. */
    val label: String,
    /** Optional help text. */
    val description: String?,
    /** Used when the value is missing or invalid. */
    val default: V,
    /** Shown as required in the UI. A missing required value is reported as an issue. */
    val required: Boolean,
) {
    /**
     * Reads this setting's value from [json].
     *
     * Must never throw. If [json] isn't valid, call [invalid] to record an issue and return the default.
     *
     * @param path where this value lives, used in issue messages, e.g. `"cameras[0].id"`.
     * @param issues the list to add problems to.
     */
    abstract fun decode(json: JsonElement, path: String, issues: MutableList<SettingsIssue>): V

    /**
     * The [default] value as JSON, in the same format [decode] reads.
     *
     * (There's no general "value to JSON" function on purpose: settings screens edit the JSON directly,
     * so Sekund only ever needs to write defaults from Kotlin.)
     */
    abstract fun defaultJson(): JsonElement

    /** Describes this setting for a generated settings screen. */
    abstract fun describe(): FieldDescriptor

    /**
     * True if [value] counts as "not filled in" for a [required] setting.
     * The default treats blank strings and empty lists as empty. Override if that's wrong for a kind.
     */
    open fun isEmpty(value: V): Boolean {
        if (value == null) return true
        if (value is String) return value.isBlank()
        if (value is List<*>) return value.isEmpty()
        return false
    }

    /** Records an issue at [path] and returns the default. A helper for [decode] implementations. */
    protected fun invalid(path: String, issues: MutableList<SettingsIssue>, message: String): V {
        issues.add(SettingsIssue(path = path, message = message))
        return default
    }
}
