package uk.dnpk.sekund.plugins.trakt

import uk.dnpk.sekund.protocol.plugin.AuthRequirement
import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.protocol.plugin.Requirements
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.plugin.PluginRegistrar
import uk.dnpk.sekund.sdk.plugin.SekundPlugin

/** Trakt as a calendar source: when the shows and films you follow air. */
object TraktPlugin : SekundPlugin {
    /** The auth provider id (trakt.auth.json). */
    const val PROVIDER = "trakt"

    /** The source's type id, as used in config.json (and by the legacy importer). */
    const val TRAKT = "trakt"

    override val manifest = PluginManifest(
        id = "trakt",
        name = "Trakt",
        version = "1.0.0",
        description = "Air dates of shows and films from a Trakt list or watchlist.",
        requires = Requirements(auth = listOf(AuthRequirement(provider = PROVIDER))),
    )

    override fun register(registrar: PluginRegistrar) {
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.CalendarSources,
                type = TRAKT,
                name = "Trakt",
                description = "When the next episodes of your shows air, and when films are released.",
                settings = TraktSettingsSchema,
                create = { context -> TraktCalendarSource(context) },
            ),
        )
    }
}
