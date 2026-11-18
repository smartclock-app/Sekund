package uk.dnpk.sekund.sdk.settings

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.JsonObject
import uk.dnpk.sekund.protocol.settings.SettingsDescriptor
import uk.dnpk.sekund.protocol.settings.SettingsIssue
import kotlin.time.Duration

/**
 * The settings a plugin needs, and how to turn them into a typed Kotlin object.
 *
 * Usage: create an `object` that extends this class, declare one property per setting, then
 * implement [build] to assemble your own settings class:
 *
 * ```kotlin
 * data class WeatherSettings(val postcode: String, val units: Units)
 *
 * object WeatherSettingsSchema : SettingsSchema<WeatherSettings>() {
 *     val postcode = text("postcode", label = "Postcode", required = true)
 *     val units = choice("units", label = "Units", options = Units.entries, default = Units.Metric)
 *
 *     override fun build(values: SettingsValues) = WeatherSettings(
 *         postcode = values[postcode],
 *         units = values[units],
 *     )
 * }
 * ```
 *
 * From that one declaration Sekund gets:
 * - [decode]: reads the JSON from config.json, replacing bad values with defaults and reporting issues,
 * - [describe]: the description the settings screens are generated from,
 * - [defaults]: the JSON for a brand-new instance.
 *
 * (Comparable to a Zod schema in the old TypeScript app, but it never fails as a whole.)
 *
 * @param T your plugin's settings class.
 */
abstract class SettingsSchema<T> {
    private val declared = mutableListOf<Setting<*>>()

    /** Every setting declared in this schema, in declaration order. */
    val settings: List<Setting<*>>
        get() = declared

    /** Assembles your settings class from the decoded [values]. Keep this a simple mapping. */
    abstract fun build(values: SettingsValues): T

    // ---------------------------------------------------------------------------------------------
    // Declaring settings. Each function creates a setting, remembers it, and returns it so you can
    // store it in a property and later read its value with `values[property]` in build().
    // ---------------------------------------------------------------------------------------------

    protected fun text(
        key: String,
        label: String,
        default: String = "",
        description: String? = null,
        required: Boolean = false,
        placeholder: String? = null,
        multiline: Boolean = false,
    ): Setting<String> = declare(TextSetting(key, label, description, default, required, placeholder, multiline))

    protected fun number(
        key: String,
        label: String,
        default: Int,
        description: String? = null,
        min: Int? = null,
        max: Int? = null,
    ): Setting<Int> = declare(NumberSetting(key, label, description, default, required = false, min = min, max = max))

    protected fun toggle(
        key: String,
        label: String,
        default: Boolean,
        description: String? = null,
    ): Setting<Boolean> = declare(ToggleSetting(key, label, description, default))

    /** A choice between the values of an enum. [optionLabel] gives the text shown for each option. */
    protected fun <E : Enum<E>> choice(
        key: String,
        label: String,
        options: List<E>,
        default: E,
        description: String? = null,
        optionLabel: (E) -> String = { option -> option.name },
    ): Setting<E> = declare(ChoiceSetting(key, label, description, default, options, optionLabel))

    protected fun duration(
        key: String,
        label: String,
        default: Duration,
        description: String? = null,
        min: Duration? = null,
    ): Setting<Duration> = declare(DurationSetting(key, label, description, default, min))

    protected fun color(
        key: String,
        label: String,
        default: Color,
        description: String? = null,
    ): Setting<Color> = declare(ColorSetting(key, label, description, default))

    protected fun textList(
        key: String,
        label: String,
        default: List<String> = emptyList(),
        description: String? = null,
    ): Setting<List<String>> = declare(TextListSetting(key, label, description, default))

    /** A list of objects, each described by [itemSchema] (another SettingsSchema). */
    protected fun <I> objectList(
        key: String,
        label: String,
        itemSchema: SettingsSchema<I>,
        description: String? = null,
    ): Setting<List<I>> = declare(ObjectListSetting(key, label, description, itemSchema))

    /**
     * A connected account of [provider] (e.g. `"google"`). The plugin must also declare the provider in
     * its manifest's `requires.auth`.
     */
    protected fun account(
        key: String,
        label: String,
        provider: String,
        description: String? = null,
        required: Boolean = true,
    ): Setting<AccountRef?> = declare(AccountSetting(key, label, description, required, provider))

    /** Other instances contributing to [extensionPoint], picked by the user (stored as instance ids). */
    protected fun instanceRefs(
        key: String,
        label: String,
        extensionPoint: String,
        description: String? = null,
    ): Setting<List<String>> = declare(InstanceRefsSetting(key, label, description, extensionPoint))

    private fun <V> declare(setting: Setting<V>): Setting<V> {
        for (existing in declared) {
            require(existing.key != setting.key) { "Setting '${setting.key}' is declared twice in ${this::class.simpleName}" }
        }
        declared.add(setting)
        return setting
    }

    // ---------------------------------------------------------------------------------------------
    // Using the schema (called by Sekund, not usually by plugins).
    // ---------------------------------------------------------------------------------------------

    /**
     * Reads settings from [json] (null = nothing configured yet).
     *
     * Never throws because of bad input: every missing or invalid value becomes its default, and every
     * problem is listed in [DecodedSettings.issues]. [pathPrefix] is put in front of issue paths when this
     * schema is nested inside a list.
     */
    fun decode(json: JsonObject?, pathPrefix: String = ""): DecodedSettings<T> {
        val issues = mutableListOf<SettingsIssue>()
        val values = SettingsValues()

        for (setting in declared) {
            decodeOne(setting, json, pathPrefix, issues, values)
        }

        if (json != null) {
            val knownKeys = declared.map { setting -> setting.key }.toSet()
            for (key in json.keys) {
                if (key !in knownKeys) {
                    issues.add(SettingsIssue(pathOf(pathPrefix, key), "Unknown setting, ignored"))
                }
            }
        }

        return DecodedSettings(value = build(values), issues = issues)
    }

    /** Decodes one setting into [values]. A separate function so the type `V` stays the same throughout. */
    private fun <V> decodeOne(
        setting: Setting<V>,
        json: JsonObject?,
        pathPrefix: String,
        issues: MutableList<SettingsIssue>,
        values: SettingsValues,
    ) {
        val path = pathOf(pathPrefix, setting.key)
        val element = json?.get(setting.key)

        val value: V = if (element == null) setting.default else setting.decode(element, path, issues)

        if (setting.required && setting.isEmpty(value)) {
            issues.add(SettingsIssue(path, "Required"))
        }
        values.put(setting, value)
    }

    private fun pathOf(prefix: String, key: String): String = if (prefix.isEmpty()) key else "$prefix.$key"

    /** Describes every setting, for generated settings screens. */
    fun describe(): SettingsDescriptor = SettingsDescriptor(fields = declared.map { setting -> setting.describe() })

    /** The settings JSON for a brand-new instance: every setting at its default value. */
    fun defaults(): JsonObject = JsonObject(declared.associate { setting -> setting.key to setting.defaultJson() })
}
