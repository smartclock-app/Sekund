package uk.dnpk.sekund.protocol.settings

import kotlinx.serialization.Serializable

/**
 * A problem found while reading someone's settings, e.g. "expected a number".
 *
 * Sekund never throws away a whole config because one value is wrong. Instead the bad value is replaced
 * by its default and an issue is recorded, so the UI and the companion can point at exactly what to fix.
 *
 * @property path where the problem is, e.g. `"cameras[1].aspectRatio"`.
 * @property message a human-readable explanation.
 */
@Serializable
data class SettingsIssue(
    val path: String,
    val message: String,
)
