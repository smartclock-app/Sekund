package uk.dnpk.sekund.sdk.settings

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import uk.dnpk.sekund.protocol.settings.ChoiceOption
import uk.dnpk.sekund.protocol.settings.FieldDescriptor
import uk.dnpk.sekund.protocol.settings.SettingsDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals

/** Settings known only as data (e.g. from a JSON widget definition) decode just like compiled ones. */
class DescriptorSettingsSchemaTest {
    private val schema = DescriptorSettingsSchema(
        SettingsDescriptor(
            fields = listOf(
                FieldDescriptor.Text(key = "city", label = "City", required = true),
                FieldDescriptor.Choice(
                    key = "units",
                    label = "Units",
                    options = listOf(ChoiceOption("metric", "Celsius"), ChoiceOption("imperial", "Fahrenheit")),
                    default = "metric",
                ),
                FieldDescriptor.Duration(key = "every", label = "Every", default = "15m"),
                FieldDescriptor.Account(key = "account", label = "Account", provider = "openweather"),
            ),
        ),
    )

    @Test
    fun goodValuesAreKeptAndNormalised() {
        val decoded = schema.decode(
            JsonObject(
                mapOf(
                    "city" to JsonPrimitive("Leeds"),
                    "units" to JsonPrimitive("IMPERIAL"),
                    "every" to JsonPrimitive("1h 30m"),
                    "account" to JsonPrimitive("acc_1"),
                ),
            ),
        )

        assertEquals(emptyList(), decoded.issues)
        assertEquals(JsonPrimitive("imperial"), decoded.value["units"])
        assertEquals(JsonPrimitive("1h 30m"), decoded.value["every"])
        assertEquals(JsonPrimitive("acc_1"), decoded.value["account"])
    }

    @Test
    fun badValuesFallBackToDefaultsWithIssues() {
        val decoded = schema.decode(JsonObject(mapOf("units" to JsonPrimitive("kelvin"))))

        assertEquals(setOf("city", "units"), decoded.issues.map { issue -> issue.path }.toSet())
        assertEquals(JsonPrimitive("metric"), decoded.value["units"])
        assertEquals(JsonNull, decoded.value["account"])
    }
}
