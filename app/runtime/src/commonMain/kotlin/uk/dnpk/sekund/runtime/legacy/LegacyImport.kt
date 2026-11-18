package uk.dnpk.sekund.runtime.legacy

import co.touchlab.kermit.Logger
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.api.LegacyImportReport
import uk.dnpk.sekund.protocol.auth.AuthMethodDefinition
import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.runtime.AppPaths
import uk.dnpk.sekund.runtime.auth.AuthRegistry
import uk.dnpk.sekund.runtime.config.ConditionalUpdate
import uk.dnpk.sekund.runtime.config.ConfigRepository
import kotlin.time.Clock

/**
 * Brings settings over from the old Tauri app, in one of two ways:
 *
 * - **At startup:** if config.json is still in the old format (someone copied it over), it's moved to
 *   `backups/legacy-config-<time>.json` and replaced by the imported config ([importFileIfLegacy]).
 * - **From the companion:** `POST /v1/import/legacy` with the old file as the body ([importText]).
 *
 * Either way, credentials found in the old file become connected accounts (encrypted in the secret
 * store), and the new config.json only refers to them by id.
 */
class LegacyImport(
    private val paths: AppPaths,
    private val config: ConfigRepository,
    private val auth: AuthRegistry,
    private val fileSystem: FileSystem = SystemFileSystem,
    private val clock: Clock = Clock.System,
) {
    private val log = Logger.withTag("LegacyImport")

    /**
     * Call before loading config.json. If it's in the old format, imports it and returns the new config
     * to load instead; otherwise returns null and touches nothing.
     */
    suspend fun importFileIfLegacy(): AppConfig? {
        if (!fileSystem.exists(paths.configFile)) return null
        val text = fileSystem.source(paths.configFile).buffered().use { source -> source.readString() }
        val old = parseLegacy(text) ?: return null

        log.i { "config.json is from the old Sekund app; importing it" }
        fileSystem.createDirectories(paths.backupDir)
        val timestamp = clock.now().toString().replace(':', '-')
        fileSystem.atomicMove(paths.configFile, Path(paths.backupDir, "legacy-config-$timestamp.json"))

        val plan = LegacyImporter.plan(old)
        createAccounts(plan)
        for (note in plan.notes) log.w { "Import: $note" }
        return plan.config
    }

    /**
     * Imports an old config sent through the device API, replacing the current config (which is
     * backed up first).
     *
     * @throws IllegalArgumentException if [text] isn't an old-format config.
     */
    suspend fun importText(text: String): LegacyImportReport {
        val old = parseLegacy(text) ?: throw IllegalArgumentException("That isn't a config.json from the old Sekund app")
        val plan = LegacyImporter.plan(old)
        createAccounts(plan)
        config.backup()
        val etag = when (val saved = config.updateIfMatch(ifMatch = null) { plan.config }) {
            is ConditionalUpdate.Saved -> saved.etag
            is ConditionalUpdate.Conflict -> error("unreachable: no If-Match was given")
            ConditionalUpdate.FileBroken -> throw IllegalStateException("config.json can't be read, so the import wasn't saved. Fix or delete it first.")
        }
        return LegacyImportReport(
            instances = plan.config.instances.map { instance -> instance.id },
            accounts = plan.accounts.map { account -> account.id },
            notes = plan.notes,
            etag = etag,
        )
    }

    private suspend fun createAccounts(plan: LegacyImportPlan) {
        for (account in plan.accounts) {
            auth.importAccount(
                id = account.id,
                provider = account.provider,
                methodIndex = methodIndexFor(account.provider, account.methodType),
                label = account.label,
                fields = account.fields,
                credential = account.credential,
            )
        }
    }

    /** The index of the provider's first method of [methodType], or 0 if it isn't installed or has none. */
    private fun methodIndexFor(provider: String, methodType: String): Int {
        val definition = auth.provider(provider) ?: return 0
        for ((index, method) in definition.methods.withIndex()) {
            if (typeOf(method) == methodType) return index
        }
        return 0
    }

    private fun typeOf(method: AuthMethodDefinition): String = when (method) {
        is AuthMethodDefinition.ApiKey -> "apiKey"
        is AuthMethodDefinition.Bearer -> "bearer"
        is AuthMethodDefinition.OAuthDeviceCode -> "oauth2.deviceCode"
        is AuthMethodDefinition.OAuthAuthorizationCode -> "oauth2.authorizationCode"
        is AuthMethodDefinition.Custom -> "custom"
    }

    /** The old config as JSON, or null if [text] isn't one. */
    private fun parseLegacy(text: String): JsonObject? {
        val json = try {
            SekundJson.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return null
        }
        if (!LegacyImporter.isLegacy(json)) return null
        return json as JsonObject
    }
}
