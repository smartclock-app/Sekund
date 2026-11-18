package uk.dnpk.sekund.protocol.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A settings screen described as data.
 *
 * Plugins declare their settings in Kotlin (see `SettingsSchema` in :app:sdk). Sekund turns that into a
 * [SettingsDescriptor], which the on-device settings UI and the companion app render generically,
 * so neither needs to know anything about a specific plugin.
 */
@Serializable
data class SettingsDescriptor(
    val fields: List<FieldDescriptor>,
)

/**
 * One field on a settings screen. Each subclass is one kind of input (text box, toggle, dropdown...).
 *
 * In JSON the kind is written as `"kind"`, e.g. `{"kind": "toggle", "key": "showSeconds", ...}`.
 * `default` is always the value used when the user hasn't set anything.
 */
@Serializable
sealed class FieldDescriptor {
    /** The JSON key in the instance's settings object. */
    abstract val key: String

    /** Short human label, e.g. "Postcode". */
    abstract val label: String

    /** Optional longer help text shown under the field. */
    abstract val description: String?

    /** If true the UI marks the field as required and Sekund reports an issue while it's empty. */
    abstract val required: Boolean

    /** A single line (or multi-line) text box. */
    @Serializable
    @SerialName("text")
    data class Text(
        override val key: String,
        override val label: String,
        override val description: String? = null,
        override val required: Boolean = false,
        val default: String = "",
        val placeholder: String? = null,
        val multiline: Boolean = false,
    ) : FieldDescriptor()

    /** A whole number, optionally limited to [min]..[max]. */
    @Serializable
    @SerialName("number")
    data class Number(
        override val key: String,
        override val label: String,
        override val description: String? = null,
        override val required: Boolean = false,
        val default: Int,
        val min: Int? = null,
        val max: Int? = null,
    ) : FieldDescriptor()

    /** An on/off switch. */
    @Serializable
    @SerialName("toggle")
    data class Toggle(
        override val key: String,
        override val label: String,
        override val description: String? = null,
        override val required: Boolean = false,
        val default: Boolean,
    ) : FieldDescriptor()

    /** Pick one of a fixed list of [options] (a dropdown or radio buttons). */
    @Serializable
    @SerialName("choice")
    data class Choice(
        override val key: String,
        override val label: String,
        override val description: String? = null,
        override val required: Boolean = false,
        val options: List<ChoiceOption>,
        val default: String,
    ) : FieldDescriptor()

    /**
     * A length of time. Stored in JSON as a readable string such as `"15m"`, `"1h 30m"` or `"45s"`.
     * [default] and [min] use the same format.
     */
    @Serializable
    @SerialName("duration")
    data class Duration(
        override val key: String,
        override val label: String,
        override val description: String? = null,
        override val required: Boolean = false,
        val default: String,
        val min: String? = null,
    ) : FieldDescriptor()

    /** A colour, stored as `"#RRGGBB"` or `"#AARRGGBB"`. */
    @Serializable
    @SerialName("color")
    data class Color(
        override val key: String,
        override val label: String,
        override val description: String? = null,
        override val required: Boolean = false,
        val default: String,
    ) : FieldDescriptor()

    /** A list of strings, e.g. calendar title filters. */
    @Serializable
    @SerialName("textList")
    data class TextList(
        override val key: String,
        override val label: String,
        override val description: String? = null,
        override val required: Boolean = false,
        val default: List<String> = emptyList(),
    ) : FieldDescriptor()

    /** A list of small objects that all share the same fields ([item]), e.g. a list of cameras. */
    @Serializable
    @SerialName("objectList")
    data class ObjectList(
        override val key: String,
        override val label: String,
        override val description: String? = null,
        override val required: Boolean = false,
        val item: SettingsDescriptor,
    ) : FieldDescriptor()

    /** Pick a connected account from the auth registry, limited to one [provider] (e.g. "google"). */
    @Serializable
    @SerialName("account")
    data class Account(
        override val key: String,
        override val label: String,
        override val description: String? = null,
        override val required: Boolean = false,
        val provider: String,
    ) : FieldDescriptor()

    /**
     * Pick other instances that contribute to an extension point, e.g. which calendar sources a
     * calendar widget shows. Stored as a list of instance ids.
     */
    @Serializable
    @SerialName("instanceRefs")
    data class InstanceRefs(
        override val key: String,
        override val label: String,
        override val description: String? = null,
        override val required: Boolean = false,
        val extensionPoint: String,
    ) : FieldDescriptor()
}

/** One option of a [FieldDescriptor.Choice]: the stored [value] and the [label] shown to people. */
@Serializable
data class ChoiceOption(
    val value: String,
    val label: String,
)
