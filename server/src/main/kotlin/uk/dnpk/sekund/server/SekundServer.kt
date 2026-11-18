package uk.dnpk.sekund.server

import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.server.config.ServerConfig
import uk.dnpk.sekund.server.db.ServerDatabase
import uk.dnpk.sekund.server.devices.DeviceStore
import uk.dnpk.sekund.server.relay.DeviceConnection
import uk.dnpk.sekund.server.relay.DeviceSocket
import uk.dnpk.sekund.server.relay.RelayHub
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

    val devices: DeviceStore = DeviceStore(database, clock)

    /** The devices connected right now: claiming, the RPC tunnel and their events. */
    val relay: RelayHub = RelayHub(
        devices = devices,
        welcome = { owner -> welcomeFor(owner) },
        ownerName = { ownerId -> users.usernameOf(ownerId) },
        clock = clock,
    )

    /** Runs each device's relay WebSocket. */
    val deviceSocket: DeviceSocket = DeviceSocket(
        hub = relay,
        devices = devices,
        serverUrl = config.publicUrl ?: "",
        handleMessage = { connection, message -> handleDeviceMessage(connection, message) },
    )

    /** What a device is told it can use once it's connected. */
    private fun welcomeFor(owner: String) = RelayMessage.Welcome(
        owner = owner,
        oauthProviders = config.oauth.keys.sorted(),
        webhooks = config.publicUrl != null,
    )

    /** The device's own requests (everything that isn't a reply to the server). */
    private suspend fun handleDeviceMessage(connection: DeviceConnection, message: RelayMessage) {
        when (message) {
            else -> {} // a message the server doesn't handle (yet); ignore it
        }
    }
}
