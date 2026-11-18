package uk.dnpk.sekund

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The root of the UI, shown by both the Android and Desktop apps.
 *
 * Placeholder until the shell (main zone + sidebar) is built in phase 3.
 */
@Composable
fun App() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text("Sekund", color = Color.White)
    }
}
