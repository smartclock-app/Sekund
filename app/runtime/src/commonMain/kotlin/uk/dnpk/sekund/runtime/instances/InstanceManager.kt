package uk.dnpk.sekund.runtime.instances

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.protocol.settings.SettingsIssue
import uk.dnpk.sekund.runtime.auth.PluginAuthAccess
import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.protocol.config.InstanceConfig
import uk.dnpk.sekund.runtime.plugins.PluginRegistry
import uk.dnpk.sekund.runtime.services.FolderFileCache
import uk.dnpk.sekund.runtime.services.InstanceRefresher
import uk.dnpk.sekund.runtime.services.JsonFileStore
import uk.dnpk.sekund.runtime.services.TaggedPluginLog
import uk.dnpk.sekund.sdk.plugin.Contribution
import kotlin.time.Duration

/**
 * Keeps the running instances in step with config.json and the installed plugins.
 *
 * Whenever the config, the plugins (e.g. a declarative widget was installed) or the auth providers
 * change, it compares old and new:
 * - instances whose config and plugin are unchanged are kept as they are,
 * - new or changed instances are (re)created, and so are ones that failed last time (they may work now),
 * - removed instances are stopped (their coroutine scope is cancelled, their alert cleared).
 *
 * So editing one widget's settings only restarts that widget.
 */
class InstanceManager(
    private val registry: PluginRegistry,
    private val services: SharedServices,
    private val appScope: CoroutineScope,
) {
    private val log = Logger.withTag("Instances")
    private val current = MutableStateFlow<Map<String, RunningInstance>>(emptyMap())

    /** Every instance from config.json, by id. */
    val instances: StateFlow<Map<String, RunningInstance>> = current

    /**
     * Starts following [config]: creates instances now and updates them whenever the config, the plugin
     * registry or the auth providers change.
     */
    fun follow(config: StateFlow<AppConfig>) {
        appScope.launch {
            // combine ≈ a React effect with three dependencies: runs again when any of them changes.
            combine(config, registry.version, services.auth.providers) { latest, _, _ -> latest.instances }
                .collect { instances -> apply(instances) }
        }
    }

    /** Recreates every instance from scratch (the long-press menu's "Reload"). */
    fun reloadAll() {
        val configs = current.value.values.map { instance -> instance.config }
        stopAll()
        apply(configs)
    }

    /** Makes the running instances match [configs]. Public so tests can call it directly. */
    fun apply(configs: List<InstanceConfig>) {
        val before = current.value
        val after = linkedMapOf<String, RunningInstance>()

        for (config in configs) {
            if (after.containsKey(config.id)) {
                log.w { "Instance id '${config.id}' is used twice in config.json; the second is ignored" }
                continue
            }
            val existing = before[config.id]
            if (existing != null && isUnchanged(existing, config)) {
                after[config.id] = existing // unchanged: keep it running
            } else {
                if (existing != null) stop(existing)
                after[config.id] = create(config)
            }
        }

        for ((id, instance) in before) {
            if (!after.containsKey(id)) stop(instance)
        }

        current.value = after
    }

    /** True if [existing] can keep running for [config]: same settings, same plugin, and it didn't fail. */
    private fun isUnchanged(existing: RunningInstance, config: InstanceConfig): Boolean {
        if (existing is RunningInstance.Failed) return false
        if (existing.config != config) return false
        return existing.contribution === registry.contribution(config.type)
    }

    private fun stopAll() {
        for (instance in current.value.values) stop(instance)
        current.value = emptyMap()
    }

    private fun stop(instance: RunningInstance) {
        if (instance is RunningInstance.Ready) {
            instance.scope.cancel()
        }
        services.alerts.clear(instance.id)
        services.overlays.clear(instance.id)
    }

    private fun create(config: InstanceConfig): RunningInstance {
        val contribution = registry.contribution(config.type)
        if (contribution == null) {
            return RunningInstance.Failed(config, "No installed plugin provides '${config.type}'")
        }
        val unavailable = registry.unavailableReason(config.type, services.auth.providers.value.keys)
        if (unavailable != null) {
            return RunningInstance.Failed(config, unavailable, contribution)
        }
        return createFrom(config, contribution, registry.manifestOf(config.type))
    }

    /** A separate function so the contribution's settings type `S` is known while building its context. */
    private fun <T : Any, S> createFrom(config: InstanceConfig, contribution: Contribution<T, S>, manifest: PluginManifest?): RunningInstance {
        val decoded = contribution.settings.decode(config.settings)
        val issues = decoded.issues.toMutableList()
        val intervalOverride = parseInterval(config.refreshInterval, issues)

        // Each instance gets its own scope. SupervisorJob: one failing coroutine doesn't cancel the others.
        // The handler logs crashes in background work instead of taking the whole app down.
        val crashLogger = CoroutineExceptionHandler { _, error ->
            log.e(error) { "Background work in '${config.id}' crashed" }
        }
        val scope = CoroutineScope(appScope.coroutineContext + SupervisorJob(appScope.coroutineContext[Job]) + crashLogger)

        val context = RuntimeInstanceContext(
            instanceId = config.id,
            settings = decoded.value,
            scope = scope,
            http = services.http,
            auth = PluginAuthAccess(
                registry = services.auth,
                pluginId = manifest?.id ?: config.type,
                declaredProviders = declaredProviders(manifest),
            ),
            time = services.time,
            refresher = InstanceRefresher(
                scope = scope,
                instanceId = config.id,
                intervalOverride = intervalOverride,
                wakeUps = services.wakeUps,
                displayIsOn = services.displayIsOn,
                clock = services.clock,
            ),
            storage = JsonFileStore(Path(services.paths.storageDir, "${config.id}.json")),
            files = FolderFileCache(Path(services.paths.cacheDir, "instances", config.id)),
            alerts = services.alerts.forOwner(config.id),
            overlays = services.overlays.forOwner(config.id),
            events = services.events,
            log = TaggedPluginLog(config.id),
            instances = RuntimeInstanceLookup(running = current, scope = scope),
            webhooks = services.webhooks.forInstance(config.id),
        )

        try {
            val value = contribution.create(context)
            return RunningInstance.Ready(config, contribution.point, value, issues, scope, contribution, refreshNow = { context.refresher.refreshNow() })
        } catch (error: Exception) {
            log.e(error) { "Couldn't create '${config.id}'" }
            scope.cancel()
            return RunningInstance.Failed(config, error.message ?: "Couldn't start (${error::class.simpleName})", contribution)
        }
    }

    private fun declaredProviders(manifest: PluginManifest?): Set<String> {
        if (manifest == null) return emptySet()
        return manifest.requires.auth.map { requirement -> requirement.provider }.toSet()
    }

    private fun parseInterval(text: String?, issues: MutableList<SettingsIssue>): Duration? {
        if (text == null) return null
        val parsed = Duration.parseOrNull(text)
        if (parsed == null) {
            issues.add(SettingsIssue("refreshInterval", "'$text' isn't a duration like \"15m\"; using the plugin's default"))
        }
        return parsed
    }
}
