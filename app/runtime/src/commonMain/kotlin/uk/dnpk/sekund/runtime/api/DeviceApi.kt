package uk.dnpk.sekund.runtime.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import uk.dnpk.sekund.protocol.API_VERSION
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.api.ConfigSaved
import uk.dnpk.sekund.protocol.api.DefinitionResult
import uk.dnpk.sekund.protocol.api.DefinitionSummary
import uk.dnpk.sekund.protocol.api.DeviceInfo
import uk.dnpk.sekund.protocol.api.DevicePaths
import uk.dnpk.sekund.protocol.api.InstanceStatus
import uk.dnpk.sekund.protocol.api.InstanceUpdate
import uk.dnpk.sekund.protocol.api.LegacyImportReport
import uk.dnpk.sekund.protocol.api.NewInstance
import uk.dnpk.sekund.protocol.auth.AuthProviderDefinition
import uk.dnpk.sekund.protocol.auth.DefinitionIssue
import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.protocol.config.InstanceConfig
import uk.dnpk.sekund.protocol.config.LayoutConfig
import uk.dnpk.sekund.protocol.declarative.DefinitionKind
import uk.dnpk.sekund.runtime.AppGraph
import uk.dnpk.sekund.runtime.config.ConditionalUpdate
import uk.dnpk.sekund.runtime.config.ConfigEdits
import uk.dnpk.sekund.runtime.config.JsonMergePatch
import uk.dnpk.sekund.runtime.auth.randomUrlSafe
import uk.dnpk.sekund.runtime.instances.RunningInstance

/**
 * What the device API can do, without the HTTP part: the routes in [deviceApiModule] only read the
 * request, call one function here, and send back what it returns. Errors are thrown as [ApiProblem]s.
 *
 * Keeping the logic here (rather than in the routes) makes it readable on its own and easy to test.
 */
