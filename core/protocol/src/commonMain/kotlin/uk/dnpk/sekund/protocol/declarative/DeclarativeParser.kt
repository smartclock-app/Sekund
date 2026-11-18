package uk.dnpk.sekund.protocol.declarative

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.auth.DefinitionIssue
import uk.dnpk.sekund.protocol.settings.FieldDescriptor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Reads and checks declarative widget definitions (`*.widget.json`).
 *
 * Never throws: a broken definition gives a null [ParsedDeclarativeDefinition.definition] plus issues
 * saying what's wrong, so one bad file is skipped and reported instead of stopping the others.
 */
object DeclarativeParser {

    /** Lenient about hand-editing mistakes that don't change the meaning: comments and trailing commas. */
    @OptIn(ExperimentalSerializationApi::class)
    private val json: Json = Json(from = SekundJson) {
        allowComments = true
        allowTrailingComma = true
    }

    /** The filters the engine understands. Anything else is reported as a typo. */
    val KNOWN_FILTERS: Set<String> = setOf(
        "number", "round", "divide", "multiply", "lookup", "upper", "lower", "default", "date", "time", "urlencode", "convert",
    )

    private val LOCATIONS = setOf("main", "sidebar")

    fun parse(text: String): ParsedDeclarativeDefinition {
        val definition = try {
            json.decodeFromString(DeclarativeDefinition.serializer(), text)
        } catch (error: SerializationException) {
            return ParsedDeclarativeDefinition(null, listOf(DefinitionIssue("", error.message ?: "Not a valid definition")))
        } catch (error: IllegalArgumentException) {
            return ParsedDeclarativeDefinition(null, listOf(DefinitionIssue("", error.message ?: "Not a valid definition")))
        }
        val issues = validate(definition)
        if (issues.isEmpty()) return ParsedDeclarativeDefinition(definition, issues)
        return ParsedDeclarativeDefinition(null, issues)
    }

    /** Writes [definition] as indented JSON, the way it's saved to disk. */
    fun format(definition: DeclarativeDefinition): String =
        Json(from = SekundJson) { prettyPrint = true }.encodeToString(DeclarativeDefinition.serializer(), definition)

    /** Checks a definition that decoded fine for mistakes the JSON shape can't catch. Empty list = valid. */
    fun validate(definition: DeclarativeDefinition): List<DefinitionIssue> {
        val issues = mutableListOf<DefinitionIssue>()
        val manifest = definition.manifest
        val settingKeys = definition.settings.fields.map { field -> field.key }.toSet()

        if (!isValidId(manifest.id)) issues.add(DefinitionIssue("manifest.id", "Use lowercase letters, digits and dashes"))
        if (manifest.name.isBlank()) issues.add(DefinitionIssue("manifest.name", "Must not be empty"))

        if (definition.hosts.isEmpty()) issues.add(DefinitionIssue("hosts", "List at least one address requests may go to"))
        for ((index, host) in definition.hosts.withIndex()) {
            val looksLikeUrl = host.startsWith("https://") || host.startsWith("http://") || host.startsWith("{settings.")
            if (!looksLikeUrl) issues.add(DefinitionIssue("hosts[$index]", "Must start with https://, http:// or {settings.…}"))
            checkTemplate("hosts[$index]", host, definition, settingKeys, issues)
        }

        when (definition.kind) {
            DefinitionKind.Widget -> checkWidget(definition, settingKeys, issues)
            DefinitionKind.PhotoSource -> {
                val rows = definition.source.map.rows
                if (rows == null || !rows.fields.containsKey("url")) {
                    issues.add(DefinitionIssue("source.map.rows", "A photo source must map rows with a \"url\" field"))
                }
            }
        }

        checkSource("source", definition.source, definition, settingKeys, issues)
        for ((name, source) in definition.sources) {
            checkSource("sources.$name", source, definition, settingKeys, issues)
        }
        return issues
    }

