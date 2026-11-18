package uk.dnpk.sekund.server.users

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uk.dnpk.sekund.protocol.server.ServerUser
import uk.dnpk.sekund.protocol.server.SessionStarted
import uk.dnpk.sekund.server.Secrets
import uk.dnpk.sekund.server.db.ServerDatabase
import uk.dnpk.sekund.server.db.SessionsTable
import uk.dnpk.sekund.server.db.UsersTable
import uk.dnpk.sekund.server.http.ServerProblem
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Accounts (username + password) and their signed-in sessions.
 *
 * @param allowSignups whether anyone may create an account. The first account is always allowed.
 */
class UserStore(
    private val database: ServerDatabase,
    private val allowSignups: Boolean,
    private val hasher: PasswordHasher = PasswordHasher(),
    private val clock: Clock = Clock.System,
) {
    /** Creates an account and signs it in. */
    fun signUp(username: String, password: String): SessionStarted {
        val name = cleanUsername(username)
        if (password.length < 8) throw ServerProblem.badRequest("The password must be at least 8 characters")

        val user = database.tx {
            val isFirst = UsersTable.selectAll().empty()
            if (!isFirst && !allowSignups) throw ServerProblem.forbidden("This server doesn't allow new accounts")
            val taken = !UsersTable.selectAll().where { UsersTable.username eq name }.empty()
            if (taken) throw ServerProblem.conflict("That username is taken")

            val id = "usr_" + Secrets.randomToken(9)
            UsersTable.insert { row ->
                row[UsersTable.id] = id
                row[UsersTable.username] = name
                row[UsersTable.passwordHash] = hasher.hash(password)
                row[UsersTable.createdAt] = clock.now().toEpochMilliseconds()
            }
            ServerUser(id, name)
        }
        return SessionStarted(newSession(user.id), user)
    }

    /** Checks the password and starts a session. The same error for an unknown user or a wrong password. */
    fun signIn(username: String, password: String): SessionStarted {
        val name = username.trim().lowercase()
        val row = database.tx { UsersTable.selectAll().where { UsersTable.username eq name }.singleOrNull() }
        val wrong = ServerProblem.unauthorized("Wrong username or password")
        if (row == null) {
            hasher.hash(password) // takes as long as a real check, so timing doesn't reveal which usernames exist
            throw wrong
        }
        if (!hasher.matches(password, row[UsersTable.passwordHash])) throw wrong
        val user = ServerUser(row[UsersTable.id], row[UsersTable.username])
        return SessionStarted(newSession(user.id), user)
    }

    /** The user a session token belongs to, or null if it isn't valid. */
    fun authenticate(token: String): ServerUser? {
        val tokenHash = Secrets.hash(token)
        return database.tx {
            val session = SessionsTable.selectAll().where { SessionsTable.tokenHash eq tokenHash }.singleOrNull() ?: return@tx null
            val userId = session[SessionsTable.userId]
            val user = UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull() ?: return@tx null

            // Note when the session was last used, at most once a minute.
            val now = clock.now().toEpochMilliseconds()
            if (now - session[SessionsTable.lastUsedAt] > 1.minutes.inWholeMilliseconds) {
                SessionsTable.update({ SessionsTable.tokenHash eq tokenHash }) { row -> row[SessionsTable.lastUsedAt] = now }
            }
            ServerUser(user[UsersTable.id], user[UsersTable.username])
        }
    }

    /** Ends the session with this token. */
    fun signOut(token: String) {
        val tokenHash = Secrets.hash(token)
        database.tx { SessionsTable.deleteWhere { SessionsTable.tokenHash eq tokenHash } }
    }

    fun usernameOf(userId: String): String? = database.tx {
        UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()?.get(UsersTable.username)
    }

    private fun newSession(userId: String): String {
        val token = Secrets.randomToken()
        val now = clock.now().toEpochMilliseconds()
        database.tx {
            SessionsTable.insert { row ->
                row[SessionsTable.tokenHash] = Secrets.hash(token)
                row[SessionsTable.userId] = userId
                row[SessionsTable.createdAt] = now
                row[SessionsTable.lastUsedAt] = now
            }
        }
        return token
    }

    /** Usernames are lower case, 3 to 40 letters, digits, dots, dashes or underscores. */
    private fun cleanUsername(username: String): String {
        val name = username.trim().lowercase()
        val allowed = name.all { char -> char.isLetterOrDigit() || char == '.' || char == '-' || char == '_' }
        if (name.length !in 3..40 || !allowed) {
            throw ServerProblem.badRequest("Usernames are 3 to 40 letters, digits, dots, dashes or underscores")
        }
        return name
    }
}
