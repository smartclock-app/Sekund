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
import kotlinx.coroutines.flow.drop
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
import uk.dnpk.sekund.runtime.auth.secrets.SecretStore
import uk.dnpk.sekund.runtime.api.EventStream
import uk.dnpk.sekund.runtime.relay.LocalApi
import uk.dnpk.sekund.runtime.relay.RelayClient
import uk.dnpk.sekund.runtime.relay.RelayWebhooks
import uk.dnpk.sekund.runtime.relay.ServerSignIns
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
import uk.dnpk.sekund.runtime.services.OverlayCenter
import uk.dnpk.sekund.runtime.updates.AppUpdater
import uk.dnpk.sekund.runtime.updates.UpdateService
import uk.dnpk.sekund.runtime.updates.UpdateSources
import uk.dnpk.sekund.runtime.platform.updatePlatformKey
import uk.dnpk.sekund.runtime.services.SharedEventBus
import uk.dnpk.sekund.runtime.services.ConnectivityMonitor
import uk.dnpk.sekund.runtime.services.InternetCheck
import uk.dnpk.sekund.runtime.platform.DisplayControl
import uk.dnpk.sekund.runtime.platform.PlatformServices
import uk.dnpk.sekund.sdk.context.PresenceDetected
import uk.dnpk.sekund.sdk.context.on
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
 * @param services what this platform can do: display and kiosk control, network help, updates...
 *   Anything left out is simply not offered.
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
    val services: PlatformServices = PlatformServices(),
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

    /** Full-screen overlays from instances (e.g. a doorbell camera). The shell draws the top one. */
    val overlays: OverlayCenter = OverlayCenter()

    /** One HTTP client for the whole app (it pools connections). */
    val http: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
    }

    /** Tokens, keys and other secrets, encrypted (account credentials, the server's device token). */
    private val secrets: SecretStore = EncryptedFileSecretStore(Path(paths.secretsDir, "secrets.bin"), secretCipher)

    /** Connected accounts and their (encrypted) credentials. */
    val auth: AuthRegistry = AuthRegistry(
        http = http,
        secrets = secrets,
        accountsFile = AccountsFile(paths.accountsFile),
        alerts = alerts,
        scope = scope,
        // Custom sign-ins (e.g. Amazon) are code that plugins provide.
        customHandlers = { name -> registry.authHandler(name) },
        // Accounts that signed in with the Sekund server's own client are refreshed through it.
        serverRefresh = { provider, refreshToken -> serverSignIns.refresh(provider, refreshToken) },
    )

    /** Signing in with the Sekund server's own OAuth clients (when a server is connected). */
    val serverSignIns: ServerSignIns = ServerSignIns(auth, alerts)

    /** Browser sign-ins in progress (completed through the companion app). */
    val authCodeSignIns: AuthCodeSignIns = AuthCodeSignIns(auth)

    /** Loads auth provider definitions (built-in and the user's), and installs or removes the user's. */
    val authProviderFiles: AuthProviderFiles = AuthProviderFiles(paths.authProvidersDir, auth)

    /** Problems found in auth provider files, shown on the Accounts screen. */
    val authProblems: StateFlow<List<String>> = authProviderFiles.problems

    /** Whether the screen is on. Follows the platform's display control, where there is one. */
    val displayIsOn: MutableStateFlow<Boolean> = MutableStateFlow(true)

    /** Whether the internet works, for the offline indicator. Always true if the platform doesn't check. */
    val connectivity: ConnectivityMonitor? = connectivityMonitorFor(services)

    val isOnline: StateFlow<Boolean> = connectivity?.isOnline ?: MutableStateFlow(true)

    /** The user's time preferences, kept in step with config.json. */
    private val timePreferences: StateFlow<TimePreferences> = config.config
        .map { appConfig -> TimePreferences(use24Hour = appConfig.clock.use24Hour, showSeconds = appConfig.clock.showSeconds) }
        .stateIn(scope, SharingStarted.Eagerly, TimePreferences())

    val time: SystemTimeService = SystemTimeService(scope, timePreferences)

    /** New versions of the app: signed manifest, verified downloads, the platform's installer. */
    private val updateService: UpdateService = UpdateService(
        http = http,
        currentVersion = platform.appVersion,
        platformKey = updatePlatformKey(),
        downloadDir = Path(paths.cacheDir, "updates"),
        installer = services.updateInstaller,
        source = { UpdateSources.builtIn() },
    )

    /** Keeps the app up to date on a schedule, and drives the sidebar's update card. */
    val updater: AppUpdater = AppUpdater(updateService, config.config, scope)

    /** Webhook URLs through the Sekund server, for instances (uses [relay]). */
    private val webhooks: RelayWebhooks = RelayWebhooks()

    val instances: InstanceManager = InstanceManager(
        registry = registry,
        services = SharedServices(
            paths = paths,
            http = http,
            time = time,
            events = events,
            alerts = alerts,
            overlays = overlays,
            auth = auth,
            wakeUps = wakeUpsFrom(events),
            displayIsOn = displayIsOn,
            webhooks = webhooks,
        ),
        appScope = scope,
    )

    /** Imports the old Tauri app's config.json (at startup, or from the device API). */
    val legacyImport: LegacyImport = LegacyImport(paths, config, auth)

    /** The device API's logic: pairing, catalog, config edits, accounts... (served by [startApi]). */
    val api: DeviceApi = DeviceApi(this, withPlatformCapabilities(platform))

    /** The link to the optional Sekund server, set up in config.json's `server.url` (off without one). */
    val relay: RelayClient = RelayClient(
        deviceId = { api.identity.id },
        deviceName = { config.config.value.api.deviceName },
        appVersion = platform.appVersion,
        secrets = secrets,
        rpc = LocalApi(api, http),
        events = { EventStream.of(api) },
        alerts = alerts,
        scope = scope,
        features = listOf(webhooks, serverSignIns),
    )

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
            followDisplay()
            followNetwork()
            updater.start()
            startApi()
            relay.follow(config.config.map { appConfig -> appConfig.server.url })
        }
    }

    /** The device API tells companions what this device can do: what [services] provides, plus anything listed in [platform]. */
    private fun withPlatformCapabilities(platform: PlatformInfo): PlatformInfo {
        val all = (platform.capabilities + services.capabilities()).distinct()
        return platform.copy(capabilities = all)
    }

    private fun connectivityMonitorFor(services: PlatformServices): ConnectivityMonitor? {
        val checkUrl = services.connectivityCheckUrl ?: return null
        val network = services.network
        return ConnectivityMonitor(
            internetWorks = InternetCheck(http, checkUrl)::works,
            scope = scope,
            network = network?.state,
            tryToReconnect = { network?.tryToReconnect() ?: false },
        )
    }

    /**
     * Keeps [displayIsOn] in step with the real screen, tells plugins when it changes (instances refresh
     * when it comes back on), and turns the screen on when a plugin reports someone nearby.
     */
    private fun followDisplay() {
        val display = services.display ?: return
        scope.launch {
            display.isOn.collect { isOn ->
                val changed = displayIsOn.value != isOn
                displayIsOn.value = isOn
                if (changed) events.publish(DisplayChanged(isOn))
            }
        }
        scope.launch {
            events.on<PresenceDetected>().collect { event ->
                if (!display.isOn.value) {
                    log.i { "Someone's nearby (${event.source}): turning the screen on" }
                    turnDisplayOn(display)
                }
            }
        }
    }

    private suspend fun turnDisplayOn(display: DisplayControl) {
        try {
            display.turnOn()
        } catch (error: IllegalStateException) {
            log.w(error) { "Couldn't turn the screen on" }
        }
    }

    /** Starts the offline check, and tells plugins when the internet comes back or goes (instances refresh when it's back). */
    private fun followNetwork() {
        val monitor = connectivity ?: return
        monitor.start()
        scope.launch {
            // drop(1): the first value is the starting guess, not a change.
            monitor.isOnline.drop(1).collect { isOnline -> events.publish(NetworkChanged(isOnline)) }
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
