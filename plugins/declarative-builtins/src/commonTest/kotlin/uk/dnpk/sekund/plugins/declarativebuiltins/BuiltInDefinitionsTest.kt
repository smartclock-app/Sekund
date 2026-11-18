package uk.dnpk.sekund.plugins.declarativebuiltins

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonObject
import uk.dnpk.sekund.plugins.declarative.SourceRunner
import uk.dnpk.sekund.plugins.declarative.TemplateEngine
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.declarative.DeclarativeDefinition
import uk.dnpk.sekund.protocol.declarative.DeclarativeParser
import uk.dnpk.sekund.sdk.auth.AuthAccess
import uk.dnpk.sekund.sdk.auth.AuthState
import uk.dnpk.sekund.sdk.context.TimePreferences
import uk.dnpk.sekund.sdk.settings.AccountRef
import uk.dnpk.sekund.sdk.settings.DescriptorSettingsSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The shipped definitions are real files, so test them like a user's: they must load cleanly and map
// realistic answers correctly.
class BuiltInDefinitionsTest {

    /** Signs nothing; just hands out the client that answers the test's requests. */
    private class PassThroughAuth(private val client: HttpClient) : AuthAccess {
        override fun state(account: AccountRef): Flow<AuthState> = flowOf(AuthState.Valid)
        override fun client(account: AccountRef): HttpClient = client
    }

    private suspend fun load(name: String): DeclarativeDefinition {
        val parsed = DeclarativeParser.parse(BuiltInDefinitions.read(name))
        assertEquals(emptyList(), parsed.issues, "$name has problems")
        return parsed.definition!!
    }

    /** Runs [definition]'s main source with [settingsJson], answering requests with [answer]. */
    private suspend fun run(definition: DeclarativeDefinition, settingsJson: String, answer: (String) -> String?, source: String? = null) =
        runner(definition, settingsJson, answer).run(if (source == null) definition.source else definition.sources.getValue(source))

    private val requested = mutableListOf<String>()

    private fun runner(definition: DeclarativeDefinition, settingsJson: String, answer: (String) -> String?): SourceRunner {
        val client = HttpClient(
            MockEngine { request ->
                val url = request.url.toString()
                requested.add(url)
                val body = answer(url)
                if (body == null) respondError(HttpStatusCode.NotFound) else respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        val settings = DescriptorSettingsSchema(definition.settings).decode(SekundJson.parseToJsonElement(settingsJson) as JsonObject).value
        val templates = TemplateEngine(definition.lookups, timeZone = { TimeZone.UTC }, preferences = { TimePreferences(use24Hour = true) })
        return SourceRunner(definition, settings, http = client, auth = PassThroughAuth(client), templates = templates)
    }

    @Test
    fun everyListedDefinitionLoadsWithoutProblems() = runTest {
        for (name in BuiltInDefinitions.FILE_NAMES) load(name)
    }

    @Test
    fun weatherShowsTheChosenUnits() = runTest {
        val weather = load("weather.widget.json")
        val answer = """{ "name": "Leeds", "main": { "temp": 11.6, "feels_like": 9.2, "humidity": 81 },
                          "wind": { "speed": 4.5 }, "weather": [{ "icon": "10d", "description": "light rain" }] }"""

        val uk = run(weather, """{ "account": "acc_1", "postcode": "LS1 4AP" }""", { answer })
        val us = run(weather, """{ "account": "acc_1", "postcode": "90210", "country": "US", "temperatureUnit": "F", "windUnit": "km/h" }""", { answer })

        // 4.5 m/s is 10 mph (the old app showed "5 mph": the m/s number with the wrong label).
        assertEquals(mapOf("temp" to "12°C", "feelsLike" to "9°C", "wind" to "10 mph", "humidity" to "81%", "summary" to "light rain", "icon" to "🌦️", "place" to "Leeds"), uk.fields)
        assertEquals("53°F", us.fields["temp"])
        assertEquals("16 km/h", us.fields["wind"])
        assertTrue(requested.first().endsWith("/data/2.5/weather?zip=LS1%204AP,GB&units=metric"), requested.first())
    }

    @Test
    fun weatherForecastHasOneRowPerStep() = runTest {
        val weather = load("weather.widget.json")
        val answer = """{ "list": [
            { "dt": 1750237200, "main": { "temp": 15 }, "wind": { "speed": 2 }, "weather": [{ "icon": "01d", "description": "clear sky" }] },
            { "dt": 1750248000, "main": { "temp": 17 }, "wind": { "speed": 3 }, "weather": [{ "icon": "03d", "description": "scattered clouds" }] } ] }"""

        val forecast = run(weather, """{ "account": "acc_1", "postcode": "LS1 4AP" }""", { answer }, source = "forecast")

        assertEquals(2, forecast.rows.size)
        assertEquals(mapOf("time" to "09:00", "icon" to "☀️", "summary" to "clear sky", "temp" to "15°C", "wind" to "4 mph"), forecast.rows[0])
    }

    @Test
    fun actualBudgetFetchesEachAccountsBalance() = runTest {
        val budget = load("actualbudget.widget.json")
        val settings = """{ "account": "acc_1", "serverUrl": "http://actual.local:5007", "currency": "£",
            "accounts": [ { "name": "Current", "syncId": "budget-1", "accountId": "acc-a" },
                          { "name": "Savings", "syncId": "budget-1", "accountId": "acc-b" } ] }"""

        val data = run(budget, settings, { url -> if (url.contains("acc-a")) """{ "data": 123456 }""" else """{ "data": -2050 }""" })

        assertEquals(listOf(mapOf("name" to "Current", "balance" to "£1234.56"), mapOf("name" to "Savings", "balance" to "£-20.50")), data.rows)
        assertEquals("http://actual.local:5007/v1/budgets/budget-1/accounts/acc-a/balance", requested.first())
    }

    @Test
    fun immichPagesThroughTheAlbumAndPointsAtPreviews() = runTest {
        val immich = load("immich.photos.json")
        val bodies = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                bodies.add((request.body as TextContent).text)
                val answer = if (bodies.size == 1) {
                    """{ "assets": { "items": [{ "id": "p1", "fileCreatedAt": "2024-05-01T10:00:00.000Z" }], "nextPage": "2" } }"""
                } else {
                    """{ "assets": { "items": [{ "id": "p2", "fileCreatedAt": "2024-05-02T10:00:00.000Z" }], "nextPage": null } }"""
                }
                respond(answer, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        val settings = DescriptorSettingsSchema(immich.settings)
            .decode(SekundJson.parseToJsonElement("""{ "account": "acc_1", "serverUrl": "http://immich.local:2283", "albumId": "alb-1" }""") as JsonObject).value
        val templates = TemplateEngine(immich.lookups, timeZone = { TimeZone.UTC }, preferences = { TimePreferences() })

        val data = SourceRunner(immich, settings, client, PassThroughAuth(client), templates).run(immich.source)

        assertEquals(listOf("p1", "p2"), data.rows.map { row -> row["id"] })
        assertEquals("http://immich.local:2283/api/assets/p1/thumbnail?size=preview", data.rows[0]["url"])
        assertEquals("""{ "albumIds": ["alb-1"], "type": "IMAGE", "page": 2, "size": 250 }""", bodies[1])
    }
}
