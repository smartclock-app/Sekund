package uk.dnpk.sekund.sdk.actions

/**
 * Something an instance can be told to do from outside: the companion app, the device API
 * (`POST /v1/actions/<instanceId>.<name>`) or a script. For example the slideshow's "skip" or Alexa's
 * "pause".
 *
 * @property name short, lowercase, e.g. `"skip"`. Unique within the instance.
 * @property description a sentence for people, shown in the companion's action list.
 * @property run does it. Throw an exception with a readable message if it can't be done.
 */
class PluginAction(
    val name: String,
    val description: String,
    val run: suspend () -> Unit,
)

/**
 * Implement this on the object your contribution creates (a widget, clock face, source...) to offer
 * actions. Sekund lists them in the device API as `<instanceId>.<name>`.
 *
 * ```kotlin
 * class SlideshowFace(...) : ClockFace, HasActions {
 *     override val actions = listOf(PluginAction("skip", "Show the next photo") { skip() })
 * }
 * ```
 */
interface HasActions {
    val actions: List<PluginAction>
}
