package uk.dnpk.sekund.protocol.declarative

/**
 * Splits declarative-widget templates into their pieces. Only the *syntax* lives here (shared by the
 * validator and the engine); what the pieces mean is up to the engine.
 *
 * A template is text with placeholders in braces: `"{temp|round}°C in {settings.city}"`.
 * A placeholder is a *value* followed by optional *filters*, separated by `|`. A filter can take
 * arguments after `:`, e.g. `{$.balance|divide:100|number:2}`.
 */
object TemplateSyntax {
    private val placeholderPattern = Regex("\\{([^{}]+)\\}")

    /** Every placeholder in [template], in order. */
    fun placeholders(template: String): List<Placeholder> {
        val result = mutableListOf<Placeholder>()
        for (match in placeholderPattern.findAll(template)) {
            result.add(parsePlaceholder(match.groupValues[1]))
        }
        return result
    }

    /**
     * Rebuilds [template] with each placeholder replaced by [fill]'s answer. [fill] gets the parsed
     * placeholder and returns the text to put in its place.
     */
    fun replace(template: String, fill: (Placeholder) -> String): String {
        return placeholderPattern.replace(template) { match -> fill(parsePlaceholder(match.groupValues[1])) }
    }

    /**
     * If [template] is exactly one placeholder and nothing else (e.g. `"{$.main.temp}"`), that placeholder.
     * Used where a raw value is wanted rather than text, such as a progress bar's number.
     */
    fun single(template: String): Placeholder? {
        val trimmed = template.trim()
        val match = placeholderPattern.matchEntire(trimmed) ?: return null
        return parsePlaceholder(match.groupValues[1])
    }

    private fun parsePlaceholder(inside: String): Placeholder {
        val parts = inside.split('|')
        val filters = mutableListOf<Filter>()
        for (part in parts.drop(1)) {
            val pieces = part.trim().split(':')
            filters.add(Filter(name = pieces[0].trim(), arguments = pieces.drop(1)))
        }
        return Placeholder(value = parts[0].trim(), filters = filters)
    }
}

/**
 * One `{...}` in a template.
 *
 * @property value what to look up: `settings.city`, `item.id`, `page`, a mapped field name such as `temp`,
 *   or a JSON path starting with `$`.
 */
data class Placeholder(
    val value: String,
    val filters: List<Filter>,
)

/** One `|name:arg1:arg2` after a placeholder's value. */
data class Filter(
    val name: String,
    val arguments: List<String>,
)
