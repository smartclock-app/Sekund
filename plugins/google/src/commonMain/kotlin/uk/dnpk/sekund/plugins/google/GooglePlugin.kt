package uk.dnpk.sekund.plugins.google

import uk.dnpk.sekund.protocol.plugin.AuthRequirement
import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.protocol.plugin.Requirements
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.plugin.PluginRegistrar
import uk.dnpk.sekund.sdk.plugin.SekundPlugin

/** Google services. For now: Google Calendar as a calendar source. */
object GooglePlugin : SekundPlugin {
    /** The auth provider id (google.auth.json). */
    const val PROVIDER = "google"

    /** The calendar source's type id, as used in config.json (and by the legacy importer). */
    const val GOOGLE_CALENDAR = "google-calendar"

    override val manifest = PluginManifest(
        id = "google",
        name = "Google",
        version = "1.0.0",
        description = "Events from Google Calendar.",
        requires = Requirements(
            auth = listOf(AuthRequirement(provider = PROVIDER, scopes = listOf("https://www.googleapis.com/auth/calendar.readonly"))),
        ),
    )

    override fun register(registrar: PluginRegistrar) {
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.CalendarSources,
                type = GOOGLE_CALENDAR,
                name = "Google Calendar",
                description = "Upcoming events from a Google account's calendars.",
                settings = GoogleCalendarSettingsSchema,
                create = { context -> GoogleCalendarSource(context) },
            ),
        )
    }
}
