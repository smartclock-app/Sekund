package uk.dnpk.sekund.runtime.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import uk.dnpk.sekund.sdk.context.FileCache

/** The real [FileCache]: plain files in one folder (`<cache>/instances/<instanceId>/`), made when first written. */
class FolderFileCache(
    private val folder: Path,
    private val fileSystem: FileSystem = SystemFileSystem,
) : FileCache {

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = fileOf(name)
        if (!fileSystem.exists(file)) return@withContext null
        fileSystem.source(file).buffered().use { source -> source.readByteArray() }
    }

    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        fileSystem.createDirectories(folder)
        // Write to a temporary file first, so a crash half-way never leaves a broken picture behind.
        val temporary = Path(folder, "${safe(name)}.tmp")
        fileSystem.sink(temporary).buffered().use { sink -> sink.write(bytes) }
        fileSystem.atomicMove(temporary, fileOf(name))
    }

    override suspend fun names(): List<String> = withContext(Dispatchers.IO) {
        if (!fileSystem.exists(folder)) return@withContext emptyList()
        fileSystem.list(folder).map { path -> path.name }.filter { name -> !name.endsWith(".tmp") }
    }

    override suspend fun remove(name: String) = withContext(Dispatchers.IO) {
        fileSystem.delete(fileOf(name), mustExist = false)
    }

    private fun fileOf(name: String): Path = Path(folder, safe(name))

    /** Keeps names to plain file names, so a plugin can't write outside its folder. */
    private fun safe(name: String): String {
        val cleaned = name.map { char -> if (char.isLetterOrDigit() || char == '-' || char == '_' || char == '.') char else '_' }.joinToString("")
        if (cleaned.isEmpty() || cleaned.all { char -> char == '.' }) return "_"
        return cleaned
    }
}
