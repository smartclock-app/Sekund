package uk.dnpk.sekund.plugins.declarative

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.UrlPrefix
import uk.dnpk.sekund.protocol.declarative.DeclarativeDefinition
import uk.dnpk.sekund.protocol.declarative.SourceDefinition
import uk.dnpk.sekund.protocol.settings.FieldDescriptor
import uk.dnpk.sekund.sdk.auth.AuthAccess
import uk.dnpk.sekund.sdk.settings.AccountRef

/**
 * Runs a [SourceDefinition]: makes its request(s) and maps the answers into [MappedData].
 *
 * Steps for one run:
 * 1. With `forEach`, repeat everything below once per entry of the list setting.
 * 2. With `paginate`, request page after page until one comes back empty (or `maxPages`).
 * 3. Each request: fill in the URL, headers and body; refuse URLs outside the definition's `hosts`;
 *    send it (signed in, if the source has `auth`); read the answer as JSON.
 * 4. Map the answer: `map.fields` against the whole answer, `map.rows` against each list entry.
 *
 * @param http the plain client, for sources without `auth`.
 * @param auth gives signed-in clients for sources with `auth`. Credentials never pass through here.
 */
class SourceRunner(
    private val definition: DeclarativeDefinition,
    private val settings: JsonObject,
    private val http: HttpClient,
    private val auth: AuthAccess,
    private val templates: TemplateEngine,
) {
    /** Fetches and maps [source]. Throws with a readable message if anything goes wrong. */
    suspend fun run(source: SourceDefinition): MappedData {
        val forEach = source.forEach ?: return runPages(source, item = null)

        val list = settings[forEach.setting] as? JsonArray ?: return MappedData()
        var result = MappedData()
        for (item in list) {
            result = result.plus(runPages(source, item))
        }
        return result
    }

    private suspend fun runPages(source: SourceDefinition, item: JsonElement?): MappedData {
        val paginate = source.paginate ?: return map(source, request(source, item, page = null), item)

        var result = MappedData()
        var page: String? = paginate.start.toString()
        var pagesFetched = 0
        while (page != null && pagesFetched < paginate.maxPages) {
            val answer = request(source, item, page)
            val mapped = map(source, answer, item)
            // The fields come from the first page; later pages only add rows.
            result = if (pagesFetched == 0) mapped else MappedData(result.fields, result.rows + mapped.rows)
            pagesFetched += 1
            if (mapped.rows.isEmpty()) break
            page = nextPage(paginate.next, answer, paginate.start + pagesFetched)
        }
        return result
    }

    /** The next page's value: read from the answer if the definition says where, otherwise just counted. */
    private fun nextPage(nextPath: String?, answer: JsonElement, counted: Int): String? {
        if (nextPath == null) return counted.toString()
        val next = JsonPathLite.find(answer, nextPath)
        if (next == null || next is JsonNull) return null
        return TemplateEngine.textOf(next)
    }

    /** One HTTP request, answered as JSON. */
    private suspend fun request(source: SourceDefinition, item: JsonElement?, page: String?): JsonElement {
        val scope = TemplateScope(settings = settings, item = item, page = page)
        val url = templates.fill(source.url, scope, strict = true)
        checkHost(url)

        val client = clientFor(source)
        val body = source.body
        val response: HttpResponse = client.request(url) {
            method = HttpMethod.parse(source.method.uppercase())
            for ((name, template) in source.headers) {
                header(name, templates.fill(template, scope, strict = true))
            }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(templates.fill(body, scope, strict = true))
            }
        }

        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException("${hostOf(url)} answered ${response.status.value}: ${text.take(200)}")
        }
        try {
            return SekundJson.parseToJsonElement(text)
        } catch (error: SerializationException) {
            throw IllegalStateException("${hostOf(url)} didn't answer with JSON")
        }
    }

    /** Refuses a URL that isn't under one of the definition's `hosts`. */
    private fun checkHost(url: String) {
        val scope = TemplateScope(settings = settings)
        for (host in definition.hosts) {
            val allowed = templates.fill(host, scope)
            if (allowed.isNotEmpty() && UrlPrefix.matches(url, allowed)) return
        }
        throw IllegalStateException("Won't fetch $url: it isn't one of this widget's hosts")
    }

    /** The signed-in client for the source's provider, or the plain client if it has no `auth`. */
    fun clientFor(source: SourceDefinition): HttpClient {
        val provider = source.auth ?: return http
        return auth.client(accountFor(provider))
    }

    /** Checks an image URL against `hosts` and returns the client to fetch it with. */
    fun clientForImage(url: String): HttpClient {
        checkHost(url)
        return clientFor(definition.source)
    }

    /** The account picked in the first `account` setting for [provider]. */
    private fun accountFor(provider: String): AccountRef {
        val field = definition.settings.fields.firstOrNull { candidate -> candidate is FieldDescriptor.Account && candidate.provider == provider }
            ?: throw IllegalStateException("This widget has no account setting for '$provider'")
        val chosen = TemplateEngine.textOf(settings[field.key])
        if (chosen.isEmpty()) throw IllegalStateException("Pick a $provider account in this widget's settings")
        return AccountRef(provider = provider, accountId = chosen)
    }

    /** Applies the source's `map` to one answer. */
    private fun map(source: SourceDefinition, answer: JsonElement, item: JsonElement?): MappedData {
        val scope = TemplateScope(settings = settings, node = answer, item = item)
        val fields = linkedMapOf<String, String>()
        for ((name, template) in source.map.fields) {
            fields[name] = templates.fill(template, scope)
        }

        val rowsDefinition = source.map.rows ?: return MappedData(fields = fields)
        val rows = mutableListOf<Map<String, String>>()
        for (entry in entriesAt(answer, rowsDefinition.path)) {
            val rowScope = TemplateScope(settings = settings, node = entry, item = item, named = fields)
            val row = linkedMapOf<String, String>()
            for ((name, template) in rowsDefinition.fields) {
                row[name] = templates.fill(template, rowScope)
            }
            rows.add(row)
        }
        return MappedData(fields = fields, rows = rows)
    }

    /** The list at [path]; a single object counts as a list of one, anything else as empty. */
    private fun entriesAt(answer: JsonElement, path: String): List<JsonElement> {
        val found = JsonPathLite.find(answer, path) ?: return emptyList()
        if (found is JsonArray) return found
        if (found is JsonObject) return listOf(found)
        return emptyList()
    }

    private fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/')
}
