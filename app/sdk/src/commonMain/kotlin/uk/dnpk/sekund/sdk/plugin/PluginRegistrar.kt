package uk.dnpk.sekund.sdk.plugin

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
}
