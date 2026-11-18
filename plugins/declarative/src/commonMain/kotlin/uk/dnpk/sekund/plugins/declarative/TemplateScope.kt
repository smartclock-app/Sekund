package uk.dnpk.sekund.plugins.declarative

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What a template's placeholders can refer to at one moment. (Think of it as the "props" a template is
 * rendered with.)
 *
 * | Placeholder        | Comes from                                                        |
 * |--------------------|-------------------------------------------------------------------|
 * | `{$.a.b[0]}`       | [node]: the API answer (or the current row while mapping rows)    |
 * | `{settings.x}`     | the instance's [settings]; `{settings.x.y}` reaches inside objects |
 * | `{item}`, `{item.x}` | the current entry of a `forEach` list                           |
 * | `{page}`           | the current page, while paginating                                |
 * | `{name}`           | a value the `map` step produced ([named]), used in views          |
 */
data class TemplateScope(
    val settings: JsonObject,
    val node: JsonElement? = null,
    val item: JsonElement? = null,
    val page: String? = null,
    val named: Map<String, String> = emptyMap(),
) {
    /** The raw value a placeholder refers to, or null if there's nothing there. */
    fun resolve(value: String): JsonElement? {
        if (value.startsWith("$")) return JsonPathLite.find(node, value)
        if (value == "page") return if (page == null) null else JsonPrimitive(page)
        if (value == "item") return item
        if (value.startsWith("item.")) return JsonPathLite.find(item, "$." + value.removePrefix("item."))
        if (value.startsWith("settings.")) return JsonPathLite.find(settings, "$." + value.removePrefix("settings."))
        val mapped = named[value] ?: return null
        return JsonPrimitive(mapped)
    }
}
