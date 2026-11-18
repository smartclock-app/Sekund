package uk.dnpk.sekund.server.db

import org.jetbrains.exposed.v1.core.Table

/*
 * The server's tables, as Exposed objects. Each `object` is one table and each `val` one column; queries
 * are written against these (see the *Store classes). The tables are created on startup if missing
 * (see ServerDatabase). Times are epoch milliseconds.
 */

/** People who can sign in. Passwords are stored as Argon2 hashes (see PasswordHasher). */
object UsersTable : Table("users") {
    val id = text("id")
    val username = text("username").uniqueIndex()
    val passwordHash = text("password_hash")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

/** Signed-in sessions (the companion's bearer tokens). Only a SHA-256 hash of each token is kept. */
object SessionsTable : Table("sessions") {
    val tokenHash = text("token_hash")
    val userId = text("user_id").references(UsersTable.id)
    val createdAt = long("created_at")
    val lastUsedAt = long("last_used_at")
    override val primaryKey = PrimaryKey(tokenHash)
}

/**
 * Claimed devices. [id] is the device's own id (from its device API), so companions use the same id
 * on the LAN and through the server. [tokenHash] is the hash of the token the device connects with.
 */
object DevicesTable : Table("devices") {
    val id = text("id")
    val ownerId = text("owner_id").references(UsersTable.id)
    val name = text("name")
    val tokenHash = text("token_hash")
    val appVersion = text("app_version").nullable()
    val claimedAt = long("claimed_at")
    val lastSeenAt = long("last_seen_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

/**
 * Public webhook URLs (`/hooks/{hookId}`) and which device (and which of its hooks, by [key]) they
 * belong to. The hook id is random so URLs can't be guessed.
 */
object WebhooksTable : Table("webhooks") {
    val hookId = text("hook_id")
    val deviceId = text("device_id").references(DevicesTable.id)
    val key = text("hook_key")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(hookId)

    init {
        uniqueIndex(deviceId, key)
    }
}
