package uk.dnpk.sekund.runtime

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import uk.dnpk.sekund.protocol.declarative.DeclarativeDefinition
import uk.dnpk.sekund.runtime.auth.AccountsFile
import uk.dnpk.sekund.runtime.auth.AuthCodeSignIns
import uk.dnpk.sekund.runtime.auth.AuthProviderFiles
import uk.dnpk.sekund.runtime.auth.AuthRegistry
import uk.dnpk.sekund.runtime.auth.secrets.EncryptedFileSecretStore
import uk.dnpk.sekund.runtime.auth.secrets.SecretCipher
import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.runtime.config.ConfigRepository
import uk.dnpk.sekund.runtime.api.DeviceApi
import uk.dnpk.sekund.runtime.api.LanApiServer
import uk.dnpk.sekund.runtime.api.PlatformInfo
import uk.dnpk.sekund.runtime.discovery.AdvertisedService
import uk.dnpk.sekund.runtime.discovery.ServiceAdvertiser
import uk.dnpk.sekund.runtime.legacy.LegacyImport
import uk.dnpk.sekund.runtime.logging.LogBuffer
import uk.dnpk.sekund.runtime.logging.LogFile
import uk.dnpk.sekund.protocol.API_VERSION
import uk.dnpk.sekund.runtime.declarative.DeclarativeWidgets
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
 * @param secretCipher encrypts the secrets file. Platform-specific, so the entry point passes it in:
 *   `DesktopSecretCipher` on desktop, `AndroidKeystoreCipher` on Android.
 * @param declarativePlugin turns a declarative widget definition into a plugin (`::DeclarativePlugin`
 *   from plugins/declarative, which the runtime can't depend on directly).
 * @param builtInDefinitions the declarative definitions shipped with the app, as JSON text.
 * @param platform the platform's name, the app version and what the device can do.
 * @param advertiser announces the device API on the network (mDNS); null to not announce.
 */
class AppGraph(
    val paths: AppPaths,
    plugins: List<SekundPlugin>,
    private val defaultConfig: AppConfig,
    secretCipher: SecretCipher,
    declarativePlugin: (DeclarativeDefinition) -> SekundPlugin,
    builtInDefinitions: suspend () -> List<String> = { emptyList() },
    platform: PlatformInfo,
    private val advertiser: ServiceAdvertiser? = null,
) {
    private val log = Logger.withTag("App")

    /** The most recent log lines, for the device API (installed as a log writer in [start]). */
    val logBuffer: LogBuffer = LogBuffer()

    /** Lives as long as the app. Everything long-running is launched in here. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val registry: PluginRegistry = PluginRegistry(plugins)

    val config: ConfigRepository = ConfigRepository(paths)

    /** Declarative widgets and photo sources (JSON definitions), registered as extra plugins. */
    val declarative: DeclarativeWidgets = DeclarativeWidgets(paths.widgetsDir, registry, declarativePlugin, builtInDefinitions)

    val events: SharedEventBus = SharedEventBus()

    val alerts: AlertCenter = AlertCenter()

    /** One HTTP client for the whole app (it pools connections). */
    val http: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
    }

    /** Connected accounts and their (encrypted) credentials. */
    val auth: AuthRegistry = AuthRegistry(
        http = http,
        secrets = EncryptedFileSecretStore(Path(paths.secretsDir, "secrets.bin"), secretCipher),
        accountsFile = AccountsFile(paths.accountsFile),
        alerts = alerts,
        scope = scope,
        // Custom sign-ins (e.g. Amazon) are code that plugins provide.
        customHandlers = { name -> registry.authHandler(name) },
    )

    /** Browser sign-ins in progress (completed through the companion app). */
    val authCodeSignIns: AuthCodeSignIns = AuthCodeSignIns(auth)

    /** Loads auth provider definitions (built-in and the user's), and installs or removes the user's. */
    val authProviderFiles: AuthProviderFiles = AuthProviderFiles(paths.authProvidersDir, auth)

    /** Problems found in auth provider files, shown on the Accounts screen. */
    val authProblems: StateFlow<List<String>> = authProviderFiles.problems

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
            auth = auth,
            wakeUps = wakeUpsFrom(events),
            displayIsOn = displayIsOn,
        ),
        appScope = scope,
    )

    /** Imports the old Tauri app's config.json (at startup, or from the device API). */
    val legacyImport: LegacyImport = LegacyImport(paths, config, auth)

    /** The device API's logic: pairing, catalog, config edits, accounts... (served by [startApi]). */
    val api: DeviceApi = DeviceApi(this, platform)

    /**
     * Loads config.json and starts every instance and the device API. Returns straight away; loading
     * happens in the background. The returned job finishes when loading is done (tests wait for it).
     */
    fun start(): Job {
        Logger.addLogWriter(logBuffer)
        LogFile(paths.logDir).follow(logBuffer, scope)
        log.i { "Starting Sekund. Config folder: ${paths.configDir}" }
        for (problem in registry.problems) {
            log.w { "Plugin problem: $problem" }
        }
        return scope.launch {
            // Accounts and plugins first: instances check their providers and types when they're created.
            loadAuth()
            declarative.load()
            val imported = legacyImport.importFileIfLegacy()
            config.load { imported ?: defaultConfig }
            instances.follow(config.config)
            startApi()
        }
    }

    private suspend fun loadAuth() {
        authProviderFiles.load()
        try {
            auth.loadAccounts()
        } catch (error: IllegalStateException) {
            log.e(error) { "Couldn't read the connected accounts" }
        }
    }

    /** Starts the LAN API and announces it, if it's enabled in config.json. */
    private suspend fun startApi() {
        val settings = config.config.value.api
        if (!settings.enabled) {
            log.i { "The device API is turned off in config.json" }
            return
        }
        try {
            api.clients.load()
        } catch (error: IllegalStateException) {
            log.e(error) { "Not starting the device API: the paired clients file can't be read" }
            return
        }
        if (!LanApiServer(api, settings.port).start()) return
        advertiser?.advertise(
            AdvertisedService(
                name = settings.deviceName,
                port = settings.port,
                txt = mapOf("id" to api.identity.id, "name" to settings.deviceName, "api" to API_VERSION.toString(), "ver" to api.platform.appVersion),
            ),
        )
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
