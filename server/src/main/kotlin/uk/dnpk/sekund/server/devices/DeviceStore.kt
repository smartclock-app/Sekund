package uk.dnpk.sekund.server.devices

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uk.dnpk.sekund.server.Secrets
import uk.dnpk.sekund.server.db.DevicesTable
import uk.dnpk.sekund.server.db.ServerDatabase
import uk.dnpk.sekund.server.db.WebhooksTable
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A claimed device, as stored.
 *
 * @property ownerId the user it belongs to.
 */
data class StoredDevice(
    val id: String,
    val ownerId: String,
    val name: String,
    val appVersion: String?,
    val claimedAt: Instant,
    val lastSeenAt: Instant?,
)

/** Claimed devices and the tokens they connect with (only hashes are stored). */
class DeviceStore(
    private val database: ServerDatabase,
    private val clock: Clock = Clock.System,
) {
    fun find(id: String): StoredDevice? = database.tx {
        DevicesTable.selectAll().where { DevicesTable.id eq id }.singleOrNull()?.let { row -> toDevice(row) }
    }

    fun ownedBy(ownerId: String): List<StoredDevice> = database.tx {
        DevicesTable.selectAll().where { DevicesTable.ownerId eq ownerId }.map { row -> toDevice(row) }
    }

    /**
     * Makes [deviceId] belong to [ownerId] and returns the device's new token (shown only this once).
     * Returns null if the device is already claimed.
     */
    fun claim(deviceId: String, ownerId: String, name: String, appVersion: String?): String? {
        val token = Secrets.randomToken()
        return database.tx {
            val exists = !DevicesTable.selectAll().where { DevicesTable.id eq deviceId }.empty()
            if (exists) return@tx null
            DevicesTable.insert { row ->
                row[DevicesTable.id] = deviceId
                row[DevicesTable.ownerId] = ownerId
                row[DevicesTable.name] = name
                row[DevicesTable.tokenHash] = Secrets.hash(token)
                row[DevicesTable.appVersion] = appVersion
                row[DevicesTable.claimedAt] = clock.now().toEpochMilliseconds()
            }
            token
        }
    }

    /** The device if [token] is its token, else null. */
    fun authenticate(deviceId: String, token: String): StoredDevice? {
        val device = database.tx {
            DevicesTable.selectAll()
                .where { (DevicesTable.id eq deviceId) and (DevicesTable.tokenHash eq Secrets.hash(token)) }
                .singleOrNull()
                ?.let { row -> toDevice(row) }
        }
        return device
    }

    /** Notes that the device connected or disconnected, with its current name and version. */
    fun seen(deviceId: String, name: String, appVersion: String?) {
        database.tx {
            DevicesTable.update({ DevicesTable.id eq deviceId }) { row ->
                row[DevicesTable.name] = name
                row[DevicesTable.appVersion] = appVersion
                row[DevicesTable.lastSeenAt] = clock.now().toEpochMilliseconds()
            }
        }
    }

    /** Forgets a device (and its webhooks). Returns false if [ownerId] doesn't own [deviceId]. */
    fun remove(ownerId: String, deviceId: String): Boolean = database.tx {
        val owned = !DevicesTable.selectAll().where { (DevicesTable.id eq deviceId) and (DevicesTable.ownerId eq ownerId) }.empty()
        if (!owned) return@tx false
        WebhooksTable.deleteWhere { WebhooksTable.deviceId eq deviceId }
        DevicesTable.deleteWhere { DevicesTable.id eq deviceId }
        true
    }

    private fun toDevice(row: ResultRow): StoredDevice {
        val lastSeen = row[DevicesTable.lastSeenAt]
        return StoredDevice(
            id = row[DevicesTable.id],
            ownerId = row[DevicesTable.ownerId],
            name = row[DevicesTable.name],
            appVersion = row[DevicesTable.appVersion],
            claimedAt = Instant.fromEpochMilliseconds(row[DevicesTable.claimedAt]),
            lastSeenAt = if (lastSeen == null) null else Instant.fromEpochMilliseconds(lastSeen),
        )
    }
}
