package uk.dnpk.sekund.protocol.declarative

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.protocol.settings.SettingsDescriptor

/**
 * A widget (or photo source) described entirely in JSON: call an API, pick values out of the answer,
 * show them with a built-in template. No code runs, so a definition can be installed at runtime
 * (a file in `<config>/widgets/`, or `PUT /v1/declarative-widgets/{id}`) without rebuilding the app.
 *
 * Example (`bins.widget.json`, a made-up "next bin day" API):
 * ```json
 * {
 *   "manifest": { "id": "bins", "name": "Bin day", "version": "1.0.0" },
 *   "hosts": ["https://bins.example.com/"],
 *   "settings": { "fields": [{ "kind": "text", "key": "uprn", "label": "Property number", "required": true }] },
 *   "source": {
 *     "url": "https://bins.example.com/api/next?uprn={settings.uprn|urlencode}",
 *     "refresh": "6h",
 *     "map": { "fields": { "bin": "{$.type}", "day": "{$.date|date}" } }
 *   },
 *   "view": { "kind": "stat", "value": "{day}", "label": "{bin} bin" }
 * }
 * ```
 *
 * Text values are *templates*: `{...}` placeholders are filled in when used. See
 * docs/plugins/declarative-widgets.md for what placeholders and `|filters` are available.
 *
 * @property manifest who this is and what it needs. `requires.auth` must list any provider a source signs in with.
 * @property kind what the definition provides: a widget, or photos for the slideshow clock face.
 * @property locations widgets only: where it may be placed (`"main"`, `"sidebar"`).
 * @property hosts the only addresses requests may go to. A URL that doesn't start with one of these is
 *   refused, so a definition can't send settings anywhere unexpected. May use `{settings.x}` (self-hosted services).
 * @property settings the settings form, in the same format the companion and settings screens use.
 * @property source where the data comes from, and how it's mapped.
 * @property sources extra named sources, e.g. a forecast for the [expanded] view.
 * @property view widgets only: how the data is shown.
 * @property expanded widgets only: an optional full-screen view, opened by tapping the widget.
 * @property lookups named tables for the `|lookup:name` filter, e.g. weather codes to emoji. The key
 *   `"*"` is used when nothing else matches.
 */
@Serializable
data class DeclarativeDefinition(
    val manifest: PluginManifest,
    val kind: DefinitionKind = DefinitionKind.Widget,
    val locations: List<String> = listOf("sidebar"),
    val hosts: List<String>,
    val settings: SettingsDescriptor = SettingsDescriptor(fields = emptyList()),
    val source: SourceDefinition,
    val sources: Map<String, SourceDefinition> = emptyMap(),
    val view: ViewDefinition? = null,
    val expanded: ExpandedDefinition? = null,
    val lookups: Map<String, Map<String, String>> = emptyMap(),
)

/** What a declarative definition provides. */
@Serializable
enum class DefinitionKind {
    /** Something shown in the main square or the sidebar, drawn with a built-in [ViewDefinition]. */
    @SerialName("widget")
    Widget,

    /** Photos for the slideshow clock face. Each mapped row must have a `url`. */
    @SerialName("photoSource")
    PhotoSource,
}

/**
 * One HTTP request (or a series of them) and how to read its answer.
 *
 * @property url may use `{settings.x}`, `{item...}` (with [forEach]) and `{page}` (with [paginate]).
 * @property body sent as JSON for `POST`, also a template.
 * @property auth the id of an auth provider to sign in with, e.g. `"openweather"`. The account comes from
 *   the definition's first `account` setting for that provider. Credentials are added by Sekund; a
 *   definition never sees them.
 * @property refresh how often to fetch again, e.g. `"15m"`. At least one minute.
 */
@Serializable
data class SourceDefinition(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val auth: String? = null,
    val refresh: String = "15m",
    val paginate: PaginateDefinition? = null,
    val forEach: ForEachDefinition? = null,
    val map: MapDefinition = MapDefinition(),
)

/**
 * Fetch several pages and join their rows. Each page's number (or cursor) is available as `{page}`.
 *
 * Stops after [maxPages], when a page has no rows, or (if [next] is set) when the next page value is missing.
 *
 * @property start the first page's value.
 * @property next optional JSON path to the next page's value in each answer (e.g. `"$.assets.nextPage"`).
 *   Without it, pages are counted up from [start].
 */
@Serializable
data class PaginateDefinition(
    val start: Int = 1,
    val maxPages: Int = 10,
    val next: String? = null,
)

/**
 * Make one request per entry of a list setting, and join the results. The current entry is `{item}`
 * (for a text list) or `{item.key}` (for an object list).
 */
@Serializable
data class ForEachDefinition(
    val setting: String,
)

/**
 * Picks named values out of an answer.
 *
 * @property fields name → template, evaluated against the whole answer, e.g. `"temp": "{$.main.temp|round}"`.
 * @property rows optional: a list in the answer, turned into rows of named values.
 */
@Serializable
data class MapDefinition(
    val fields: Map<String, String> = emptyMap(),
    val rows: RowsDefinition? = null,
)

/**
 * @property path where the list is, e.g. `"$.list"`. If it points at a single object, that's one row.
 * @property fields name → template, evaluated against each list entry (so `{$.name}` is the entry's name).
 */
@Serializable
data class RowsDefinition(
    val path: String,
    val fields: Map<String, String>,
)

/** A built-in way of showing data. Its text values are templates over the mapped field names, e.g. `"{temp}°"`. */
@Serializable
sealed class ViewDefinition {
    /** A big value with a label, an optional icon (text or emoji) and some smaller lines under it. */
    @Serializable
    @SerialName("stat")
    data class Stat(
        val value: String,
        val label: String? = null,
        val icon: String? = null,
        val secondary: List<LabelledValue> = emptyList(),
    ) : ViewDefinition()

    /** One line per row. Row templates can use the row's fields. */
    @Serializable
    @SerialName("list")
    data class ListView(
        val title: String? = null,
        val rowTitle: String,
        val rowSubtitle: String? = null,
        val rowTrailing: String? = null,
        val rowIcon: String? = null,
        val maxRows: Int = 10,
    ) : ViewDefinition()

    /** Label: value pairs. Fixed [items], or one pair per row with [rowLabel]/[rowValue]. */
    @Serializable
    @SerialName("keyValue")
    data class KeyValue(
        val title: String? = null,
        val items: List<LabelledValue> = emptyList(),
        val rowLabel: String? = null,
        val rowValue: String? = null,
    ) : ViewDefinition()

    /** A picture from [url], fetched with the source's account if it has one. */
    @Serializable
    @SerialName("image")
    data class Image(
        val url: String,
        val caption: String? = null,
    ) : ViewDefinition()

    /** A progress bar: [value] out of [max] (both numbers once filled in). */
    @Serializable
    @SerialName("progress")
    data class Progress(
        val label: String,
        val value: String,
        val max: String = "1",
        val detail: String? = null,
    ) : ViewDefinition()
}

/** A label and a value, both templates. */
@Serializable
data class LabelledValue(
    val label: String,
    val value: String,
)

/**
 * The full-screen view opened by tapping the widget.
 *
 * @property source the name of an entry in [DeclarativeDefinition.sources], or null to use the main source.
 */
@Serializable
data class ExpandedDefinition(
    val source: String? = null,
    val view: ViewDefinition,
)
