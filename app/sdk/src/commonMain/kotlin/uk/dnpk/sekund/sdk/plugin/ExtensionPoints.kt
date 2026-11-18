package uk.dnpk.sekund.sdk.plugin

import uk.dnpk.sekund.sdk.calendar.CalendarSource
import uk.dnpk.sekund.sdk.clock.ClockFace
import uk.dnpk.sekund.sdk.photos.PhotoSource
import uk.dnpk.sekund.sdk.widget.Widget

/** The extension points built into Sekund. */
object ExtensionPoints {
    /** Things shown in the main zone or the sidebar. */
    val Widgets = ExtensionPoint<Widget>(id = "widget", name = "Widgets")

    /** What fills the main square: the default clock, a photo slideshow... Exactly one is shown at a time. */
    val ClockFaces = ExtensionPoint<ClockFace>(id = "clockFace", name = "Clock faces")

    /** Where calendar widgets get their events from (Google, Trakt, ...). */
    val CalendarSources = ExtensionPoint<CalendarSource>(id = "calendarSource", name = "Calendar sources")

    /** Where the slideshow clock face gets its photos from (Immich, ...). */
    val PhotoSources = ExtensionPoint<PhotoSource>(id = "photoSource", name = "Photo sources")
}
