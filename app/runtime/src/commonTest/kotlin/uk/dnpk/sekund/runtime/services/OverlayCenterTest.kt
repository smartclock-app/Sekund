package uk.dnpk.sekund.runtime.services

import uk.dnpk.sekund.sdk.context.Overlay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverlayCenterTest {
    private fun overlay(priority: Int = Overlay.NORMAL, onDismissed: () -> Unit = {}) =
        Overlay(priority = priority, onDismissed = onDismissed, content = {})

    private fun OverlayCenter.topOwner(): String? = OverlayCenter.top(entries.value)?.owner

    @Test
    fun `the newest overlay of equal priority is on top`() {
        val center = OverlayCenter()
        center.forOwner("doorbell").show(overlay())
        center.forOwner("garden").show(overlay())

        assertEquals("garden", center.topOwner())

        center.clear("garden")
        assertEquals("doorbell", center.topOwner())
    }

    @Test
    fun `higher priority wins over newer`() {
        val center = OverlayCenter()
        center.forOwner("alarm").show(overlay(priority = Overlay.URGENT))
        center.forOwner("doorbell").show(overlay())

        assertEquals("alarm", center.topOwner())
    }

    @Test
    fun `each owner has one overlay and only touches its own`() {
        val center = OverlayCenter()
        val doorbell = center.forOwner("doorbell")
        doorbell.show(overlay())
        doorbell.show(overlay())
        center.forOwner("garden").clear()

        assertEquals(setOf("doorbell"), center.entries.value.keys)
    }

    @Test
    fun `tapping away tells the plugin, clearing it itself doesn't`() {
        val center = OverlayCenter()
        var dismissals = 0
        center.forOwner("doorbell").show(overlay(onDismissed = { dismissals += 1 }))

        center.dismiss("doorbell")
        assertNull(center.topOwner())
        assertEquals(1, dismissals)

        center.forOwner("doorbell").show(overlay(onDismissed = { dismissals += 1 }))
        center.forOwner("doorbell").clear()
        assertEquals(1, dismissals)
        assertTrue(center.entries.value.isEmpty())
    }
}
