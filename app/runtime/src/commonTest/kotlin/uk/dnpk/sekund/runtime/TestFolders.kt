package uk.dnpk.sekund.runtime

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random

/** Creates a fresh, empty folder for one test, and [AppPaths] that point inside it. */
fun newTestPaths(): AppPaths {
    val root = Path(SystemTemporaryDirectory, "sekund-test-" + Random.nextLong().toULong().toString(16))
    SystemFileSystem.createDirectories(root)
    return AppPaths.underOneFolder(root)
}
