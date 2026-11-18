package uk.dnpk.sekund

import uk.dnpk.sekund.plugins.declarativebuiltins.BuiltInDefinitions
import android.app.Application
import uk.dnpk.sekund.runtime.AndroidPaths
import uk.dnpk.sekund.runtime.AppGraph
import uk.dnpk.sekund.runtime.api.PlatformInfo
import uk.dnpk.sekund.runtime.discovery.NsdAdvertiser
import uk.dnpk.sekund.runtime.auth.secrets.AndroidKeystoreCipher

/**
 * Created once when the app process starts, before any screen. It owns the [AppGraph], so the app
 * keeps running (and instances keep their state) when Android recreates the screen, e.g. on rotation.
 */
class SekundApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(
            paths = AndroidPaths.resolve(this),
            plugins = BuiltInPlugins,
            declarativePlugin = ::declarativePlugin,
            builtInDefinitions = { BuiltInDefinitions.readAll() },
            defaultConfig = DefaultConfig.create(),
            secretCipher = AndroidKeystoreCipher(),
            platform = PlatformInfo(name = "android", appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"),
            advertiser = NsdAdvertiser(this),
        )
        graph.start()
    }
}
