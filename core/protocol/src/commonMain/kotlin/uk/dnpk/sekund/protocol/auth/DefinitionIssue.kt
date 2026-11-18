package uk.dnpk.sekund.protocol.auth

import kotlinx.serialization.Serializable

/**
 * A problem found in a definition file (e.g. an `*.auth.json`), reported instead of crashing.
 *
 * @property path where in the file, e.g. `"methods[0].tokenUrl"`. Empty for "the whole file".
 */
@Serializable
data class DefinitionIssue(
    val path: String,
    val message: String,
)
