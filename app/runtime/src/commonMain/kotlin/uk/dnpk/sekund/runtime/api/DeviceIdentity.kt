package uk.dnpk.sekund.runtime.api

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import uk.dnpk.sekund.protocol.SekundPrettyJson
import uk.dnpk.sekund.runtime.auth.randomUrlSafe

/**
 * This device's permanent id, made up on first start and kept in `<data>/device.json`. Companions use
 * it to recognise a device again even if its name or address changes.
 */
class DeviceIdentity(
    private val file: Path,
    private val fileSystem: FileSystem = SystemFileSystem,
) {
    @Serializable
    private data class Saved(val id: String)

    /** The id, created (and saved) the first time it's asked for. */
    val id: String by lazy { readOrCreate() }

    private fun readOrCreate(): String {
        if (fileSystem.exists(file)) {
            val text = fileSystem.source(file).buffered().use { source -> source.readString() }
            try {
                return SekundPrettyJson.decodeFromString(Saved.serializer(), text).id
            } catch (_: SerializationException) {
                // A damaged file: make a new id rather than refusing to start.
            }
        }
        val id = "dev_" + randomUrlSafe(9)
        file.parent?.let { folder -> fileSystem.createDirectories(folder) }
        fileSystem.sink(file).buffered().use { sink -> sink.writeString(SekundPrettyJson.encodeToString(Saved.serializer(), Saved(id))) }
        return id
    }
}
