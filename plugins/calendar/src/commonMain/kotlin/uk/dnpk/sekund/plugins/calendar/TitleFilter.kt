package uk.dnpk.sekund.plugins.calendar

import uk.dnpk.sekund.sdk.calendar.CalendarEvent

/**
 * One "hide events matching" entry from the settings.
 *
 * An event is hidden if any line of its title either:
 * - **equals** [text] exactly, or
 * - **contains a match** for [text] read as a regular expression (like the old app's `RegExp(filter).test(title)`,
 *   so `Dentist` hides "Dentist appointment" and `^Private` hides "Private: ...").
 *
 * The exact check means titles containing regex characters, like `C++ meetup` or `Lunch (Bob)`, can still be
 * filtered as plain text. If [text] isn't a valid regular expression only the exact check is used.
 */
class TitleFilter(val text: String) {
    /** The compiled expression, or null if [text] isn't a valid regular expression. */
    private val regex: Regex? = compileOrNull(text)

    /** True if [text] couldn't be used as a regular expression (worth a warning in the log). */
    val isPlainTextOnly: Boolean
        get() = regex == null

    /** True if [event] should be hidden. */
    fun hides(event: CalendarEvent): Boolean {
        for (line in event.titleLines) {
            if (line == text) return true
            if (regex != null && regex.containsMatchIn(line)) return true
        }
        return false
    }

    private fun compileOrNull(pattern: String): Regex? {
        try {
            return Regex(pattern)
        } catch (error: IllegalArgumentException) {
            return null
        }
    }
}