    private fun checkWidget(definition: DeclarativeDefinition, settingKeys: Set<String>, issues: MutableList<DefinitionIssue>) {
        if (definition.locations.isEmpty()) issues.add(DefinitionIssue("locations", "List where the widget may go: \"main\", \"sidebar\""))
        for ((index, location) in definition.locations.withIndex()) {
            if (location !in LOCATIONS) issues.add(DefinitionIssue("locations[$index]", "Must be \"main\" or \"sidebar\""))
        }
        val view = definition.view
        if (view == null) {
            issues.add(DefinitionIssue("view", "A widget needs a view"))
        } else {
            checkView("view", view, definition, settingKeys, issues)
        }
        val expanded = definition.expanded
        if (expanded != null) {
            val sourceName = expanded.source
            if (sourceName != null && !definition.sources.containsKey(sourceName)) {
                issues.add(DefinitionIssue("expanded.source", "There's no source called '$sourceName' in \"sources\""))
            }
            checkView("expanded.view", expanded.view, definition, settingKeys, issues)
        }
    }

    private fun checkSource(
        path: String,
        source: SourceDefinition,
        definition: DeclarativeDefinition,
        settingKeys: Set<String>,
        issues: MutableList<DefinitionIssue>,
    ) {
        val method = source.method.uppercase()
        if (method != "GET" && method != "POST") issues.add(DefinitionIssue("$path.method", "Must be GET or POST"))
        checkTemplate("$path.url", source.url, definition, settingKeys, issues)
        val body = source.body
        if (body != null) checkTemplate("$path.body", body, definition, settingKeys, issues)
        for ((name, value) in source.headers) {
            checkTemplate("$path.headers.$name", value, definition, settingKeys, issues)
        }

        val refresh = Duration.parseOrNull(source.refresh)
        if (refresh == null) {
            issues.add(DefinitionIssue("$path.refresh", "Use a duration such as \"15m\""))
        } else if (refresh < 1.minutes) {
            issues.add(DefinitionIssue("$path.refresh", "Must be at least 1m"))
        }

        val auth = source.auth
        if (auth != null) checkAuth("$path.auth", auth, definition, issues)

        val forEach = source.forEach
        if (forEach != null) {
            val field = definition.settings.fields.firstOrNull { candidate -> candidate.key == forEach.setting }
            if (field !is FieldDescriptor.TextList && field !is FieldDescriptor.ObjectList) {
                issues.add(DefinitionIssue("$path.forEach.setting", "Must name a list setting"))
            }
        }

        val paginate = source.paginate
        if (paginate != null) {
            val usesPage = source.url.contains("{page") || (body?.contains("{page") ?: false)
            if (!usesPage) issues.add(DefinitionIssue("$path.paginate", "Put {page} in the url or body"))
            if (paginate.maxPages < 1) issues.add(DefinitionIssue("$path.paginate.maxPages", "Must be at least 1"))
        }

        for ((name, template) in source.map.fields) {
            checkTemplate("$path.map.fields.$name", template, definition, settingKeys, issues)
        }
        val rows = source.map.rows
        if (rows != null) {
            if (!rows.path.startsWith("$")) issues.add(DefinitionIssue("$path.map.rows.path", "Use a path such as \"$.items\""))
            for ((name, template) in rows.fields) {
                checkTemplate("$path.map.rows.fields.$name", template, definition, settingKeys, issues)
            }
        }
    }

    /** The provider must be required by the manifest, and there must be an account setting to pick the account. */
    private fun checkAuth(path: String, provider: String, definition: DeclarativeDefinition, issues: MutableList<DefinitionIssue>) {
        val declared = definition.manifest.requires.auth.any { requirement -> requirement.provider == provider }
        if (!declared) issues.add(DefinitionIssue(path, "Add '$provider' to manifest.requires.auth"))
        val hasAccountField = definition.settings.fields.any { field -> field is FieldDescriptor.Account && field.provider == provider }
        if (!hasAccountField) issues.add(DefinitionIssue(path, "Add an account setting for '$provider' so people can pick an account"))
    }

    private fun checkView(
        path: String,
        view: ViewDefinition,
        definition: DeclarativeDefinition,
        settingKeys: Set<String>,
        issues: MutableList<DefinitionIssue>,
    ) {
        for (template in templatesOf(view)) {
            checkTemplate(path, template, definition, settingKeys, issues)
        }
    }

