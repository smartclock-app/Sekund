package uk.dnpk.sekund.sdk.widget

import androidx.compose.runtime.Composable

/**
 * Something shown in the main zone or the sidebar.
 *
 * Typical shape: a class that takes an [uk.dnpk.sekund.sdk.context.InstanceContext], sets up its data in
 * the constructor (usually with `context.refresher.poll { ... }`), and draws it in [Content]:
 *
 * ```kotlin
 * class WeatherWidget(context: InstanceContext<WeatherSettings>) : Widget {
 *     private val weather = context.refresher.poll(RefreshPolicy(every = 15.minutes)) { fetchWeather() }
 *
 *     @Composable
 *     override fun Content(location: WidgetLocation) {
 *         val state by weather.collectAsState()
 *         ...
 *     }
 * }
 * ```
 *
 * Background work must run in `context.scope`. Sekund cancels that scope when the instance is removed
 * or its settings change, so nothing leaks.
 */
interface Widget {
    /** Draws the widget. [location] tells you whether you're in the main zone or the sidebar. */
    @Composable
    fun Content(location: WidgetLocation)

    /** True if tapping the widget should open [ExpandedContent] as a full-screen sheet. */
    val hasExpandedView: Boolean
        get() = false

    /** The full-screen view opened by tapping (e.g. the whole calendar). Only used if [hasExpandedView]. */
    @Composable
    fun ExpandedContent() {
    }
}
