package uk.dnpk.sekund.plugins.declarative

import io.ktor.http.encodeURLParameter
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import uk.dnpk.sekund.protocol.declarative.Filter
import uk.dnpk.sekund.protocol.declarative.Placeholder
import uk.dnpk.sekund.protocol.declarative.TemplateSyntax
import uk.dnpk.sekund.protocol.declarative.UnitConversion
import uk.dnpk.sekund.sdk.context.TimePreferences

/**
 * Fills in templates such as `"{$.main.temp|round}°"` using a [TemplateScope].
 *
 * Filters, applied left to right:
 *
 * | Filter          | Does                                                          | Example                         |
 * |-----------------|---------------------------------------------------------------|---------------------------------|
 * | `number:N`      | N digits after the point (default 0)                          | `{$.temp|number:1}` → `12.3`    |
 * | `round`         | same as `number:0`                                            | `12.7` → `13`                   |
 * | `divide:N`      | divide by N                                                   | pence → pounds: `divide:100`    |
 * | `multiply:N`    | multiply by N                                                 |                                 |
 * | `lookup:table`  | replace using a table from `lookups` (`*` = anything else)    | `01d` → `☀️`                    |
 * | `upper`, `lower`| change case                                                   |                                 |
 * | `default:text`  | use text when the value is missing or empty                   | `{$.name|default:Unknown}`      |
 * | `date`          | a date/time as `Mon 3 Mar`                                    |                                 |
 * | `time`          | a date/time as `14:05` or `2:05 PM` (the person's preference) |                                 |
 * | `urlencode`     | make safe for a URL query                                     | `{settings.postcode|urlencode}` |
 * | `convert:A:B`   | convert a number from unit A to B (°C/F/K, m/s, km/h, mph...) | `{$.wind|convert:m/s:mph}`      |
 *
 * Any filter argument written as `settings.key` is replaced by that setting, e.g. `convert:C:settings.units`.
 *
 * @param lookups the definition's lookup tables.
 * @param timeZone used to show dates and times.
 * @param preferences 12/24-hour preference for `|time`.
 */
class TemplateEngine(
    private val lookups: Map<String, Map<String, String>>,
    private val timeZone: () -> TimeZone,
    private val preferences: () -> TimePreferences,
) {
    /**
     * [template] with every placeholder filled in. A placeholder with no value becomes empty text,
     * unless [strict] is true, when it throws [MissingValueException] instead (used for URLs and headers,
     * so a half-filled request is never sent).
     */
    fun fill(template: String, scope: TemplateScope, strict: Boolean = false): String {
        return TemplateSyntax.replace(template) { placeholder ->
            val value = evaluate(placeholder, scope)
            if (value == null && strict) throw MissingValueException(placeholder.value, template)
            textOf(value)
        }
    }

    /**
     * The value of a template that is a single placeholder (e.g. `"{$.progress}"`) without turning it into
     * text first; for any other template, the filled-in text. Null if a single placeholder has no value.
     */
    fun value(template: String, scope: TemplateScope): JsonElement? {
        val single = TemplateSyntax.single(template) ?: return JsonPrimitive(fill(template, scope))
        return evaluate(single, scope)
    }

    /** The value of [template] as a number, or null if it isn't one. */
    fun number(template: String, scope: TemplateScope): Double? {
        val value = value(template, scope) as? JsonPrimitive ?: return null
        return value.content.trim().toDoubleOrNull()
    }

    private fun evaluate(placeholder: Placeholder, scope: TemplateScope): JsonElement? {
        var value: JsonElement? = scope.resolve(placeholder.value)
        if (value is JsonNull) value = null
        for (filter in placeholder.filters) {
            value = apply(filter, value, scope)
        }
        return value
    }

    private fun apply(filter: Filter, value: JsonElement?, scope: TemplateScope): JsonElement? {
        // An argument written as `settings.key` means "that setting's value", e.g. `|convert:C:settings.units`.
        val arguments = filter.arguments.map { argument ->
            if (argument.startsWith("settings.")) textOf(scope.resolve(argument)) else argument
        }
        val argument = arguments.firstOrNull()
        if (filter.name == "default") {
            if (value == null || textOf(value).isEmpty()) return JsonPrimitive(arguments.joinToString(":"))
            return value
        }
        if (value == null) return null // every other filter leaves a missing value missing

        val text = textOf(value)
        return when (filter.name) {
            "number" -> numberText(value, argument?.toIntOrNull() ?: 0)
            "round" -> numberText(value, 0)
            "divide" -> arithmetic(value, argument) { number, by -> if (by == 0.0) null else number / by }
            "multiply" -> arithmetic(value, argument) { number, by -> number * by }
            "lookup" -> lookup(text, argument)
            "upper" -> JsonPrimitive(text.uppercase())
            "lower" -> JsonPrimitive(text.lowercase())
            "date" -> dateText(text) { dateTime -> ValueFormat.shortDate(dateTime) }
            "time" -> dateText(text) { dateTime -> ValueFormat.time(dateTime, preferences()) }
            "urlencode" -> JsonPrimitive(text.encodeURLParameter())
            "convert" -> convert(value, arguments)
            else -> value // unknown filters are reported when the definition loads; ignore them here
        }
    }

    private fun numberText(value: JsonElement, decimals: Int): JsonElement? {
        val number = numberOf(value) ?: return value
        return JsonPrimitive(ValueFormat.fixed(number, decimals))
    }

    private fun arithmetic(value: JsonElement, argument: String?, operation: (Double, Double) -> Double?): JsonElement? {
        val number = numberOf(value) ?: return value
        val other = argument?.toDoubleOrNull() ?: return value
        val result = operation(number, other) ?: return null
        return JsonPrimitive(result)
    }

    /** `convert:<from>:<to>`. Leaves the value as it is if it isn't a number or the units don't fit. */
    private fun convert(value: JsonElement, arguments: List<String>): JsonElement {
        if (arguments.size != 2) return value
        val number = numberOf(value) ?: return value
        val converted = UnitConversion.convert(number, from = arguments[0], to = arguments[1]) ?: return value
        return JsonPrimitive(converted)
    }

    private fun lookup(text: String, tableName: String?): JsonElement {
        val table = lookups[tableName] ?: return JsonPrimitive(text)
        val found = table[text] ?: table["*"] ?: text
        return JsonPrimitive(found)
    }

    private fun dateText(text: String, format: (kotlinx.datetime.LocalDateTime) -> String): JsonElement {
        val dateTime = ValueFormat.parseDateTime(text, timeZone()) ?: return JsonPrimitive(text)
        return JsonPrimitive(format(dateTime))
    }

    private fun numberOf(value: JsonElement): Double? {
        if (value !is JsonPrimitive) return null
        return value.doubleOrNull ?: value.content.trim().toDoubleOrNull()
    }

    companion object {
        /** How a value is shown as text: strings as they are, whole numbers without `.0`, objects as JSON. */
        fun textOf(value: JsonElement?): String {
            if (value == null || value is JsonNull) return ""
            if (value is JsonObject || value is JsonArray) return value.toString()
            val primitive = value as JsonPrimitive
            if (primitive.isString) return primitive.content
            val number = primitive.doubleOrNull ?: return primitive.content
            return ValueFormat.plain(number)
        }
    }
}

/** A template needed a value that isn't there, e.g. `{settings.postcode}` while the postcode is empty. */
class MissingValueException(val name: String, template: String) :
    IllegalStateException("No value for {$name} in \"$template\"")
