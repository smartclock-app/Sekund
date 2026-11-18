# :server

The optional Sekund server: remote access to devices, a generic webhook relay, and OAuth sign-ins with
server-managed clients. How to run and configure it: [docs/server.md](../docs/server.md). The wire protocol
with devices: [docs/api/relay-protocol.md](../docs/api/relay-protocol.md).

Ktor (Netty) + Exposed on SQLite (PostgreSQL optional). Everything is assembled by hand in `SekundServer`
(like the app's `AppGraph`).

| Package | What's in it |
|---|---|
| `config` | `ServerConfig`: `server.json` plus environment variable overrides. |
| `db` | The tables (`Tables.kt`) and `ServerDatabase` (creates missing tables; `tx { }` = one transaction). |
| `users` | `UserStore` (accounts, sessions) and `PasswordHasher` (Argon2id). |
| `devices` | `DeviceStore`: claimed devices and their token hashes. |
| `relay` | `DeviceSocket` (one device's WebSocket), `DeviceConnection` (send / request-and-wait), `RelayHub` (who's online, claim codes, RPC, events), `Outbox` (at-least-once delivery). |
| `webhooks` | `WebhookStore` (hook id → device and key) and `WebhookRelay`. |
| `oauth` | `OAuthHost`: starts sign-ins, handles the provider's callback, refreshes tokens. |
| `http` | The routes, one file per area, and `ServerModule` (JSON, WebSockets, errors). |

To add a new message from devices: add it to `RelayMessage` in `:core:protocol`, handle it in
`SekundServer.handleDeviceMessage` (or, for a reply, in `DeviceSocket.dispatch`), and document it in the
relay protocol doc.

Tests (`./gradlew :server:test`) run the whole server in Ktor's test engine with a fresh SQLite file; `FakeDevice`
plays a device on the WebSocket.

Docker: `./gradlew :server:installDist && docker build -t sekund-server server`.
