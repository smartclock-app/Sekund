package uk.dnpk.sekund.protocol

/**
 * Version of the device API (the `/v1/...` routes) and of the relay protocol.
 *
 * Bump it only for breaking changes. Companions read it from the mDNS TXT record (`api=1`)
 * and from `GET /v1/device` to decide whether they can talk to this device.
 */
const val API_VERSION: Int = 1
