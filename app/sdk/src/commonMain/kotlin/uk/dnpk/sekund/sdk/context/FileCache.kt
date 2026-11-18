package uk.dnpk.sekund.sdk.context

/**
 * A folder of files private to one instance, for things too big or binary for [KeyValueStore], such as
 * downloaded photos. Kept in Sekund's cache folder (`<cache>/<instanceId>/`), which the system or the
 * user may clear at any time, so only put things here that can be downloaded again.
 *
 * Names are simple file names (letters, digits, `-`, `_`, `.`); anything else is replaced with `_`.
 */
interface FileCache {
    /** The file's bytes, or null if there's no such file. */
    suspend fun read(name: String): ByteArray?

    /** Creates or replaces a file. */
    suspend fun write(name: String, bytes: ByteArray)

    /** Every file name in the cache. */
    suspend fun names(): List<String>

    /** Deletes a file (nothing happens if it doesn't exist). */
    suspend fun remove(name: String)
}
