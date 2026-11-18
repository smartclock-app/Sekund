package uk.dnpk.sekund.runtime.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.protocol.config.UpdatesConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdaterTest {
    private val rig = ReleaseRig()

    /**
     * Waits (in real time, up to 5 s) until [condition] holds. Update checks run through Ktor on real
     * threads, so moving the test's virtual clock alone doesn't wait for them.
     */
    private suspend fun AppUpdater.waitUntil(condition: (UpdateStatus) -> Boolean): UpdateStatus =
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { status.first(condition) } }

    @Test
    fun `checks after start and on schedule, and waits for a tap to install`() = runTest {
        val installed = mutableListOf<Path>()
        val config = MutableStateFlow(AppConfig(updates = UpdatesConfig(checkEvery = "1h")))
        val updater = AppUpdater(rig.service(installer = { file -> installed.add(file); InstallResult.WaitingForUser }), config, backgroundScope)
        updater.start()

        advanceTimeBy(AppUpdater.FIRST_CHECK_AFTER + 1.seconds)
        val first = updater.waitUntil { status -> status.check != null }
        assertEquals(UpdateCheck.UpToDate("2.0.0"), first.check, "2.0.0 is what's running")

        rig.release("2.1.0")
        advanceTimeBy(1.hours)
        val later = updater.waitUntil { status -> status.available() != null }
        assertEquals("2.1.0", later.available()?.version)
        assertEquals(InstallState.Idle, later.install)

        updater.installNow()
        updater.waitUntil { status -> status.install == InstallState.WaitingForUser }
        assertEquals(1, installed.size)
    }

    @Test
    fun `with installAutomatically a new version installs once by itself`() = runTest {
        var installs = 0
        rig.release("2.1.0")
        val config = MutableStateFlow(AppConfig(updates = UpdatesConfig(installAutomatically = true)))
        val updater = AppUpdater(rig.service(installer = { installs += 1; InstallResult.NotSupported("later") }), config, backgroundScope)

        updater.checkNow()
        updater.waitUntil { status -> status.install is InstallState.NotSupported }
        updater.checkNow() // the same version again: not installed a second time

        assertEquals(1, installs)
        assertEquals(InstallState.NotSupported("later"), updater.status.value.install)
    }

    @Test
    fun `a failed check is reported and nothing can be installed`() = runTest {
        rig.signature = "broken"
        val updater = AppUpdater(rig.service(), MutableStateFlow(AppConfig()), backgroundScope)

        updater.checkNow()

        assertEquals("The release manifest's signature doesn't match this build's key", updater.status.value.checkProblem)
        assertFalse(updater.installNow())
    }

    @Test
    fun `check intervals have a floor and a default`() {
        assertEquals(30.minutes, AppUpdater.checkInterval("30m"))
        assertEquals(5.minutes, AppUpdater.checkInterval("10s"))
        assertEquals(6.hours, AppUpdater.checkInterval("often"))
    }
}
