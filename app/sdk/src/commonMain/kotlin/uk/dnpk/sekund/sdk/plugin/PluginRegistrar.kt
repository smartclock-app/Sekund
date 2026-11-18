package uk.dnpk.sekund.sdk.plugin

import uk.dnpk.sekund.sdk.auth.CustomAuthHandler

/**
 * Handed to [SekundPlugin.register] so a plugin can say what it provides.
 * Implemented by the runtime; plugins only call it.
 */
interface PluginRegistrar {
    /**
     * Adds something to an extension point: a widget, a clock face, a calendar source...
     * A plugin can call this as many times as it likes.
     */
    fun <T : Any, S> contribute(contribution: Contribution<T, S>)

    /**
     * Provides the code for a custom sign-in method: auth definitions with
     * `{"type": "custom", "handler": "<name>"}` use [handler]. See [CustomAuthHandler].
     */
    fun authHandler(name: String, handler: CustomAuthHandler)
}
