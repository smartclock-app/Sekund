package uk.dnpk.sekund.runtime.api

import co.touchlab.kermit.Logger
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers

/**
 * Runs the device API on the local network (all interfaces) at [port], using Ktor's CIO server, which
 * works on both Android and desktop. If the port is taken, the API is simply off and the rest of the
 * app carries on.
 *
 * With [host] `127.0.0.1` and [port] 0 it runs privately on a free port instead, for the relay
 * client's own requests (see LocalApi); [boundPort] then says which port that is.
 *
 * Why the port is checked first: CIO binds the port in the background, so a taken port would otherwise
 * surface as an uncaught exception on a background thread (which on Android crashes the app).
 */
class LanApiServer(
    private val api: DeviceApi,
    private val port: Int,
    private val host: String = "0.0.0.0",
) {
    private val log = Logger.withTag("DeviceApi")
    private var server: EmbeddedServer<*, *>? = null

    /** The port it's listening on, once started. */
    var boundPort: Int? = null
        private set

    /** Starts listening. Returns false if it couldn't (e.g. the port is in use). */
    suspend fun start(): Boolean {
        if (!portIsFree()) {
            log.w { "Not starting the device API: port $port is already in use. Change api.port in config.json." }
            return false
        }
        try {
            val created = embeddedServer(CIO, port = port, host = host) { deviceApiModule(api) }
            created.startSuspend(wait = false)
            val connectors = created.engine.resolvedConnectors() // waits until the port is really bound
            server = created
            boundPort = connectors.firstOrNull()?.port ?: port
            log.i { "Device API listening on $host port $boundPort" }
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.e(error) { "Couldn't start the device API on port $port" }
            return false
        }
    }

    /** Tries to bind the port briefly. */
    private suspend fun portIsFree(): Boolean {
        val selector = SelectorManager(Dispatchers.Default)
        try {
            aSocket(selector).tcp().bind(host, port).close()
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        } finally {
            selector.close()
        }
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        server = null
    }
}
