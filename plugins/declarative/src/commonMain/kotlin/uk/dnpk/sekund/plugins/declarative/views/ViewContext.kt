package uk.dnpk.sekund.plugins.declarative.views

import kotlinx.serialization.json.JsonObject
import uk.dnpk.sekund.plugins.declarative.ImageLoader
import uk.dnpk.sekund.plugins.declarative.MappedData
import uk.dnpk.sekund.plugins.declarative.TemplateEngine
import uk.dnpk.sekund.plugins.declarative.TemplateScope

/**
 * Everything a view template needs to turn its templates into text: the mapped [data], the instance's
 * settings and the template engine. Views call [text] (whole widget) or [rowText] (one list row).
 */
class ViewContext(
    val data: MappedData,
    private val settings: JsonObject,
    private val templates: TemplateEngine,
    val images: ImageLoader,
) {
    private val scope = TemplateScope(settings = settings, named = data.fields)

    /** [template] filled in with the widget's mapped fields and settings. Null in, null out. */
    fun text(template: String?): String? {
        if (template == null) return null
        return templates.fill(template, scope)
    }

    /** [template] filled in for one row: the row's fields, falling back to the widget's fields. */
    fun rowText(template: String?, row: Map<String, String>): String? {
        if (template == null) return null
        return templates.fill(template, scope.copy(named = data.fields + row))
    }

    /** [template] as a number, or null if it doesn't fill in as one. */
    fun number(template: String): Double? = templates.number(template, scope)
}
