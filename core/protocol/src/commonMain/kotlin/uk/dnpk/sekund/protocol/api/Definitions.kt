package uk.dnpk.sekund.protocol.api

import kotlinx.serialization.Serializable
import uk.dnpk.sekund.protocol.auth.DefinitionIssue

/**
 * A declarative widget or auth provider definition as listed by `GET /v1/declarative-widgets` or
 * `GET /v1/auth-providers`. Fetch the full text with `GET .../{id}`.
 *
 * @property builtIn shipped with the app. A user file with the same id replaces it; deleting the user
 *   file brings the built-in one back.
 */
@Serializable
data class DefinitionSummary(
    val id: String,
    val name: String,
    val kind: String,
    val builtIn: Boolean,
)

/** The answer to installing a definition: it was saved, or it was refused and [issues] says why. */
@Serializable
data class DefinitionResult(
    val saved: Boolean,
    val issues: List<DefinitionIssue>,
)
