package uk.dnpk.sekund.sdk.settings

import uk.dnpk.sekund.protocol.settings.SettingsIssue

/**
 * The result of [SettingsSchema.decode]: always a usable [value], plus any [issues] found on the way.
 * An empty [issues] list means the settings were perfect.
 */
data class DecodedSettings<T>(
    val value: T,
    val issues: List<SettingsIssue>,
)
