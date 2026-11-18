package uk.dnpk.sekund.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import uk.dnpk.sekund.protocol.settings.FieldDescriptor
import uk.dnpk.sekund.protocol.settings.SettingsDefaults
import uk.dnpk.sekund.protocol.settings.SettingsDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsDefaultsTest {
    @Test
    fun everyFieldGetsItsDefault() {
        val descriptor = SettingsDescriptor(
            fields = listOf(
                FieldDescriptor.Text(key = "postcode", label = "Postcode", default = "LS1"),
                FieldDescriptor.Toggle(key = "metric", label = "Metric", default = true),
                FieldDescriptor.Account(key = "account", label = "Account", provider = "openweather"),
                FieldDescriptor.TextList(key = "filters", label = "Filters", default = listOf("a")),
            ),
        )

        val defaults = SettingsDefaults.of(descriptor)

        assertEquals(JsonPrimitive("LS1"), defaults["postcode"])
        assertEquals(JsonPrimitive(true), defaults["metric"])
        assertEquals(JsonNull, defaults["account"])
        assertEquals(JsonArray(listOf(JsonPrimitive("a"))), defaults["filters"])
    }
}
