package uk.dnpk.sekund.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import uk.dnpk.sekund.runtime.updates.AvailableUpdate
import uk.dnpk.sekund.runtime.updates.InstallState
import uk.dnpk.sekund.ui.components.SekundCard
import uk.dnpk.sekund.ui.theme.Sekund

/** "Sekund X is available", with an Install button, then the download's progress. */
@Composable
fun UpdateCard(update: AvailableUpdate, install: InstallState, onInstall: () -> Unit) {
    SekundCard {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Sekund.theme.spacing)) {
            Text("Sekund ${update.version} is available", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val notes = update.notes
            if (notes != null) Text(notes, color = Sekund.theme.colors.onCardMuted)
            InstallLine(install, onInstall)
        }
    }
}

@Composable
private fun InstallLine(install: InstallState, onInstall: () -> Unit) {
    when (install) {
        is InstallState.Idle -> Button(onClick = onInstall) { Text("Install") }
        is InstallState.Downloading -> {
            val fraction = install.fraction
            if (fraction == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
        }
        is InstallState.Installing -> Text("Installing… Sekund will restart.")
        is InstallState.WaitingForUser -> Text("Finish the installation on screen.")
        is InstallState.NotSupported -> Text(install.reason, color = Sekund.theme.colors.onCardMuted)
        is InstallState.Failed -> {
            Text(install.message, color = Sekund.theme.colors.warning)
            Button(onClick = onInstall) { Text("Try again") }
        }
    }
}
