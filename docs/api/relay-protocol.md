# Relay protocol (device ↔ Sekund server)

The optional Sekund server (see [docs/server.md](../server.md)) talks to devices over **one WebSocket that
the device opens** (`wss://<server>/v1/device/connect`), so it works behind any home router. Companions then
reach a device through the server with **the same device API** as on the LAN.

The message types are `RelayMessage` in `:core:protocol` (`protocol/relay/RelayMessage.kt`); this page
is a guide to them. API version: 1 (the same `API_VERSION` as the device API).

## Frames

Each message is one JSON text frame. Its kind is in `"type"`; the other fields sit next to it:

```json
{"type": "rpc.request", "id": "r1", "method": "GET", "path": "/v1/config"}
```

- A request carries an `id`; its answer carries the same value in `replyTo`.
- Both sides **ignore unknown types and fields** (newer versions may add some).
- Keep-alive uses WebSocket ping/pong frames (the server pings every 30 s and drops a connection that is
  silent for 75 s; the device pings every 30 s too).

## Connecting and claiming

```
device                                   server
  | -- hello {deviceId, deviceToken?} ---> |
  |                                        |  no token, nobody owns the device:
  | <-- claim.code {code, expiresAt} ----- |     the device shows the code (renewed every 10 min)
  |        (someone enters the code: POST /v1/devices/claim)
  | <-- claimed {deviceToken} ------------ |     the device keeps the token (encrypted)
  | <-- welcome {owner, oauthProviders, webhooks}
```

On later connections the device sends its token in `hello` and gets `welcome` straight away.

| Type | Direction | Fields | Meaning |
|---|---|---|---|
| `hello` | device → server | `deviceId, deviceToken?, name, appVersion, apiVersion` | Always the first message. `deviceId` is the device API's id, so it's the same on the LAN and through the server. |
| `claim.code` | server → device | `code, expiresAt, serverUrl` | Nobody owns the device; show `code` (e.g. `K7QM-3XRP`). |
| `claimed` | server → device | `deviceToken` | Someone claimed it. Keep the token secret; send it in every later `hello`. |
| `welcome` | server → device | `owner, oauthProviders[], webhooks` | Ready. `oauthProviders`: what the server can sign in to for the device; `webhooks`: whether it relays webhooks. |
| `rejected` | server → device | `reason, forgetToken` | Then the server closes. With `forgetToken` the token is no longer valid (the owner removed the device): delete it and reconnect without one to be claimed again. Without it (e.g. someone else already owns this device id), wait a long time before retrying. |

The device reconnects by itself, waiting 1, 2, 4… seconds after each failure (at most 5 minutes, ±20%
randomness), and 10 minutes after a `rejected` without `forgetToken`.

## The RPC tunnel

A companion calls `https://<server>/v1/devices/{deviceId}/v1/...` with its **server session token**; the
server checks the device belongs to that user and passes the request on:

| Type | Direction | Fields |
|---|---|---|
| `rpc.request` | server → device | `id, method, path, query?, headers{Content-Type, If-Match, Accept}, body?` |
| `rpc.response` | device → server | `replyTo, status, headers{Content-Type, ETag}, body?` |

The device answers by making the very same request to its own device API privately over loopback, so a
request behaves exactly as it does on the LAN (ETags, errors and all). The server's session token never
reaches the device. `/v1/pair...` isn't passed on (pairing only makes sense in front of the screen). The
server waits 30 s for an answer (then `504`); a device that isn't connected gives `503`.

### Events

`WS https://<server>/v1/devices/{deviceId}/v1/events?token=<session token>` streams the same `DeviceEvent`s
as the device's own `WS /v1/events`.

| Type | Direction | Meaning |
|---|---|---|
| `events.start` | server → device | Someone is watching: (re)start sending events, current state first. Sent again for each new watcher. |
| `events.stop` | server → device | Nobody is watching any more. |
| `event` | device → server | `{event: DeviceEvent}`, passed on to every watcher. |

## Webhooks

A generic relay: a device asks for a public URL, anything POSTed to it reaches the device.

| Type | Direction | Fields |
|---|---|---|
| `webhook.register` | device → server | `id, key` (`"<instance id>/<hook name>"`) |
| `webhook.registered` | server → device | `replyTo, key, url` (`https://<server>/hooks/<random id>`) |
| `webhook.event` | server → device | `id, key, headers{lower-case names}, body` — answer with `ack` |

The same device and key always get the same URL. `Authorization` and `Cookie` headers aren't passed on;
bodies are limited to 256 KB. Webhooks need the server's `publicUrl` (otherwise `error`).

## Signing in through the server

For providers the server has an OAuth client for (its secret stays on the server):

```
device                         server                          browser (phone)       provider
  | -- oauth.start ------------> |                                 |                     |
  | <-- oauth.started {url} ---- |                                 |                     |
  |   (shows url as a QR code)   | <-- GET /oauth/{p}/start?flow -- |                     |
  |                              | -- 302 to the provider -------> | -- sign in -------> |
  |                              | <-- GET /oauth/{p}/callback?code&state ---------------|
  |                              | -- code + secret + PKCE verifier -------------------> |
  | <-- credential.delivered --- |   (tokens; not kept on the server)                    |
  | -- ack -------------------> |                                                        |
```

| Type | Direction | Fields |
|---|---|---|
| `oauth.start` | device → server | `id, provider, scopes[], scopeSeparator, extraAuthorizeParams{}` (from the device's auth definition) |
| `oauth.started` | server → device | `replyTo, flowId, url, expiresAt` (10 minutes) |
| `credential.delivered` | server → device | `id, flowId, accessToken, refreshToken?, expiresInSeconds?` — answer with `ack` |
| `oauth.failed` | server → device | `id, flowId, reason` — answer with `ack` |
| `token.refresh` | device → server | `id, provider, refreshToken` |
| `token.refreshed` | server → device | `replyTo, status, body` — the provider's token endpoint's answer, as it was |

The provider's URLs come from the server's own config, never from the device, so the client secret can
only be sent to the real provider. The device marks such accounts `viaServer` and refreshes them with
`token.refresh`; without a server connected, a refresh fails like a network error and is tried again later.

## General

| Type | Direction | Fields |
|---|---|---|
| `ack` | either | `replyTo`: "handled; don't send it again" |
| `error` | either | `replyTo, message`: the request couldn't be handled |

## Delivery guarantees

`webhook.event`, `credential.delivered` and `oauth.failed` are **at-least-once**: the server keeps each
one until the device answers `ack`, and sends whatever is still waiting every time the device reconnects. A
device can therefore see a message twice and ignores repeats by `id`. The queue is in memory (at most 100
messages per device; the oldest are dropped), so a server restart loses it — harmless for webhooks, as
devices keep polling. Everything else is best-effort: an `rpc.request` to a device that drops is answered
`504` and the companion retries.
