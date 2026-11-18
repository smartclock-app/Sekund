package uk.dnpk.sekund.server.db

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import uk.dnpk.sekund.server.config.DatabaseConfig as DatabaseSettings

/**
 * The server's database connection. Opening it creates any missing tables.
 *
 * Every query runs inside [tx], which is one transaction: committed when the block returns, rolled
 * back if it throws.
 */
class ServerDatabase(settings: DatabaseSettings) {
    private val database: Database = connect(settings)

    init {
        tx { SchemaUtils.create(UsersTable, SessionsTable, DevicesTable, WebhooksTable) }
    }

    /** Runs [block] in one transaction and returns its result. */
    fun <T> tx(block: JdbcTransaction.() -> T): T = transaction(database) { block() }

    private fun connect(settings: DatabaseSettings): Database {
        val url = settings.url
        if (url.startsWith("jdbc:sqlite:")) {
            // SQLite allows one writer at a time; serializable transactions are the level it really supports.
            val config = DatabaseConfig { defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE }
            return Database.connect(url, driver = "org.sqlite.JDBC", databaseConfig = config)
        }
        if (url.startsWith("jdbc:postgresql:")) {
            return Database.connect(url, driver = "org.postgresql.Driver", user = settings.user ?: "", password = settings.password ?: "")
        }
        throw IllegalArgumentException("database.url must start with jdbc:sqlite: or jdbc:postgresql: (got $url)")
    }
}
