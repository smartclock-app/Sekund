package uk.dnpk.sekund.runtime.auth

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.auth.AuthMethodDefinition
import uk.dnpk.sekund.protocol.auth.AuthProviderDefinition
import uk.dnpk.sekund.protocol.auth.ClientAuth
import uk.dnpk.sekund.protocol.auth.DevicePollStatus
import uk.dnpk.sekund.runtime.services.AlertCenter
import uk.dnpk.sekund.sdk.context.Alert
import kotlin.time.Duration.Companion.seconds

/** Where a device-code sign-in is up to. The Accounts screen shows this. */
sealed class DeviceSignInStatus {
    /**
     * Waiting for the user to approve on another device.
     *
     * @property qrUrl what the QR code should open: the URL with the code already filled in, if the service gives one.
     */
    data class Waiting(val userCode: String, val verificationUrl: String, val qrUrl: String) : DeviceSignInStatus()

    data class Connected(val account: ConnectedAccount) : DeviceSignInStatus()

    data class Failed(val reason: String) : DeviceSignInStatus()
}

/**
 * One device-code sign-in (RFC 8628) in progress: "go to trakt.tv/activate and enter AB12-CD34".
 *
 * Created by [start]. While it waits, it polls the token endpoint in the background and shows a central
 * alert with the code, so the user can approve from their phone while looking at the wall display.
 */
class DeviceSignIn private constructor(
    initial: DeviceSignInStatus,
) {
    private val current = MutableStateFlow(initial)
    private var job: Job? = null
    private var alertOwner: String = ""
    private var alerts: AlertCenter? = null

    val status: StateFlow<DeviceSignInStatus> = current

    /** Stops waiting (the user closed the screen). */
    fun cancel() {
        job?.cancel()
        alerts?.clear(alertOwner)
        if (current.value is DeviceSignInStatus.Waiting) current.value = DeviceSignInStatus.Failed("Cancelled")
    }

    companion object {
        private val log = Logger.withTag("Auth")

        /**
         * Asks the service for a code, then starts polling in the background.
         *
         * @param clientId/clientSecret the user's own OAuth client, for `byo` providers.
         * @param reconnect the id of an existing account to update instead of adding one.
         * @throws IllegalStateException if the service refuses to give a code (e.g. a wrong client id).
         */
        suspend fun start(
            registry: AuthRegistry,
            alerts: AlertCenter,
            providerId: String,
            methodIndex: Int,
            fields: Map<String, String>,
            clientId: String?,
            clientSecret: String?,
            reconnect: String? = null,
        ): DeviceSignIn {
            val definition = registry.requireProvider(providerId)
            val method = definition.methods.getOrNull(methodIndex)
            if (method !is AuthMethodDefinition.OAuthDeviceCode) error("Method $methodIndex of $providerId isn't a device-code sign-in")

            // The user's client id/secret travel with the credential, so later refreshes can use them.
            val startingCredential = Credential(clientId = clientId?.trim(), clientSecret = clientSecret?.trim())
            val templates = AccountTemplates(definition, fields, startingCredential)

            val params = mutableMapOf<String, String>()
            if (method.scopes.isNotEmpty()) params["scope"] = method.scopes.joinToString(method.scopeSeparator)
            val answer = registry.tokenEndpoint.post(
                url = templates.fill(method.deviceCodeUrl),
                params = params,
                encoding = method.bodyEncoding,
                clientAuth = ClientAuth.None, // only the client id is needed here
                clientId = templates.clientId(),
                clientSecret = null,
            )
            if (!answer.isSuccess) error("${definition.name} didn't give a sign-in code (${answer.describe()})")
            val code = SekundJson.decodeFromString(DeviceCodeResponse.serializer(), answer.body)

            val verificationUrl = code.verificationUri ?: code.verificationUrl ?: error("${definition.name} didn't say where to enter the code")
            val waiting = DeviceSignInStatus.Waiting(
                userCode = code.userCode,
                verificationUrl = verificationUrl,
                qrUrl = code.verificationUriComplete ?: verificationUrl,
            )
            val signIn = DeviceSignIn(waiting)
            signIn.alerts = alerts
            signIn.alertOwner = "auth:signin:$providerId"
            alerts.show(
                signIn.alertOwner,
                Alert(title = "Sign in to ${definition.name}", subtitle = "Go to $verificationUrl and enter ${code.userCode}", qrCode = waiting.qrUrl),
            )

            signIn.job = registry.scope.launch {
                val result = poll(registry, definition, method, methodIndex, fields, templates, startingCredential, code, reconnect)
                alerts.clear(signIn.alertOwner)
                signIn.current.value = result
            }
            return signIn
        }

        /** Polls until the user approves, declines, or the code expires. */
        private suspend fun poll(
            registry: AuthRegistry,
            definition: AuthProviderDefinition,
            method: AuthMethodDefinition.OAuthDeviceCode,
            methodIndex: Int,
            fields: Map<String, String>,
            templates: AccountTemplates,
            startingCredential: Credential,
            code: DeviceCodeResponse,
            reconnect: String?,
        ): DeviceSignInStatus {
            var intervalSeconds = code.interval
            var waitedSeconds = 0L
            val params = mapOf(
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                method.deviceCodeParam to code.deviceCode,
            )

            while (waitedSeconds < code.expiresIn) {
                delay(intervalSeconds.seconds)
                waitedSeconds += intervalSeconds

                val answer = try {
                    registry.tokenEndpoint.post(
                        url = templates.fill(method.tokenUrl),
                        params = params,
                        encoding = method.bodyEncoding,
                        clientAuth = method.clientAuth,
                        clientId = templates.clientId(),
                        clientSecret = templates.clientSecret(),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    log.w(error) { "Polling ${definition.id} failed; trying again" } // e.g. Wi-Fi blip
                    continue
                }

                val tokens = answer.tokens()
                val accessToken = tokens.accessToken
                if (answer.isSuccess && accessToken != null) {
                    val credential = startingCredential.copy(
                        accessToken = accessToken,
                        refreshToken = tokens.refreshToken,
                        expiresAtEpochSeconds = registry.expiryFrom(tokens.expiresIn),
                    )
                    val account = registry.saveSignIn(definition, methodIndex, fields, credential, reconnect)
                    return DeviceSignInStatus.Connected(account)
                }

                when (pollStatus(method, answer)) {
                    DevicePollStatus.Pending -> Unit
                    DevicePollStatus.SlowDown -> intervalSeconds += 5 // what RFC 8628 asks for
                    DevicePollStatus.Expired -> return DeviceSignInStatus.Failed("The code expired. Try again.")
                    DevicePollStatus.Denied -> return DeviceSignInStatus.Failed("Sign-in was declined.")
                    DevicePollStatus.Invalid -> return DeviceSignInStatus.Failed("${definition.name} rejected the code (${answer.describe()}).")
                }
            }
            return DeviceSignInStatus.Failed("The code expired. Try again.")
        }

        /** Uses the definition's status-code map if it covers this status, else the standard error body. */
        internal fun pollStatus(method: AuthMethodDefinition.OAuthDeviceCode, answer: EndpointAnswer): DevicePollStatus {
            val mapped = method.pollStatusMap[answer.status.toString()]
            if (mapped != null) return mapped
            return when (answer.tokens().error) {
                "authorization_pending" -> DevicePollStatus.Pending
                "slow_down" -> DevicePollStatus.SlowDown
                "expired_token" -> DevicePollStatus.Expired
                "access_denied" -> DevicePollStatus.Denied
                else -> DevicePollStatus.Invalid
            }
        }
    }
}
