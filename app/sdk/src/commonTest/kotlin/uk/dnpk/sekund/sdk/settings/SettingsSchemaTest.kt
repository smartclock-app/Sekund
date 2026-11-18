package uk.dnpk.sekund.sdk.settings

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.settings.FieldDescriptor
import uk.dnpk.sekund.protocol.settings.SettingsIssue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// An example schema, written the way a plugin would write one.

private enum class Units { Metric, Imperial }

private data class Camera(val id: String, val seconds: Int)

private object CameraSchema : SettingsSchema<Camera>() {
    val id = text("id", label = "Camera entity", required = true)
    val seconds = number("seconds", label = "Show for (seconds)", default = 30, min = 5)

    override fun build(values: SettingsValues) = Camera(id = values[id], seconds = values[seconds])
}

private data class Example(
    val postcode: String,
    val units: Units,
    val showSeconds: Boolean,
    val refresh: kotlin.time.Duration,
    val accent: Color,
    val filters: List<String>,
    val cameras: List<Camera>,
)

private object ExampleSchema : SettingsSchema<Example>() {
    val postcode = text("postcode", label = "Postcode", required = true)
    val units = choice("units", label = "Units", options = Units.entries, default = Units.Metric)
    val showSeconds = toggle("showSeconds", label = "Show seconds", default = true)
    val refresh = duration("refresh", label = "Refresh every", default = 15.minutes, min = 1.minutes)
    val accent = color("accent", label = "Accent colour", default = Color.Red)
    val filters = textList("filters", label = "Hide events containing")
    val cameras = objectList("cameras", label = "Cameras", itemSchema = CameraSchema)

    override fun build(values: SettingsValues) = Example(
        postcode = values[postcode],
        units = values[units],
        showSeconds = values[showSeconds],
        refresh = values[refresh],
        accent = values[accent],
        filters = values[filters],
        cameras = values[cameras],
    )
}

private fun json(text: String): JsonObject = SekundJson.parseToJsonElement(text).jsonObject

class SettingsSchemaTest {

    @Test
    fun `valid settings decode without issues`() {
        val decoded = ExampleSchema.decode(
            json(
                """
                {
                  "postcode": "SW1A 1AA",
                  "units": "imperial",
                  "showSeconds": false,
                  "refresh": "1h 30m",
                  "accent": "#00FF00",
                  "filters": ["Bins"],
                  "cameras": [{ "id": "camera.door", "seconds": 10 }]
                }
                """,
            ),
        )

        assertEquals(emptyList(), decoded.issues)
        assertEquals("SW1A 1AA", decoded.value.postcode)
        assertEquals(Units.Imperial, decoded.value.units) // enum names ignore case
        assertEquals(false, decoded.value.showSeconds)
        assertEquals(90.minutes, decoded.value.refresh)
        assertEquals(Color(0xFF00FF00), decoded.value.accent)
        assertEquals(listOf("Bins"), decoded.value.filters)
        assertEquals(listOf(Camera("camera.door", 10)), decoded.value.cameras)
    }

    @Test
    fun `missing settings use their defaults, and only required ones are reported`() {
        val decoded = ExampleSchema.decode(null)

        assertEquals(listOf(SettingsIssue("postcode", "Required")), decoded.issues)
        assertEquals(Units.Metric, decoded.value.units)
        assertEquals(15.minutes, decoded.value.refresh)
    }

    @Test
    fun `invalid values fall back to defaults and are reported, without affecting other settings`() {
        val decoded = ExampleSchema.decode(
            json("""{ "postcode": "AB1 2CD", "units": "kelvin", "showSeconds": "yes", "refresh": "10s" }"""),
        )

        assertEquals("AB1 2CD", decoded.value.postcode) // the valid value is kept
        assertEquals(Units.Metric, decoded.value.units)
        assertEquals(true, decoded.value.showSeconds)
        assertEquals(15.minutes, decoded.value.refresh) // 10s is below the 1 minute minimum
        assertEquals(listOf("units", "showSeconds", "refresh"), decoded.issues.map { issue -> issue.path })
    }

    @Test
    fun `durations also accept a plain number of seconds`() {
        val decoded = ExampleSchema.decode(json("""{ "postcode": "x", "refresh": 120 }"""))

        assertEquals(120.seconds, decoded.value.refresh)
    }

    @Test
    fun `problems inside list items have precise paths`() {
        val decoded = ExampleSchema.decode(
            json("""{ "postcode": "x", "cameras": [{ "id": "a" }, { "id": "", "seconds": 1 }, "oops"] }"""),
        )

        assertEquals(2, decoded.value.cameras.size) // "oops" is skipped
        assertEquals(30, decoded.value.cameras[1].seconds)
        assertEquals(
            listOf("cameras[1].id", "cameras[1].seconds", "cameras[2]"),
            decoded.issues.map { issue -> issue.path },
        )
    }

    @Test
    fun `unknown keys are reported but don't break anything`() {
        val decoded = ExampleSchema.decode(json("""{ "postcode": "x", "colour": "blue" }"""))

        assertEquals(listOf(SettingsIssue("colour", "Unknown setting, ignored")), decoded.issues)
    }

    @Test
    fun `defaults produce JSON that decodes back to the same defaults`() {
        val defaults = ExampleSchema.defaults()

        assertEquals("15m", defaults.getValue("refresh").jsonPrimitive.content)
        assertEquals("#FF0000", defaults.getValue("accent").jsonPrimitive.content)
        assertEquals(listOf(SettingsIssue("postcode", "Required")), ExampleSchema.decode(defaults).issues)
    }

    @Test
    fun `describe lists every field in declaration order`() {
        val descriptor = ExampleSchema.describe()

        assertEquals(
            listOf("postcode", "units", "showSeconds", "refresh", "accent", "filters", "cameras"),
            descriptor.fields.map { field -> field.key },
        )
        val units = descriptor.fields[1] as FieldDescriptor.Choice
        assertEquals(listOf("Metric", "Imperial"), units.options.map { option -> option.value })
        val cameras = descriptor.fields[6] as FieldDescriptor.ObjectList
        assertEquals(listOf("id", "seconds"), cameras.item.fields.map { field -> field.key })
    }

    @Test
    fun `declaring the same key twice is a programming error`() {
        val error = assertFailsWith<IllegalArgumentException> {
            object : SettingsSchema<Unit>() {
                val first = text("name", label = "Name")
                val second = text("name", label = "Name again")
                override fun build(values: SettingsValues) = Unit
            }
        }
        assertTrue(error.message!!.contains("declared twice"))
    }
}
