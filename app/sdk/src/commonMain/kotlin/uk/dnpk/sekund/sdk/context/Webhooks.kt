package uk.dnpk.sekund.sdk.context

import kotlinx.coroutines.flow.Flow

/**
 * Public URLs that other services can call to tell this instance something changed, e.g. Google
 * Calendar's push notifications. They only exist when the device is connected to a Sekund server that
 * relays webhooks (see docs/server.md); without one, keep polling.
 *
 * ```kotlin
 * context.scope.launch {
 *     context.webhooks.url("calendar").collect { url ->
 *         if (url != null) api.watch(address = url)   // tell the service where to call
 *     }
 * }
 * context.scope.launch {
 *     context.webhooks.calls("calendar").collect { context.refresher.refreshNow() }
 * }
 * ```
 */
interface Webhooks {
    /**
     * The public URL for this instance's hook called [name], or null while there's no server to receive
     * it. The same instance and name normally keep the same URL, even across restarts, but register it
     * with the other service again whenever a new value arrives.
     */
    fun url(name: String): Flow<String?>

    /** Every call to the hook called [name], as it arrives. */
    fun calls(name: String): Flow<WebhookCall>
}

/**
 * One request that arrived at a webhook URL.
 *
 * @property headers the request's headers, with names in lower case (e.g. `x-goog-resource-state`).
 */
data class WebhookCall(
    val headers: Map<String, String>,
    val body: String,
)
