package uk.dnpk.sekund.plugins.declarative

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import uk.dnpk.sekund.sdk.settings.AccountRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A weather widget like the one shipped later: one GET, a few fields, an emoji lookup. */
private const val WEATHER = """
{
  "manifest": { "id": "weather", "name": "Weather", "version": "1.0.0",
                "requires": { "auth": [{ "provider": "openweather" }] } },
  "locations": ["main", "sidebar"],
  "hosts": ["https://api.openweathermap.org/"],
  "settings": { "fields": [
    { "kind": "account", "key": "account", "label": "OpenWeather account", "provider": "openweather", "required": true },
    { "kind": "text", "key": "postcode", "label": "Postcode", "default": "LS1 4AP" },
    { "kind": "choice", "key": "units", "label": "Units", "default": "metric",
      "options": [{ "value": "metric", "label": "Celsius" }, { "value": "imperial", "label": "Fahrenheit" }] }
  ] },
  "source": {
    "url": "https://api.openweathermap.org/data/2.5/weather?zip={settings.postcode|urlencode},GB&units={settings.units}",
    "auth": "openweather",
    "map": { "fields": {
      "temp": "{$.main.temp|round}{settings.units|lookup:unitSymbol}",
      "icon": "{$.weather[0].icon|lookup:icons}",
      "place": "{$.name|default:Somewhere}"
    } }
  },
  "view": { "kind": "stat", "value": "{temp}", "icon": "{icon}", "label": "{place}" },
  "lookups": {
    "icons": { "01d": "☀️", "*": "☁️" },
    "unitSymbol": { "metric": "°C", "imperial": "°F" }
  }
}
"""

class SourceRunnerTest {
    @Test
    fun mapsAnAnswerWithFiltersAndLookups() = runTest {
        val definition = definitionOf(WEATHER)
        val signed = RecordingApi { respondJson("""{ "name": "Leeds", "main": { "temp": 11.6 }, "weather": [{ "icon": "01d" }] }""") }
        val auth = FakeAuth(signed.client)
        val settings = settingsFor(definition, JsonObject(mapOf("account" to JsonPrimitive("acc_1"))))
        val runner = SourceRunner(definition, settings, http = unusedClient(), auth = auth, templates = testTemplates(definition))

        val data = runner.run(definition.source)

        assertEquals(mapOf("temp" to "12°C", "icon" to "☀️", "place" to "Leeds"), data.fields)
        // The postcode's space is encoded, and the request went through the signed-in client.
        assertEquals("https://api.openweathermap.org/data/2.5/weather?zip=LS1%204AP,GB&units=metric", signed.requests.single().url.toString())
        assertEquals(listOf(AccountRef("openweather", "acc_1")), auth.requestedAccounts)
    }

    @Test
    fun refusesUrlsOutsideTheHosts() = runTest {
        val definition = definitionOf(WEATHER.replace("https://api.openweathermap.org/data", "https://evil.example.com/data"))
        val settings = settingsFor(definition, JsonObject(mapOf("account" to JsonPrimitive("acc_1"))))
        val runner = SourceRunner(definition, settings, unusedClient(), FakeAuth(unusedClient()), testTemplates(definition))

        val error = assertFailsWith<IllegalStateException> { runner.run(definition.source) }
        assertTrue(error.message!!.contains("isn't one of this widget's hosts"))
    }

    @Test
    fun asksForAnAccountWhenNoneIsPicked() = runTest {
        val definition = definitionOf(WEATHER)
        val runner = SourceRunner(definition, settingsFor(definition, JsonObject(emptyMap())), unusedClient(), FakeAuth(unusedClient()), testTemplates(definition))

        val error = assertFailsWith<IllegalStateException> { runner.run(definition.source) }
        assertEquals("Pick a openweather account in this widget's settings", error.message)
    }

