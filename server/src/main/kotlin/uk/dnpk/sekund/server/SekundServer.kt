package uk.dnpk.sekund.server

import uk.dnpk.sekund.server.config.ServerConfig
import uk.dnpk.sekund.server.db.ServerDatabase
import uk.dnpk.sekund.server.users.UserStore
import kotlin.time.Clock

/**
 * Builds and holds everything the server is made of, with plain constructor calls (like the app's
 * AppGraph). To find out where something comes from, look here.
 */
class SekundServer(
    val config: ServerConfig,
    val clock: Clock = Clock.System,
) {
    val database: ServerDatabase = ServerDatabase(config.database)

    val users: UserStore = UserStore(database, config.allowSignups, clock = clock)
}
