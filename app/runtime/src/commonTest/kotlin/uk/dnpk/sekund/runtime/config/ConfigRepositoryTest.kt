package uk.dnpk.sekund.runtime.config

import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import uk.dnpk.sekund.runtime.newTestPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private fun writeText(file: Path, text: String) {
    SystemFileSystem.createDirectories(file.parent!!)
    SystemFileSystem.sink(file).buffered().use { sink -> sink.writeString(text) }
}

private fun readText(file: Path): String = SystemFileSystem.source(file).buffered().use { source -> source.readString() }

/** A clock we move forward by hand, so backups get different timestamps. */
private class StepClock : Clock {
    var current = Instant.parse("2026-01-01T00:00:00Z")

    override fun now(): Instant = current
}

class ConfigRepositoryTest {
    private val defaults = AppConfig(layout = LayoutConfig(sidebar = listOf("calendar")))

    @Test
    fun `a missing config file is created with the defaults`() = runTest {
        val paths = newTestPaths()
        val repository = ConfigRepository(paths)

        repository.load { defaults }

        assertEquals(defaults, repository.config.value)
        assertTrue(SystemFileSystem.exists(paths.configFile))
        assertTrue(readText(paths.configFile).contains("\"calendar\""))
    }

    @Test
    fun `an existing file is read, and missing or invalid fields use defaults`() = runTest {
        val paths = newTestPaths()
        writeText(paths.configFile, """{ "orientation": "sideways", "clock": { "use24Hour": true } }""")
        val repository = ConfigRepository(paths)

        repository.load { defaults }

        assertEquals(Orientation.Landscape, repository.config.value.orientation) // "sideways" isn't valid
        assertTrue(repository.config.value.clock.use24Hour)
        assertTrue(repository.config.value.clock.showSeconds) // missing, so default
        assertNull(repository.loadProblem.value)
    }

    @Test
    fun `a broken file is reported and never overwritten`() = runTest {
        val paths = newTestPaths()
        writeText(paths.configFile, "{ this is not json")
        val repository = ConfigRepository(paths)

        repository.load { defaults }
        val saved = repository.update { config -> config.copy(orientation = Orientation.Portrait) }

        assertNotNull(repository.loadProblem.value)
        assertEquals(defaults, repository.config.value)
        assertFalse(saved)
        assertEquals("{ this is not json", readText(paths.configFile))
    }

    @Test
    fun `updates are saved to the file`() = runTest {
        val paths = newTestPaths()
        val repository = ConfigRepository(paths)
        repository.load { defaults }

        repository.update { config -> config.copy(orientation = Orientation.Portrait) }

        val reloaded = ConfigRepository(paths)
        reloaded.load { AppConfig() }
        assertEquals(Orientation.Portrait, reloaded.config.value.orientation)
    }

    @Test
    fun `only the newest backups are kept`() = runTest {
        val paths = newTestPaths()
        val clock = StepClock()
        val repository = ConfigRepository(paths, clock = clock)
        repository.load { defaults }

        repeat(7) {
            repository.backup(keep = 5)
            clock.current += 1.minutes
        }

        val backups = SystemFileSystem.list(paths.backupDir).map { file -> file.name }.sorted()
        assertEquals(5, backups.size)
        assertEquals("config-2026-01-01T00-02-00Z.json", backups.first()) // the two oldest were deleted
    }
}
