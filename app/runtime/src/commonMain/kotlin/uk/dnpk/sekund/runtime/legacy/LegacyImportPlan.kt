package uk.dnpk.sekund.runtime.legacy

import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.runtime.auth.Credential

/**
 * What importing an old config will do, worked out before anything is changed.
 *
 * @property config the new config.json.
 * @property accounts accounts to create from credentials found in the old file. Instance settings
 *   already refer to them by [ImportedAccount.id].
 * @property notes things a person should know: settings that couldn't be carried over, and why.
 */
data class LegacyImportPlan(
    val config: AppConfig,
    val accounts: List<ImportedAccount>,
    val notes: List<String>,
)

/** One account to create, with a credential taken from the old config. */
data class ImportedAccount(
    val id: String,
    val provider: String,
    /** The kind of sign-in method the credential belongs to, e.g. `"apiKey"`, to pick the method index. */
    val methodType: String,
    val label: String,
    val fields: Map<String, String>,
    val credential: Credential,
)