    /** Every template text in a view, so they can all be checked the same way. */
    private fun templatesOf(view: ViewDefinition): List<String> {
        val result = mutableListOf<String>()
        when (view) {
            is ViewDefinition.Stat -> {
                result.add(view.value)
                addIfPresent(result, view.label)
                addIfPresent(result, view.icon)
                for (line in view.secondary) {
                    result.add(line.label)
                    result.add(line.value)
                }
            }
            is ViewDefinition.ListView -> {
                addIfPresent(result, view.title)
                result.add(view.rowTitle)
                addIfPresent(result, view.rowSubtitle)
                addIfPresent(result, view.rowTrailing)
                addIfPresent(result, view.rowIcon)
            }
            is ViewDefinition.KeyValue -> {
                addIfPresent(result, view.title)
                for (item in view.items) {
                    result.add(item.label)
                    result.add(item.value)
                }
                addIfPresent(result, view.rowLabel)
                addIfPresent(result, view.rowValue)
            }
            is ViewDefinition.Image -> {
                result.add(view.url)
                addIfPresent(result, view.caption)
            }
            is ViewDefinition.Progress -> {
                result.add(view.label)
                result.add(view.value)
                result.add(view.max)
                addIfPresent(result, view.detail)
            }
        }
        return result
    }

    private fun addIfPresent(list: MutableList<String>, value: String?) {
        if (value != null) list.add(value)
    }

    /** Checks references we can check without running anything: settings keys, filter names, lookup tables. */
    private fun checkTemplate(
        path: String,
        template: String,
        definition: DeclarativeDefinition,
        settingKeys: Set<String>,
        issues: MutableList<DefinitionIssue>,
    ) {
        for (placeholder in TemplateSyntax.placeholders(template)) {
            val value = placeholder.value
            if (value.startsWith("settings.")) {
                val key = value.removePrefix("settings.")
                if (key !in settingKeys) issues.add(DefinitionIssue(path, "Unknown setting {$value}"))
            }
            for (filter in placeholder.filters) {
                // Any filter argument may be a setting (`|convert:C:settings.units`): check it exists.
                for (argument in filter.arguments) {
                    if (argument.startsWith("settings.") && argument.removePrefix("settings.") !in settingKeys) {
                        issues.add(DefinitionIssue(path, "Unknown setting '$argument' in filter '${filter.name}'"))
                    }
                }
                if (filter.name == "convert") checkConvert(path, filter, issues)
                if (filter.name !in KNOWN_FILTERS) {
                    issues.add(DefinitionIssue(path, "Unknown filter '${filter.name}'. Known: ${KNOWN_FILTERS.joinToString()}"))
                }
                if (filter.name == "lookup") {
                    val table = filter.arguments.firstOrNull()
                    if (table == null || !definition.lookups.containsKey(table)) {
                        issues.add(DefinitionIssue(path, "No lookup table called '${table ?: ""}' in \"lookups\""))
                    }
                }
            }
        }
    }

    /** `convert` needs two units of the same kind; units taken from settings can only be checked when used. */
    private fun checkConvert(path: String, filter: Filter, issues: MutableList<DefinitionIssue>) {
        if (filter.arguments.size != 2) {
            issues.add(DefinitionIssue(path, "'convert' needs two units, e.g. convert:m/s:mph"))
            return
        }
        for (unit in filter.arguments) {
            if (!unit.startsWith("settings.") && unit !in UnitConversion.NAMES) {
                issues.add(DefinitionIssue(path, "Unknown unit '$unit'. Known: ${UnitConversion.NAMES.joinToString()}"))
            }
        }
    }

    private fun isValidId(id: String): Boolean = id.isNotEmpty() && id.all { char -> char in 'a'..'z' || char in '0'..'9' || char == '-' }
}

/** The result of [DeclarativeParser.parse]: the definition if it's usable, and every problem found. */
data class ParsedDeclarativeDefinition(
    val definition: DeclarativeDefinition?,
    val issues: List<DefinitionIssue>,
)