    @Test
    fun forEachMakesOneRequestPerListEntryAndJoinsTheRows() = runTest {
        // Like Actual Budget: one balance call per configured account.
        val definition = definitionOf(
            """
            {
              "manifest": { "id": "balances", "name": "Balances", "version": "1" },
              "hosts": ["{settings.server}"],
              "settings": { "fields": [
                { "kind": "text", "key": "server", "label": "Server" },
                { "kind": "objectList", "key": "accounts", "label": "Accounts", "item": { "fields": [
                  { "kind": "text", "key": "id", "label": "Id" },
                  { "kind": "text", "key": "name", "label": "Name" }
                ] } }
              ] },
              "source": {
                "url": "{settings.server}/accounts/{item.id}/balance",
                "forEach": { "setting": "accounts" },
                "map": { "rows": { "path": "$", "fields": {
                  "name": "{item.name}",
                  "balance": "£{$.data|divide:100|number:2}"
                } } }
              },
              "view": { "kind": "keyValue", "rowLabel": "{name}", "rowValue": "{balance}" }
            }
            """,
        )
        val api = RecordingApi { request ->
            val pence = if (request.url.encodedPath.contains("/a1/")) 123456 else -500
            respondJson("""{ "data": $pence }""")
        }
        val accounts = JsonArray(
            listOf(
                JsonObject(mapOf("id" to JsonPrimitive("a1"), "name" to JsonPrimitive("Current"))),
                JsonObject(mapOf("id" to JsonPrimitive("a2"), "name" to JsonPrimitive("Credit card"))),
            ),
        )
        val settings = settingsFor(definition, JsonObject(mapOf("server" to JsonPrimitive("http://budget.local:5007"), "accounts" to accounts)))
        val runner = SourceRunner(definition, settings, api.client, FakeAuth(unusedClient()), testTemplates(definition))

        val data = runner.run(definition.source)

        assertEquals(
            listOf(
                mapOf("name" to "Current", "balance" to "£1234.56"),
                mapOf("name" to "Credit card", "balance" to "£-5.00"),
            ),
            data.rows,
        )
        assertEquals(2, api.requests.size)
    }

    @Test
    fun paginatesUntilThereIsNoNextPage() = runTest {
        // Like Immich's search: POST with the page in the body, the next page number in the answer.
        val definition = definitionOf(
            """
            {
              "manifest": { "id": "album", "name": "Album", "version": "1" },
              "kind": "photoSource",
              "hosts": ["https://photos.example.com/"],
              "source": {
                "url": "https://photos.example.com/api/search",
                "method": "POST",
                "body": "{ \"page\": {page}, \"size\": 2 }",
                "paginate": { "start": 1, "maxPages": 5, "next": "$.assets.nextPage" },
                "map": { "rows": { "path": "$.assets.items", "fields": {
                  "id": "{$.id}",
                  "url": "https://photos.example.com/api/assets/{$.id}/thumbnail"
                } } }
              }
            }
            """,
        )
        val api = RecordingApi { request ->
            when (bodyOf(request)) {
                """{ "page": 1, "size": 2 }""" -> respondJson("""{ "assets": { "items": [{ "id": "p1" }, { "id": "p2" }], "nextPage": "2" } }""")
                """{ "page": 2, "size": 2 }""" -> respondJson("""{ "assets": { "items": [{ "id": "p3" }], "nextPage": null } }""")
                else -> respondError(HttpStatusCode.BadRequest)
            }
        }
        val runner = SourceRunner(definition, JsonObject(emptyMap()), api.client, FakeAuth(unusedClient()), testTemplates(definition))

        val data = runner.run(definition.source)

        assertEquals(listOf("p1", "p2", "p3"), data.rows.map { row -> row["id"] })
        assertEquals(2, api.requests.size)
    }

    @Test
    fun aFailedRequestSaysWhichHostAndStatus() = runTest {
        val definition = definitionOf(WEATHER)
        val signed = RecordingApi { respondJson("""{ "message": "Invalid API key" }""", HttpStatusCode.Unauthorized) }
        val settings = settingsFor(definition, JsonObject(mapOf("account" to JsonPrimitive("acc_1"))))
        val runner = SourceRunner(definition, settings, unusedClient(), FakeAuth(signed.client), testTemplates(definition))

        val error = assertFailsWith<IllegalStateException> { runner.run(definition.source) }
        assertTrue(error.message!!.startsWith("api.openweathermap.org answered 401"))
    }

    /** A client that fails the test if it's used. */
    private fun unusedClient(): HttpClient = HttpClient(MockEngine { error("This client shouldn't be used") })
}
