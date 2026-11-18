package uk.dnpk.sekund.sdk.plugin

/**
 * A named "slot" that plugins can contribute to, e.g. "widgets" or "calendar sources".
 *
 * [T] is the interface every contribution must implement, so the compiler checks that a calendar
 * source really is a [uk.dnpk.sekund.sdk.calendar.CalendarSource]. The built-in points are in
 * [ExtensionPoints]; plugins may define their own the same way.
 *
 * @property id stable identifier, used in config files and the device API.
 */
class ExtensionPoint<T : Any>(
    val id: String,
    val name: String,
) {
    override fun equals(other: Any?): Boolean = other is ExtensionPoint<*> && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "ExtensionPoint($id)"
}
