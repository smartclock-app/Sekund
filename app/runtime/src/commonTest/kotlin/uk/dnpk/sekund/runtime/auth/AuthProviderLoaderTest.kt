package uk.dnpk.sekund.runtime.auth

import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import uk.dnpk.sekund.runtime.newTestPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthProviderLoaderTest {
    private val paths = newTestPaths()

    @Test
    fun `every built-in provider file is valid`() = runTest {
        val loaded = AuthProviderLoader(paths.authProvidersDir).load()

        assertEquals(emptyList(), loaded.problems)
        assertEquals(listOf("google", "trakt", "immich", "openweather", "homeassistant", "actual-http-api"), loaded.providers.keys.toList())
    }

    @Test
    fun `user files override built-ins and broken ones are reported`() = runTest {
        SystemFileSystem.createDirectories(paths.authProvidersDir)
        write("trakt.auth.json", """{"id": "trakt", "name": "My Trakt", "methods": [{"type": "apiKey"}], "baseUrls": ["https://api.trakt.tv/"]}""")
        write("broken.auth.json", """{"id": "Broken!", "name": "", "methods": [], "baseUrls": []}""")
        write("notes.txt", "ignored")

        val loaded = AuthProviderLoader(paths.authProvidersDir).load()

        assertEquals("My Trakt", loaded.providers.getValue("trakt").name)
        assertTrue(loaded.problems.any { problem -> problem.contains("broken.auth.json") })
    }

    private fun write(name: String, text: String) {
        SystemFileSystem.sink(Path(paths.authProvidersDir, name)).buffered().use { sink -> sink.writeString(text) }
    }
}
