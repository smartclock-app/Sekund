package uk.dnpk.sekund.plugins.declarative

import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.sdk.context.TimePreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TemplateEngineTest {
    private val engine = TemplateEngine(
        lookups = mapOf("dir" to mapOf("N" to "North", "*" to "?")),
        timeZone = { TimeZone.UTC },
        preferences = { TimePreferences(use24Hour = false) },
    )
    private val answer = SekundJson.parseToJsonElement(
        """{ "temp": 7.25, "when": "2025-03-03T14:05:00Z", "wind": "N", "list": [1, 2, 3], "empty": "" }""",
    )
    private val scope = TemplateScope(
        settings = JsonObject(mapOf("city" to JsonPrimitive("Leeds"))),
        node = answer,
        named = mapOf("greeting" to "Hi"),
    )

    @Test
    fun fillsEveryKindOfPlaceholder() {
        assertEquals("Hi from Leeds: 7.25", engine.fill("{greeting} from {settings.city}: {$.temp}", scope))
        assertEquals("3", engine.fill("{$.list[-1]}", scope))
    }

    @Test
    fun filtersApplyLeftToRight() {
        assertEquals("7.3", engine.fill("{$.temp|number:1}", scope))
        assertEquals("3.6", engine.fill("{$.temp|divide:2|number:1}", scope))
        assertEquals("North / ?", engine.fill("{$.wind|lookup:dir} / {$.temp|lookup:dir}", scope))
        assertEquals("LEEDS", engine.fill("{settings.city|upper}", scope))
        assertEquals("none", engine.fill("{$.empty|default:none}", scope))
        assertEquals("a%20b", engine.fill("{x|default:a b|urlencode}", scope))
    }

    @Test
    fun datesAndTimes() {
        assertEquals("Mon 3 Mar at 2:05 PM", engine.fill("{$.when|date} at {$.when|time}", scope))
    }

    @Test
    fun missingValuesAreEmptyUnlessStrict() {
        assertEquals("[]", engine.fill("[{$.nope}]", scope))
        assertFailsWith<MissingValueException> { engine.fill("https://x/{settings.nope}", scope, strict = true) }
    }

    @Test
    fun singlePlaceholdersKeepTheirRawValue() {
        assertEquals(7.25, engine.number("{$.temp}", scope))
        assertNull(engine.value("{$.nope}", scope))
    }
}
