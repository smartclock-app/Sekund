package uk.dnpk.sekund

import android.app.Application
import android.content.ComponentName
import uk.dnpk.sekund.plugins.declarativebuiltins.BuiltInDefinitions
import uk.dnpk.sekund.runtime.AndroidPaths
import uk.dnpk.sekund.runtime.AppGraph
import uk.dnpk.sekund.runtime.android.AndroidDisplayControl
import uk.dnpk.sekund.runtime.android.AndroidKiosk
import uk.dnpk.sekund.runtime.android.AndroidNetworkWatcher
import uk.dnpk.sekund.runtime.android.AndroidShortcuts
import uk.dnpk.sekund.runtime.android.AndroidUpdateInstaller
import uk.dnpk.sekund.runtime.android.AppScreen
import uk.dnpk.sekund.runtime.android.DeviceOwner
import uk.dnpk.sekund.runtime.api.PlatformInfo
import uk.dnpk.sekund.runtime.auth.secrets.AndroidKeystoreCipher
import uk.dnpk.sekund.runtime.discovery.NsdAdvertiser
import uk.dnpk.sekund.runtime.platform.PlatformServices

/**
 * Created once when the app process starts, before any screen. It owns the [AppGraph], so the app
 * keeps running (and instances keep their state) when Android recreates the screen, e.g. on rotation.
 */
class SekundApplication : Application() {
    lateinit var graph: AppGraph
        private set

    /** Kiosk mode, when Sekund is the device owner (MainActivity enters it each time it's shown). */
    var kiosk: AndroidKiosk? = null
        private set

    override fun onCreate() {
        super.onCreate()

        // Lets the platform services reach the current screen (MainActivity) when they need one.
        val screen = AppScreen()
        registerActivityLifecycleCallbacks(screen)

        val owner = DeviceOwner(this, admin = ComponentName(this, SekundDeviceAdminReceiver::class.java))
        // As device owner: kiosk apps, Home app, no lock screen, auto brightness and time. Otherwise nothing.
        owner.applyKioskPolicies(homeActivity = ComponentName(this, MainActivity::class.java))
        if (owner.isDeviceOwner()) {
            kiosk = AndroidKiosk(this, screen)
        }

        val shortcuts = AndroidShortcuts(this)
        val services = PlatformServices(
            display = AndroidDisplayControl(this, owner, screen),
            kiosk = kiosk,
            network = AndroidNetworkWatcher(this, owner),
            connectivityCheckUrl = PlatformServices.GOOGLE_CONNECTIVITY_CHECK,
            openBrowser = shortcuts::openBrowser,
            openSystemSettings = shortcuts::openSystemSettings,
            // Silent as device owner; otherwise Android asks on screen.
            updateInstaller = AndroidUpdateInstaller(this),
        )

        graph = AppGraph(
            paths = AndroidPaths.resolve(this),
            plugins = BuiltInPlugins,
            declarativePlugin = ::declarativePlugin,
            builtInDefinitions = { BuiltInDefinitions.readAll() },
            defaultConfig = DefaultConfig.create(),
            secretCipher = AndroidKeystoreCipher(),
            platform = PlatformInfo(name = "android", appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"),
            advertiser = NsdAdvertiser(this),
            services = services,
        )
        graph.start()
    }
}
