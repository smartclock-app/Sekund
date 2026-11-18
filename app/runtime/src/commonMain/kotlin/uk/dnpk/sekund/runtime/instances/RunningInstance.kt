package uk.dnpk.sekund.runtime.instances

import kotlinx.coroutines.CoroutineScope
import uk.dnpk.sekund.protocol.settings.SettingsIssue
import uk.dnpk.sekund.runtime.config.InstanceConfig
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoint

/**
 * One instance from config.json, as it currently exists in the app: either working ([Ready]) or not
 * ([Failed]). The shell draws an error card for failed instances instead of crashing.
 */
sealed class RunningInstance {
    /** The config this instance was created from. Used to tell whether it needs recreating. */
    abstract val config: InstanceConfig

    /**
     * The plugin contribution it was created from, or null if no plugin provided its type. When a
     * declarative widget is reloaded its contribution is replaced, which tells InstanceManager to recreate it.
     */
    internal abstract val contribution: Contribution<*, *>?

    val id: String
        get() = config.id

    /**
     * Created successfully.
     *
     * @property point the extension point it belongs to.
     * @property value what the plugin's `create` returned (a Widget, a ClockFace, ...). Use [valueFor].
     * @property issues settings problems that were worked around with defaults.
     */
    class Ready(
        override val config: InstanceConfig,
        val point: ExtensionPoint<*>,
        val value: Any,
        val issues: List<SettingsIssue>,
        internal val scope: CoroutineScope,
        override val contribution: Contribution<*, *>?,
    ) : RunningInstance()

    /** Couldn't be created. [reason] says why, for the error card. */
    class Failed(
        override val config: InstanceConfig,
        val reason: String,
        override val contribution: Contribution<*, *>? = null,
    ) : RunningInstance()
}

/**
 * The instance's value if it belongs to [point], e.g. `instance.valueFor(ExtensionPoints.Widgets)`
 * returns a `Widget?`. Null if the instance failed or is something else (such as a calendar source
 * placed in the layout by mistake).
 */
fun <T : Any> RunningInstance.valueFor(point: ExtensionPoint<T>): T? {
    if (this !is RunningInstance.Ready) return null
    if (this.point != point) return null
    // Safe: we just checked the instance was created for this point, so its value is a T.
    @Suppress("UNCHECKED_CAST")
    return value as T
}
