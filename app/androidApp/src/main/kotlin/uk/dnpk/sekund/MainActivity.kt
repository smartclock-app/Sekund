package uk.dnpk.sekund

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** The one and only screen. Shows the shared [App] using the graph owned by [SekundApplication]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val graph = (application as SekundApplication).graph
        setContent {
            App(graph = graph)
        }
    }
}