class DeviceApi(
    val graph: AppGraph,
    val platform: PlatformInfo,
) {
    val clients = ApiClients(graph.paths.apiClientsFile)
    val identity = DeviceIdentity(graph.paths.deviceFile)
    val pairing = Pairing(clients, graph.alerts, deviceId = { identity.id })
    val accounts = AccountFlows(graph.auth, graph.authCodeSignIns, graph.alerts, graph.serverSignIns)
    val actions = DeviceActions(graph.instances, graph.updater, graph.services)

    /**
     * A token made up fresh at every start, accepted like a paired client's. Only the relay client
     * knows it: it's how requests that came through the Sekund server reach the API (over loopback).
     */
    val localToken: String = randomUrlSafe(32)

    // ---- Device and catalog ---------------------------------------------------------------------

    fun deviceInfo(): DeviceInfo = DeviceInfo(
        id = identity.id,
        name = graph.config.config.value.api.deviceName,
        appVersion = platform.appVersion,
        apiVersion = API_VERSION,
        platform = platform.name,
        capabilities = platform.capabilities,
        paths = DevicePaths(
            config = graph.paths.configDir.toString(),
            data = graph.paths.dataDir.toString(),
            logs = graph.paths.logDir.toString(),
        ),
        serverSignIns = graph.serverSignIns.providers.value,
    )

    fun catalog() = CatalogBuilder.build(
        registry = graph.registry,
        auth = graph.auth,
        problems = graph.declarative.problems.value + graph.authProviderFiles.problems.value,
    )

    // ---- Config ---------------------------------------------------------------------------------

    /** Replaces the whole config (`PUT /v1/config`). */
    suspend fun replaceConfig(ifMatch: String?, config: AppConfig): ConfigSaved = save(ifMatch) { config }

    /** Applies a JSON Merge Patch to the config (`PATCH /v1/config`). */
    suspend fun patchConfig(ifMatch: String?, patch: JsonElement): ConfigSaved = save(ifMatch) { current ->
        val merged = JsonMergePatch.apply(SekundJson.encodeToJsonElement(AppConfig.serializer(), current), patch)
        try {
            SekundJson.decodeFromJsonElement(AppConfig.serializer(), merged)
        } catch (error: SerializationException) {
            throw ApiProblem(422, "invalid", "The patched config isn't valid: ${error.message}")
        } catch (error: IllegalArgumentException) {
            throw ApiProblem(422, "invalid", "The patched config isn't valid: ${error.message}")
        }
    }

    suspend fun addInstance(request: NewInstance): InstanceConfig {
        val contribution = graph.registry.contribution(request.type) ?: throw ApiProblem.badRequest("No installed plugin provides '${request.type}'")
        val location = request.location?.let { name -> CatalogBuilder.locationOf(name) ?: throw ApiProblem.badRequest("location must be \"main\" or \"sidebar\"") }
        if (location != null && location !in contribution.widgetLocations) {
            throw ApiProblem.badRequest("'${request.type}' can't be placed in the ${request.location}")
        }
        var created: InstanceConfig? = null
        save(ifMatch = null) { current ->
            val id = request.id ?: ConfigEdits.newInstanceId(current, request.type)
            if (current.instances.any { instance -> instance.id == id }) throw ApiProblem.conflict("An instance called '$id' already exists")
            val settings = JsonObject(contribution.settings.defaults() + (request.settings ?: JsonObject(emptyMap())))
            val instance = InstanceConfig(id = id, type = request.type, settings = settings, refreshInterval = request.refreshInterval)
            created = instance
            ConfigEdits.addInstance(current, instance, location)
        }
        return created ?: error("unreachable: save always runs the change")
    }

    suspend fun updateInstance(id: String, ifMatch: String?, update: InstanceUpdate): ConfigSaved = save(ifMatch) { current ->
        if (current.instances.none { instance -> instance.id == id }) throw ApiProblem.notFound("No instance called '$id'")
        ConfigEdits.setSettings(current, id, update.settings, update.refreshInterval)
    }

    suspend fun deleteInstance(id: String, ifMatch: String?): ConfigSaved = save(ifMatch) { current ->
        if (current.instances.none { instance -> instance.id == id }) throw ApiProblem.notFound("No instance called '$id'")
        ConfigEdits.removeInstance(current, id)
    }

    suspend fun setLayout(ifMatch: String?, layout: LayoutConfig): ConfigSaved = save(ifMatch) { current -> current.copy(layout = layout) }

    /**
     * Every config change from the API goes through here: backs up the file, checks `If-Match`, saves,
     * and answers with the new ETag and any settings problems.
     */
    private suspend fun save(ifMatch: String?, change: (AppConfig) -> AppConfig): ConfigSaved {
        graph.config.backup()
        return when (val result = graph.config.updateIfMatch(ifMatch, change)) {
            is ConditionalUpdate.Saved -> ConfigSaved(result.etag, ConfigIssues.of(result.config, graph.registry))
            is ConditionalUpdate.Conflict -> throw ApiProblem(412, "precondition_failed", "The config changed since you read it (now ${result.currentEtag}). Fetch it again.")
            ConditionalUpdate.FileBroken -> throw ApiProblem(409, "conflict", graph.config.loadProblem.value ?: "config.json can't be read")
        }
    }

    fun instanceStatuses(): List<InstanceStatus> {
        val running = graph.instances.instances.value
        return graph.config.config.value.instances.map { config ->
            when (val instance = running[config.id]) {
                null -> InstanceStatus(config, running = false, problem = "Not started yet")
                is RunningInstance.Ready -> InstanceStatus(config, running = true, issues = instance.issues)
                is RunningInstance.Failed -> InstanceStatus(config, running = false, problem = instance.reason)
            }
        }
    }

    /** Replaces the config with one imported from the old app's config.json ([text]). */
    suspend fun importLegacy(text: String): LegacyImportReport {
        try {
            return graph.legacyImport.importText(text)
        } catch (notLegacy: IllegalArgumentException) {
            throw ApiProblem(422, "invalid", notLegacy.message ?: "Not an old Sekund config")
        } catch (broken: IllegalStateException) {
            throw ApiProblem(409, "conflict", broken.message ?: "config.json can't be read")
        }
    }

    // ---- Declarative widgets --------------------------------------------------------------------

    fun declarativeWidgets(): List<DefinitionSummary> = graph.declarative.definitions.value.map { loaded ->
        val kind = when (loaded.definition.kind) {
            DefinitionKind.Widget -> "widget"
            DefinitionKind.PhotoSource -> "photoSource"
        }
        DefinitionSummary(loaded.id, loaded.definition.manifest.name, kind, loaded.isBuiltIn)
    }

    fun declarativeWidgetText(id: String): String =
        graph.declarative.find(id)?.text ?: throw ApiProblem.notFound("No declarative widget called '$id'")

    suspend fun putDeclarativeWidget(id: String, text: String): DefinitionResult {
        val parsed = graph.declarative.put(id, text)
        return resultOf(parsed.definition != null, parsed.issues)
    }

    suspend fun deleteDeclarativeWidget(id: String) {
        if (!graph.declarative.delete(id)) throw ApiProblem.notFound("No user definition called '$id' (built-in ones can't be deleted)")
    }

    // ---- Auth providers -------------------------------------------------------------------------

    fun authProviders(): List<DefinitionSummary> = graph.auth.providers.value.values.map { definition ->
        DefinitionSummary(definition.id, definition.name, "authProvider", builtIn = !graph.authProviderFiles.isUserFile(definition.id))
    }

    /** The provider's definition: the user's file as written if there is one, otherwise the parsed definition. */
    fun authProviderText(id: String): String {
        val userText = graph.authProviderFiles.userFileText(id)
        if (userText != null) return userText
        val definition = graph.auth.provider(id) ?: throw ApiProblem.notFound("No auth provider called '$id'")
        return SekundJson.encodeToString(AuthProviderDefinition.serializer(), definition)
    }

    suspend fun putAuthProvider(id: String, text: String): DefinitionResult {
        val parsed = graph.authProviderFiles.put(id, text)
        return resultOf(parsed.definition != null, parsed.issues)
    }

    suspend fun deleteAuthProvider(id: String) {
        if (!graph.authProviderFiles.delete(id)) throw ApiProblem.notFound("No user definition called '$id' (built-in ones can't be deleted)")
    }

    /** Saved, or refused with the issues as details (HTTP 422). */
    private fun resultOf(saved: Boolean, issues: List<DefinitionIssue>): DefinitionResult {
        if (saved) return DefinitionResult(saved = true, issues = issues)
        val details = SekundJson.encodeToJsonElement(ListSerializer(DefinitionIssue.serializer()), issues)
        throw ApiProblem(422, "invalid", "The definition has problems; see details", details)
    }
}
