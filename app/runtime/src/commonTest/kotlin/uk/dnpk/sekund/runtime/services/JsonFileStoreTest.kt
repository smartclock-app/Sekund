package uk.dnpk.sekund.runtime.services

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.Serializable
import uk.dnpk.sekund.runtime.newTestPaths
import uk.dnpk.sekund.sdk.context.get
import uk.dnpk.sekund.sdk.context.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Serializable
private data class Show(val title: String, val season: Int)

class JsonFileStoreTest {
    @Test
    fun `values survive a restart`() = runTest {
        val file = Path(newTestPaths().storageDir, "trakt.json")

        JsonFileStore(file).put("lastShow", Show("Severance", 2))
        val afterRestart = JsonFileStore(file)

        assertEquals(Show("Severance", 2), afterRestart.get<Show>("lastShow"))
    }

    @Test
    fun `missing keys and removed keys read as null`() = runTest {
        val store = JsonFileStore(Path(newTestPaths().storageDir, "x.json"))
        store.put("a", 1)

        store.remove("a")

        assertNull(store.get<Int>("a"))
        assertNull(store.get<Int>("never-set"))
    }

    @Test
    fun `a value stored in an old shape reads as null instead of crashing`() = runTest {
        val store = JsonFileStore(Path(newTestPaths().storageDir, "x.json"))
        store.put("lastShow", "just a string")

        assertNull(store.get<Show>("lastShow"))
    }
}
