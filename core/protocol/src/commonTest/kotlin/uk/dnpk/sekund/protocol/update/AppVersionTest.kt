package uk.dnpk.sekund.protocol.update

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {
    private fun v(text: String): AppVersion = AppVersion.parse(text)!!

    @Test
    fun `versions compare by number, not as text`() {
        assertTrue(v("1.10.0") > v("1.9.3"))
        assertTrue(v("v2.0") > v("1.99.99"))
        assertTrue(v("2.1").compareTo(v("2.1.0")) == 0)
        assertTrue(v("2.1.0-beta").compareTo(v("2.1.0")) == 0)
    }

    @Test
    fun `development builds have no version`() {
        assertNull(AppVersion.parse("dev"))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("1.x"))
    }
}
