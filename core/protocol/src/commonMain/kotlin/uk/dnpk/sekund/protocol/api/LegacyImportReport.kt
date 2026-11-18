package uk.dnpk.sekund.protocol.api

import kotlinx.serialization.Serializable

/**
 * The answer to `POST /v1/import/legacy` (whose body is the old Tauri app's config.json).
 *
 * @property instances the ids of the instances created.
 * @property accounts the ids of the accounts created from credentials in the old file.
 * @property notes things that couldn't be carried over, and what to do about them.
 * @property etag the new config's ETag.
 */
@Serializable
data class LegacyImportReport(
    val instances: List<String>,
    val accounts: List<String>,
    val notes: List<String>,
    val etag: String,
)
