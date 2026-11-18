package uk.dnpk.sekund.runtime

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.dnpk.sekund.runtime.config.AppConfig
import uk.dnpk.sekund.runtime.config.ConfigRepository
import uk.dnpk.sekund.runtime.instances.InstanceManager
import uk.dnpk.sekund.runtime.instances.SharedServices
import uk.dnpk.sekund.runtime.plugins.PluginRegistry
import uk.dnpk.sekund.runtime.services.AlertCenter
import uk.dnpk.sekund.runtime.services.SharedEventBus
import uk.dnpk.sekund.runtime.services.SystemTimeService
import uk.dnpk.sekund.sdk.context.DisplayChanged
import uk.dnpk.sekund.sdk.context.NetworkChanged
import uk.dnpk.sekund.sdk.context.SekundEvent
import uk.dnpk.sekund.sdk.context.TimePreferences
import uk.dnpk.sekund.sdk.plugin.SekundPlugin

/**
 * Builds and holds every long-lived object in the app.
 *
 * This is where the app is assembled, all in one place, with plain constructor calls. To find out
 * where something comes from, look here. If a dependency is missing, the code doesn't compile.
 * (We deliberately don't use a dependency-injection library.)
 *
 * Each platform's entry point creates one AppGraph, calls [start], and shows the UI.
 *
 * @param plugins the compiled-in plugins (BuiltInPlugins in :app:shared).
 * @param defaultConfig the config used when there's no config.json yet.
 */
class AppGraph(
    val paths: AppPaths,
    plugins: List<SekundPlugin>,
    private val defaultConfig: AppConfig,
) {
    private val log = Logger.withTag("App")

    /** Lives as long as the app. Everything long-running is launched in here. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val registry: PluginRegistry = PluginRegistry(plugins)

    val config: ConfigRepository = ConfigRepository(paths)

    val events: SharedEventBus = SharedEventBus()

    val alerts: AlertCenter = AlertCenter()

    /** One HTTP client for the whole app (it pools connections). */
    val http: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
    }

    /** Whether the screen is on. Platform display control (a later phase) will update this. */
    val displayIsOn: MutableStateFlow<Boolean> = MutableStateFlow(true)

    /** The user's time preferences, kept in step with config.json. */
    private val timePreferences: StateFlow<TimePreferences> = config.config
        .map { appConfig -> TimePreferences(use24Hour = appConfig.clock.use24Hour, showSeconds = appConfig.clock.showSeconds) }
        .stateIn(scope, SharingStarted.Eagerly, TimePreferences())

    val time: SystemTimeService = SystemTimeService(scope, timePreferences)

    val instances: InstanceManager = InstanceManager(
        registry = registry,
        services = SharedServices(
            paths = paths,
            http = http,
            time = time,
            events = events,
            alerts = alerts,
            wakeUps = wakeUpsFrom(events),
            displayIsOn = displayIsOn,
        ),
        appScope = scope,
    )

    /** Loads config.json and starts every instance. Returns straight away; loading happens in the background. */
    fun start() {
        log.i { "Starting Sekund. Config folder: ${paths.configDir}" }
        for (problem in registry.problems) {
            log.w { "Plugin problem: $problem" }
        }
        scope.launch {
            config.load { defaultConfig }
            instances.follow(config.config)
        }
    }

    /** Events that mean "refresh now": the network came back or the display turned on. */
    private fun wakeUpsFrom(events: SharedEventBus): Flow<Unit> = events.all
        .filter { event -> isWakeUp(event) }
        .map { }

    private fun isWakeUp(event: SekundEvent): Boolean {
        if (event is NetworkChanged) return event.isOnline
        if (event is DisplayChanged) return event.isOn
        return false
    }
}
