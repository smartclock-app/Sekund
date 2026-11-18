package uk.dnpk.sekund.runtime.updates

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import uk.dnpk.sekund.protocol.update.UpdateSource
import uk.dnpk.sekund.runtime.newTestPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class UpdateServiceTest {
    private val rig = ReleaseRig()

    @Test
    fun `a signed newer release is offered, an older or equal one isn't`() = runTest {
        rig.release("2.1.0")
        val available = rig.service().check()
        assertIs<UpdateCheck.Available>(available)
        assertEquals("2.1.0", available.update.version)
        assertEquals("Cameras", available.update.notes)

        // Linux's base64 wraps lines at 76 characters; that must still work.
        rig.signature = rig.signature.chunked(76).joinToString("\n") + "\n"
        assertIs<UpdateCheck.Available>(rig.service().check())

        rig.release("2.0.0")
        assertEquals(UpdateCheck.UpToDate("2.0.0"), rig.service().check())
    }

    @Test
    fun `a manifest signed by another key, or changed after signing, is refused`() = runTest {
        val newer = rig.manifest("2.1.0")
        rig.manifest = newer

        rig.signature = rig.sign(newer, rig.otherKeys.private)
        assertFailsWith<UpdateRejectedException> { rig.service().check() }

        rig.signature = rig.sign(rig.manifest("9.0.0"))
        assertFailsWith<UpdateRejectedException> { rig.service().check() }

        rig.signature = "not base64!"
        assertFailsWith<UpdateRejectedException> { rig.service().check() }
    }

    @Test
    fun `development builds and builds without a source don't update`() = runTest {
        rig.release("2.1.0")
        assertIs<UpdateCheck.Unavailable>(rig.service(version = "dev").check())

        val noSource = UpdateService(HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) }), "2.0.0", "android", newTestPaths().cacheDir, null) { UpdateSource() }
        assertIs<UpdateCheck.Unavailable>(noSource.check())
    }

    @Test
    fun `downloads are verified before they can be installed`() = runTest {
        val installed = mutableListOf<Path>()
        rig.release("2.1.0")
        val updates = rig.service(installer = { file -> installed.add(file); InstallResult.WaitingForUser })
        val update = (updates.check() as UpdateCheck.Available).update

        val progress = mutableListOf<Float>()
        val downloaded = updates.download(update) { fraction -> progress.add(fraction) }
        assertEquals(1f, progress.last())
        assertEquals(InstallResult.WaitingForUser, updates.install(downloaded))
        assertEquals(listOf(downloaded.file), installed.map { it.toString() })

        // Files from anywhere else are never installed.
        assertFailsWith<UpdateRejectedException> { updates.install(downloaded.copy(file = "/tmp/evil.apk")) }
    }

    @Test
    fun `a download that doesn't match the manifest is deleted`() = runTest {
        rig.manifest = rig.manifest("2.1.0", hash = "0".repeat(64))
        rig.signature = rig.sign(rig.manifest)
        val updates = rig.service()
        val update = (updates.check() as UpdateCheck.Available).update

        assertFailsWith<UpdateRejectedException> { updates.download(update) }
    }

    @Test
    fun `without a platform installer the download is kept and nothing is installed`() = runTest {
        rig.release("2.1.0")
        val updates = rig.service()
        val downloaded = updates.download((updates.check() as UpdateCheck.Available).update)

        assertIs<InstallResult.NotSupported>(updates.install(downloaded))
    }
}
