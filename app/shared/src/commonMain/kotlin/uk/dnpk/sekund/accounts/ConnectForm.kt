package uk.dnpk.sekund.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uk.dnpk.sekund.protocol.auth.AuthMethodDefinition
import uk.dnpk.sekund.protocol.auth.AuthProviderDefinition
import uk.dnpk.sekund.protocol.auth.ClientMode
import uk.dnpk.sekund.runtime.auth.AuthRegistry
import uk.dnpk.sekund.runtime.auth.ConnectedAccount
import uk.dnpk.sekund.runtime.auth.DeviceSignIn
import uk.dnpk.sekund.runtime.auth.DeviceSignInStatus
import uk.dnpk.sekund.runtime.services.AlertCenter
import uk.dnpk.sekund.ui.theme.Sekund

/**
 * Connects (or reconnects) one account of [definition].
 *
 * Shows the provider's extra fields (e.g. server address), then what its sign-in method needs:
 * - API key / token: a box to paste it in,
 * - device code: the user's own client id/secret if the provider needs them, then the code to enter,
 * - browser sign-in: a note that it's done from the companion app (a later phase).
 */
@Composable
fun ConnectForm(
    auth: AuthRegistry,
    alerts: AlertCenter,
    definition: AuthProviderDefinition,
    reconnect: ConnectedAccount?,
    onDone: () -> Unit,
) {
    val colors = Sekund.theme.colors
    val scope = rememberCoroutineScope()
    var methodIndex by remember { mutableStateOf(reconnect?.methodIndex ?: 0) }
    // Field values typed so far, keyed by field key. Pre-filled when reconnecting.
    val fieldValues = remember { startingFields(reconnect) }
    var secret by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var deviceSignIn by remember { mutableStateOf<DeviceSignIn?>(null) }

    // Leaving the form cancels a device sign-in that's still waiting.
    DisposableEffect(Unit) {
        onDispose { deviceSignIn?.cancel() }
    }

    /** Runs [action], showing its error message instead of crashing. */
    fun attempt(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Sekund.theme.spacing)) {
        val title = if (reconnect == null) "Connect ${definition.name}" else "Reconnect ${reconnect.label}"
        Text(title, color = colors.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        if (definition.methods.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(Sekund.theme.spacing)) {
                for ((index, method) in definition.methods.withIndex()) {
                    val label = methodName(method)
                    if (index == methodIndex) Button(onClick = {}) { Text(label) }
                    else OutlinedButton(onClick = { methodIndex = index }) { Text(label) }
                }
            }
        }

        for (field in definition.fields) {
            OutlinedTextField(
                value = fieldValues[field.key] ?: "",
                onValueChange = { value -> fieldValues[field.key] = value },
                label = { Text(field.label) },
                placeholder = { Text(field.placeholder ?: "") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val method = definition.methods[methodIndex]
        when (method) {
            is AuthMethodDefinition.ApiKey, is AuthMethodDefinition.Bearer, is AuthMethodDefinition.Custom -> {
                val help = keyHelp(method)
                if (help != null) Text(help, color = colors.onBackground)
                OutlinedTextField(
                    value = secret,
                    onValueChange = { value -> secret = value },
                    label = { Text(methodName(method)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(enabled = !busy && secret.isNotBlank(), onClick = {
                    attempt {
                        auth.connectWithKey(definition.id, methodIndex, fieldValues.toMap(), secret, reconnect?.id)
                        onDone()
                    }
                }) { Text("Connect") }
            }

            is AuthMethodDefinition.OAuthDeviceCode -> {
                val current = deviceSignIn
                if (current == null) {
                    if (method.client.mode != ClientMode.Fixed) {
                        Text("Create an app in your ${definition.name} developer settings and enter its details.", color = colors.onBackground)
                        OutlinedTextField(clientId, { value -> clientId = value }, label = { Text("Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            clientSecret, { value -> clientSecret = value }, label = { Text("Client secret") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Button(enabled = !busy, onClick = {
                        attempt {
                            deviceSignIn = DeviceSignIn.start(
                                registry = auth,
                                alerts = alerts,
                                providerId = definition.id,
                                methodIndex = methodIndex,
                                fields = fieldValues.toMap(),
                                clientId = clientId.ifBlank { null },
                                clientSecret = clientSecret.ifBlank { null },
                                reconnect = reconnect?.id,
                            )
                        }
                    }) { Text("Get a sign-in code") }
                } else {
                    DeviceCodeStatus(current, onDone = onDone)
                }
            }

            is AuthMethodDefinition.OAuthAuthorizationCode -> {
                Text(
                    "${definition.name} needs a web browser to sign in. Connect it from the Sekund companion app on your phone.",
                    color = colors.onBackground,
                )
            }
        }

        val shownError = error
        if (shownError != null) Text(shownError, color = colors.error)
        TextButton(onClick = onDone) { Text("Back") }
    }
}

/** The code to type in and where, until the sign-in finishes. */
@Composable
private fun DeviceCodeStatus(signIn: DeviceSignIn, onDone: () -> Unit) {
    val status by signIn.status.collectAsState()
    val colors = Sekund.theme.colors
    when (val current = status) {
        is DeviceSignInStatus.Waiting -> {
            Text("On your phone or computer, go to", color = colors.onBackground)
            Text(current.verificationUrl, color = colors.accent, fontSize = 22.sp)
            Text("and enter", color = colors.onBackground)
            Text(current.userCode, color = colors.onBackground, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
        is DeviceSignInStatus.Connected -> {
            Text("Connected ${current.account.label}.", color = colors.onBackground)
            Button(onClick = onDone) { Text("Done") }
        }
        is DeviceSignInStatus.Failed -> Text(current.reason, color = colors.error)
    }
}

/** The fields typed into the form, starting with the account's current values when reconnecting. */
private fun startingFields(reconnect: ConnectedAccount?): SnapshotStateMap<String, String> {
    val values = mutableStateMapOf<String, String>()
    if (reconnect != null) values.putAll(reconnect.fields)
    return values
}

private fun keyHelp(method: AuthMethodDefinition): String? = when (method) {
    is AuthMethodDefinition.ApiKey -> method.help
    is AuthMethodDefinition.Bearer -> method.help
    is AuthMethodDefinition.Custom -> method.help
    else -> null
}

private fun methodName(method: AuthMethodDefinition): String = when (method) {
    is AuthMethodDefinition.ApiKey -> method.label
    is AuthMethodDefinition.Bearer -> method.label
    is AuthMethodDefinition.OAuthDeviceCode -> "Sign in with a code"
    is AuthMethodDefinition.OAuthAuthorizationCode -> "Sign in with a browser"
    is AuthMethodDefinition.Custom -> method.label
}
