package uk.dnpk.sekund.runtime.relay

import uk.dnpk.sekund.protocol.relay.RelayMessage

/** Answers device API requests that came through the server (see [LocalApi] for the real one). */
fun interface RpcHandler {
    suspend fun handle(request: RelayMessage.RpcRequest): RelayMessage.RpcResponse
}
