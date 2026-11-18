package uk.dnpk.sekund.protocol

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uk.dnpk.sekund.protocol.settings.ChoiceOption
import uk.dnpk.sekund.protocol.settings.FieldDescriptor
import uk.dnpk.sekund.protocol.settings.SettingsDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins down the JSON shape the companion app relies on. Changing it is a breaking API change. */
class SettingsDescriptorJsonTest {
    private val descriptor = SettingsDescriptor(
        fields = listOf(
            FieldDescriptor.Toggle(key = "showSeconds", label = "Show seconds", default = true),
            FieldDescriptor.Choice(
                key = "format",
                label = "Time format",
                options = listOf(ChoiceOption("12h", "12 hour"), ChoiceOption("24h", "24 hour")),
                default = "12h",
            ),
        ),
    )

    @Test
    fun `fields are tagged with their kind`() {
        val json = SekundJson.encodeToJsonElement(SettingsDescriptor.serializer(), descriptor)

        val fields = json.jsonObject.getValue("fields").jsonArray
        assertEquals("toggle", fields[0].jsonObject.getValue("kind").jsonPrimitive.content)
        assertEquals("choice", fields[1].jsonObject.getValue("kind").jsonPrimitive.content)
    }

    @Test
    fun `descriptor survives a round trip`() {
        val text = SekundJson.encodeToString(SettingsDescriptor.serializer(), descriptor)
        val decoded = SekundJson.decodeFromString(SettingsDescriptor.serializer(), text)

        assertEquals(descriptor, decoded)
    }
}
