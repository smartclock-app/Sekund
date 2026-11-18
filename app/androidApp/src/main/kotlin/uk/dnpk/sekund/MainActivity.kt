package uk.dnpk.sekund

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** The one and only screen. Shows the shared [App] using the graph owned by [SekundApplication]. */
class MainActivity : ComponentActivity() {
    private val app: SekundApplication
        get() = application as SekundApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        hideSystemBars()

        val graph = app.graph
        setContent {
            App(graph = graph)
        }
    }

    override fun onResume() {
        super.onResume()
        // As device owner, lock the device to Sekund (unless someone chose "Exit kiosk").
        app.kiosk?.enterIfWanted(this)
    }

    /**
     * Immersive full screen: no status or navigation bar. Swiping in from the edge shows them for a
     * moment (so Settings and Back stay reachable), then they hide again.
     */
    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
