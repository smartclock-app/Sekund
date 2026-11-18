package uk.dnpk.sekund

import android.app.Application
import uk.dnpk.sekund.runtime.AndroidPaths
import uk.dnpk.sekund.runtime.AppGraph
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
            defaultConfig = DefaultConfig.create(),
            secretCipher = AndroidKeystoreCipher(),
        )
        graph.start()
    }
}
