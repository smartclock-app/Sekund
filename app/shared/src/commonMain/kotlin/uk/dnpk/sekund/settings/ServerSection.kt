package uk.dnpk.sekund.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.runtime.relay.RelayStatus
import uk.dnpk.sekund.ui.theme.Sekund

/**
 * The optional Sekund server: its address, and how the link to it is doing. Saving an empty address
 * turns the link off.
 */
@Composable
internal fun ServerSection(
    config: AppConfig,
    status: RelayStatus,
    edit: ((AppConfig) -> AppConfig) -> Unit,
) {
    val colors = Sekund.theme.colors
    // What's typed, until Save; starts from (and resets to) what's saved.
    var typed by remember(config.server.url) { mutableStateOf(config.server.url ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = typed,
                onValueChange = { text -> typed = text },
                label = { Text("Server address") },
                placeholder = { Text("https://sekund.example.org") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                enabled = typed.trim() != (config.server.url ?: ""),
                onClick = {
                    val url = typed.trim().trimEnd('/').ifEmpty { null }
                    edit { current -> current.copy(server = current.server.copy(url = url)) }
                },
            ) {
                Text("Save")
            }
        }
        Text(describe(status), color = colors.onBackground, modifier = Modifier.fillMaxWidth())
    }
}

/** One line about the link, for people. */
private fun describe(status: RelayStatus): String = when (status) {
    RelayStatus.Off -> "Not using a server. It's optional: it lets the companion reach this screen from anywhere and gives instant calendar updates."
    is RelayStatus.Connecting -> "Connecting to ${status.serverUrl}…"
    is RelayStatus.WaitingToBeClaimed -> "Waiting to be claimed: enter ${status.code} in the companion app, signed in to the server."
    is RelayStatus.Connected -> "Connected. This screen belongs to ${status.owner}."
    is RelayStatus.Refused -> "The server refused this screen: ${status.reason}"
    is RelayStatus.Offline -> "Can't reach the server (${status.reason}). Trying again in ${status.retryIn.inWholeSeconds} s."
}
