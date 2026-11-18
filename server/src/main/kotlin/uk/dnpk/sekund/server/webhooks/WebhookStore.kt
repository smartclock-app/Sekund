package uk.dnpk.sekund.server.webhooks

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import uk.dnpk.sekund.server.Secrets
import uk.dnpk.sekund.server.db.ServerDatabase
import uk.dnpk.sekund.server.db.WebhooksTable
import kotlin.time.Clock

/** Which device (and which of its hooks) a public hook URL belongs to. */
data class Webhook(val hookId: String, val deviceId: String, val key: String)

/** The public webhook URLs handed out to devices. */
class WebhookStore(
    private val database: ServerDatabase,
    private val clock: Clock = Clock.System,
) {
    /**
     * The hook id for [deviceId]'s hook [key], made the first time it's asked for. The same device and
     * key always get the same id, so URLs registered with other services keep working.
     */
    fun register(deviceId: String, key: String): String = database.tx {
        val existing = WebhooksTable.selectAll()
            .where { (WebhooksTable.deviceId eq deviceId) and (WebhooksTable.key eq key) }
            .singleOrNull()
        if (existing != null) return@tx existing[WebhooksTable.hookId]

        val hookId = Secrets.randomToken(18)
        WebhooksTable.insert { row ->
            row[WebhooksTable.hookId] = hookId
            row[WebhooksTable.deviceId] = deviceId
            row[WebhooksTable.key] = key
            row[WebhooksTable.createdAt] = clock.now().toEpochMilliseconds()
        }
        hookId
    }

    fun find(hookId: String): Webhook? = database.tx {
        val row = WebhooksTable.selectAll().where { WebhooksTable.hookId eq hookId }.singleOrNull() ?: return@tx null
        Webhook(row[WebhooksTable.hookId], row[WebhooksTable.deviceId], row[WebhooksTable.key])
    }
}
