package uk.dnpk.sekund.server.http

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.launch
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.api.DeviceEvent
import uk.dnpk.sekund.protocol.relay.RelayMessage
import uk.dnpk.sekund.protocol.server.ClaimDevice
import uk.dnpk.sekund.protocol.server.ServerDevice
import uk.dnpk.sekund.protocol.server.ServerUser
import uk.dnpk.sekund.server.SekundServer
import uk.dnpk.sekund.server.Secrets
import uk.dnpk.sekund.server.devices.StoredDevice

/**
 * The signed-in user's devices, the devices' own relay WebSocket, and the tunnel that passes device
 * API requests through to a device (see docs/api/relay-protocol.md).
 */
fun Route.deviceRoutes(server: SekundServer) {
    // Devices connect here (outbound from the device, so it works behind any home router).
    webSocket("/v1/device/connect") { server.deviceSocket.run(this) }

    get("/v1/devices") {
        val user = signedInUser(call, server)
        val devices = server.devices.ownedBy(user.id).map { device -> toServerDevice(server, device) }
        call.respond(devices)
    }
    post("/v1/devices/claim") {
        val user = signedInUser(call, server)
        val request = call.receive<ClaimDevice>()
        val device = server.relay.claim(request.code, user)
        call.respond(HttpStatusCode.Created, toServerDevice(server, device))
    }
    delete("/v1/devices/{deviceId}") {
        val user = signedInUser(call, server)
        val deviceId = call.parameters["deviceId"] ?: ""
        if (!server.devices.remove(user.id, deviceId)) throw ServerProblem.notFound("You have no device called '$deviceId'")
        server.relay.forget(deviceId)
        call.respond(HttpStatusCode.NoContent)
    }

    // The device's events stream, through the server. Same messages as the device's own WS /v1/events.
    webSocket("/v1/devices/{deviceId}/v1/events") {
        val user = signedInUser(call, server)
        val deviceId = ownedDeviceId(call, server, user)
        val forwarding = launch {
            server.relay.events(deviceId).collect { event ->
                send(Frame.Text(SekundJson.encodeToString(DeviceEvent.serializer(), event)))
            }
        }
        // Companions don't send anything here; this loop just ends when the companion disconnects.
        for (ignored in incoming) {
            continue
        }
        forwarding.cancel()
    }

    // Everything else under a device is a device API request: pass it through.
    route("/v1/devices/{deviceId}/{path...}") {
        handle {
            val user = signedInUser(call, server)
            val deviceId = ownedDeviceId(call, server, user)
            val response = server.relay.rpc(deviceId, rpcRequestOf(call, deviceId))
            respondWith(call, response)
        }
    }
}

/** The `{deviceId}` in the path, checked to belong to [user] (otherwise 404, so ids can't be probed). */
private fun ownedDeviceId(call: ApplicationCall, server: SekundServer, user: ServerUser): String {
    val deviceId = call.parameters["deviceId"] ?: ""
    val device = server.devices.find(deviceId)
    if (device == null || device.ownerId != user.id) throw ServerProblem.notFound("You have no device called '$deviceId'")
    return deviceId
}

/** Copies what the device API needs from the companion's request. Pairing can't be done through the server. */
private suspend fun rpcRequestOf(call: ApplicationCall, deviceId: String): RelayMessage.RpcRequest {
    val path = call.request.path().removePrefix("/v1/devices/$deviceId")
    if (!path.startsWith("/v1/") || path.startsWith("/v1/pair")) throw ServerProblem.notFound("No such device API route: $path")

    val headers = mutableMapOf<String, String>()
    for (name in listOf(HttpHeaders.ContentType, HttpHeaders.IfMatch, HttpHeaders.Accept)) {
        val value = call.request.header(name)
        if (value != null) headers[name] = value
    }
    val method = call.request.httpMethod
    val hasBody = method == HttpMethod.Post || method == HttpMethod.Put || method == HttpMethod.Patch
    val body = if (hasBody) call.receiveText() else null
    return RelayMessage.RpcRequest(
        id = Secrets.randomToken(9),
        method = method.value,
        path = path,
        query = call.request.queryString().ifEmpty { null },
        headers = headers,
        body = body,
    )
}

private suspend fun respondWith(call: ApplicationCall, response: RelayMessage.RpcResponse) {
    val status = HttpStatusCode.fromValue(response.status)
    val etag = headerOf(response, HttpHeaders.ETag)
    if (etag != null) call.response.header(HttpHeaders.ETag, etag)
    val body = response.body
    if (body == null) {
        call.respond(status)
        return
    }
    val contentType = headerOf(response, HttpHeaders.ContentType)
    call.respondText(body, if (contentType == null) ContentType.Application.Json else ContentType.parse(contentType), status)
}

private fun headerOf(response: RelayMessage.RpcResponse, name: String): String? {
    for ((key, value) in response.headers) {
        if (key.equals(name, ignoreCase = true)) return value
    }
    return null
}

private suspend fun toServerDevice(server: SekundServer, device: StoredDevice) = ServerDevice(
    id = device.id,
    name = device.name,
    online = server.relay.isOnline(device.id),
    appVersion = device.appVersion,
    claimedAt = device.claimedAt.toString(),
    lastSeenAt = device.lastSeenAt?.toString(),
)
