package uk.dnpk.sekund.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uk.dnpk.sekund.runtime.auth.AuthRegistry
import uk.dnpk.sekund.runtime.auth.ConnectedAccount
import uk.dnpk.sekund.runtime.services.AlertCenter
import uk.dnpk.sekund.sdk.auth.AuthState
import uk.dnpk.sekund.ui.components.SekundCard
import uk.dnpk.sekund.ui.theme.Sekund

/**
 * Lists connected accounts (with their state) and the providers they can be added from.
 * Opened from the long-press menu.
 *
 * Tapping "Connect" (or "Reconnect" on an account that needs it) swaps the list for a [ConnectForm].
 *
 * @param problems broken auth provider files, so a typo in a user's `*.auth.json` is visible somewhere.
 */
@Composable
fun AccountsScreen(
    auth: AuthRegistry,
    alerts: AlertCenter,
    problems: List<String>,
    onClose: () -> Unit,
) {
    val accounts by auth.accounts.collectAsState()
    val providers by auth.providers.collectAsState()
    // Which form is open: null = the list. `reconnect` is set when fixing an existing account.
    var connecting by remember { mutableStateOf<ConnectTarget?>(null) }
    val scope = rememberCoroutineScope()
    val colors = Sekund.theme.colors

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sekund.theme.spacing * 2),
            verticalArrangement = Arrangement.spacedBy(Sekund.theme.spacing),
        ) {
            Text("Accounts", color = colors.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Bold)

            val target = connecting
            val targetProvider = if (target == null) null else providers[target.providerId]
            if (target != null && targetProvider != null) {
                ConnectForm(
                    auth = auth,
                    alerts = alerts,
                    definition = targetProvider,
                    reconnect = target.reconnect,
                    onDone = { connecting = null },
                )
            } else {
                if (accounts.isEmpty()) {
                    Text("No accounts connected yet.", color = colors.onBackground)
                }
                for (account in accounts) {
                    AccountRow(
                        auth = auth,
                        account = account,
                        providerName = providers[account.provider]?.name ?: account.provider,
                        onReconnect = { connecting = ConnectTarget(account.provider, account) },
                        onRemove = { scope.launch { auth.remove(account.id) } },
                    )
                }

                Text("Add an account", color = colors.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                for (definition in providers.values) {
                    OutlinedButton(onClick = { connecting = ConnectTarget(definition.id, null) }) {
                        Text(definition.name)
                    }
                }

                for (problem in problems) {
                    Text(problem, color = colors.warning)
                }
            }
        }
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
            Text("Close")
        }
    }
}

/** What the connect form is for: a new account of [providerId], or reconnecting [reconnect]. */
private data class ConnectTarget(val providerId: String, val reconnect: ConnectedAccount?)

@Composable
private fun AccountRow(
    auth: AuthRegistry,
    account: ConnectedAccount,
    providerName: String,
    onReconnect: () -> Unit,
    onRemove: () -> Unit,
) {
    val state by remember(account.id) { auth.state(account.id) }.collectAsState(AuthState.Valid)
    val colors = Sekund.theme.colors

    SekundCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sekund.theme.spacing)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(account.label, color = colors.onCard, fontWeight = FontWeight.Bold)
                Text("$providerName · ${describe(state)}", color = stateColor(state))
            }
            if (state is AuthState.NeedsReauth) {
                OutlinedButton(onClick = onReconnect) { Text("Reconnect") }
            }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

private fun describe(state: AuthState): String = when (state) {
    AuthState.Valid -> "Connected"
    AuthState.Refreshing -> "Refreshing…"
    is AuthState.NeedsReauth -> state.reason
    AuthState.NotFound -> "Missing"
}

@Composable
private fun stateColor(state: AuthState) = when (state) {
    is AuthState.NeedsReauth -> Sekund.theme.colors.error
    else -> Sekund.theme.colors.onCardMuted
}
