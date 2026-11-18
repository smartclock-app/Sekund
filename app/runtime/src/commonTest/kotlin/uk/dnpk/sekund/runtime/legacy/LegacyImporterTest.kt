package uk.dnpk.sekund.runtime.legacy

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.protocol.config.Orientation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyImporterTest {
    private val old = SekundJson.parseToJsonElement(LEGACY_CONFIG) as JsonObject
    private val plan = LegacyImporter.plan(old)

    @Test
    fun recognisesOldConfigs() {
        assertTrue(LegacyImporter.isLegacy(old))
        assertFalse(LegacyImporter.isLegacy(SekundJson.parseToJsonElement("""{ "version": 1, "instances": [] }""")))
    }

    @Test
    fun coreSettingsKeepTheirMeaning() {
        assertEquals(Orientation.Portrait, plan.config.orientation)
        assertTrue(plan.config.clock.use24Hour)
        assertFalse(plan.config.clock.showSeconds)
        assertEquals(8081, plan.config.api.port)
        assertFalse(plan.config.api.enabled)
        assertEquals("Kitchen", plan.config.api.deviceName)
        assertEquals("photos", plan.config.clockFace)
    }

    @Test
    fun onlyWidgetsInUseBecomeInstances() {
        val ids = plan.config.instances.map { instance -> instance.id }
        assertEquals(listOf("clock", "calendar", "weather", "google", "trakt", "photos", "immich", "updater"), ids)
        assertEquals(listOf("weather"), plan.config.layout.main)
        assertEquals(listOf("calendar", "updater"), plan.config.layout.sidebar)
    }

    @Test
    fun secretsBecomeAccountsAndConfigOnlyHoldsTheirIds() {
        assertEquals(
            listOf("acc_legacy_openweather", "acc_legacy_google", "acc_legacy_trakt", "acc_legacy_immich"),
            plan.accounts.map { account -> account.id },
        )
        val google = plan.accounts.first { account -> account.provider == "google" }
        assertEquals("g-refresh", google.credential.refreshToken)
        assertEquals("g-client", google.credential.clientId)
        assertEquals("http://immich.local:2283", plan.accounts.first { account -> account.provider == "immich" }.fields["instance_url"])

        val weather = plan.config.instances.first { instance -> instance.id == "weather" }
        assertEquals(JsonPrimitive("acc_legacy_openweather"), weather.settings["account"])
        assertEquals(JsonPrimitive("C"), weather.settings["temperatureUnit"])
        assertEquals(JsonPrimitive("mph"), weather.settings["windUnit"])
        val configText = SekundJson.encodeToString(AppConfig.serializer(), plan.config)
        for (secret in listOf("ow-key", "g-access", "g-refresh", "g-secret", "t-refresh", "im-key", "hunter2")) {
            assertFalse(configText.contains(secret), "'$secret' must not end up in config.json")
        }
    }

    @Test
    fun calendarExtensionsBecomeItsSources() {
        val calendar = plan.config.instances.first { instance -> instance.id == "calendar" }
        assertEquals(JsonArray(listOf(JsonPrimitive("google"), JsonPrimitive("trakt"))), calendar.settings["sources"])
        assertEquals(JsonPrimitive("Bins"), calendar.settings["oddWeekTitle"])
    }

    @Test
    fun intervalsAreConvertedToDurations() {
        assertEquals("30m", plan.config.instances.first { instance -> instance.id == "updater" }.refreshInterval)
        assertEquals(JsonPrimitive("60s"), plan.config.instances.first { instance -> instance.id == "photos" }.settings["rotateEvery"])
    }

    @Test
    fun theOldPasswordIsMentionedNotCopied() {
        assertTrue(plan.notes.any { note -> note.contains("password") })
    }
}
