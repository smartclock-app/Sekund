package uk.dnpk.sekund.sdk.plugin

import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.settings.SettingsSchema
import uk.dnpk.sekund.sdk.widget.WidgetLocation

/**
 * One thing a plugin provides to an extension point, e.g. "a Weather widget" or "a Google calendar source".
 *
 * A contribution is a *type*: people can create any number of *instances* of it (two weather widgets
 * for two towns), each with its own settings. [create] is called once per instance.
 *
 * @param T what the extension point expects (e.g. [uk.dnpk.sekund.sdk.widget.Widget]).
 * @param S the plugin's settings class, produced by [settings].
 * @property type unique across all plugins, e.g. `"weather"`. Stored in config.json for each instance.
 * @property widgetLocations widgets only: where the widget may be placed. Must not be empty for widgets.
 * @property create builds one instance. Sekund calls it again whenever the instance's settings change.
 */
class Contribution<T : Any, S>(
    val point: ExtensionPoint<T>,
    val type: String,
    val name: String,
    val settings: SettingsSchema<S>,
    val description: String? = null,
    val widgetLocations: Set<WidgetLocation> = emptySet(),
    val create: (context: InstanceContext<S>) -> T,
)
