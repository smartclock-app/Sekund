package uk.dnpk.sekund.runtime.auth

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import uk.dnpk.sekund.protocol.SekundPrettyJson

/** Reads and writes the list of [ConnectedAccount]s (`<data>/accounts.json`). */
class AccountsFile(
    private val file: Path,
    private val fileSystem: FileSystem = SystemFileSystem,
) {
    private val serializer = ListSerializer(ConnectedAccount.serializer())

    /** The saved accounts. A missing file means none; an unreadable one throws, so accounts aren't silently lost. */
    fun read(): List<ConnectedAccount> {
        if (!fileSystem.exists(file)) return emptyList()
        val text = fileSystem.source(file).buffered().use { source -> source.readString() }
        try {
            return SekundPrettyJson.decodeFromString(serializer, text)
        } catch (error: SerializationException) {
            throw IllegalStateException("$file is not a valid accounts file: ${error.message}", error)
        }
    }

    /** Replaces the file's contents, writing to a temporary file first so a crash can't leave half a file. */
    fun write(accounts: List<ConnectedAccount>) {
        val folder = file.parent ?: error("$file has no parent folder")
        fileSystem.createDirectories(folder)
        val temporary = Path(folder, file.name + ".tmp")
        val text = SekundPrettyJson.encodeToString(serializer, accounts)
        fileSystem.sink(temporary).buffered().use { sink -> sink.writeString(text) }
        fileSystem.atomicMove(temporary, file)
    }
}
