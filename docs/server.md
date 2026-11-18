# The Sekund server (optional)

Sekund works without a server. Run one if you want any of these:

- **Remote access:** the companion app reaches your screens from anywhere, not just on the home network.
- **Instant updates:** webhooks reach the device, e.g. Google Calendar changes appear within seconds instead
  of at the next 5-minute poll.
- **Easier sign-ins:** the server signs in to services (e.g. Google) with its own OAuth client, so you
  don't need to create one per device.

Nothing on the device depends on it: without a server address set, these features are simply off.

How it works: each device opens one WebSocket to the server (so nothing needs opening on the home
router), and the server passes companion requests, webhooks and sign-ins along it. The details are in
[api/relay-protocol.md](api/relay-protocol.md).

## Running it

### Docker

Each release publishes an image for amd64 and arm64 ([releasing.md](releasing.md)):

```sh
docker run -d --name sekund-server -p 8080:8080 \
  -v sekund-data:/data \
  -e PUBLIC_URL=https://sekund.example.org \
  ghcr.io/<owner>/sekund-server:latest
```

Or build the image from source:

```sh
./gradlew :server:installDist
docker build -t sekund-server server
```

`/data` holds the SQLite database and, optionally, `server.json`.

### Without Docker

```sh
./gradlew :server:run                 # from source
# or: ./gradlew :server:installDist, then server/build/install/server/bin/server
```

It reads `server.json` from the working folder, or the file named by `SEKUND_SERVER_CONFIG`.

### HTTPS

Put it behind a reverse proxy that provides HTTPS (Caddy, nginx, Cloudflare Tunnel...) and **passes
WebSockets through** (`/v1/device/connect` and `/v1/devices/*/v1/events`). Google only sends webhooks to
`https://` addresses, and OAuth providers require HTTPS redirects.

## Settings

`server.json` (all optional; see `server/server.example.json`):

```json
{
  "port": 8080,
  "publicUrl": "https://sekund.example.org",
  "database": { "url": "jdbc:sqlite:sekund-server.db" },
  "allowSignups": false,
  "oauth": {
    "google": {
      "clientId": "1234567890-abc.apps.googleusercontent.com",
      "clientSecret": "GOCSPX-...",
      "authorizeUrl": "https://accounts.google.com/o/oauth2/v2/auth",
      "tokenUrl": "https://oauth2.googleapis.com/token"
    }
  }
}
```

| Setting | Environment variable | Meaning |
|---|---|---|
| `port` | `PORT` | Where it listens. |
| `publicUrl` | `PUBLIC_URL` | The address people and services reach it at. **Needed for webhooks and sign-ins** (both off without it). |
| `database.url` (`user`, `password`) | `DATABASE_URL` (`DATABASE_USER`, `DATABASE_PASSWORD`) | SQLite by default; PostgreSQL also works: `jdbc:postgresql://db:5432/sekund`. Tables are created on first start. |
| `allowSignups` | `ALLOW_SIGNUPS` | Whether anyone may create an account. The **first account can always be created**, so set up yours first. |
| `oauth` | — | OAuth clients the server signs in with, by auth provider id (the ids of the app's `*.auth.json` files). `clientAuth`: `body` (default) or `basic`. |

Environment variables win over the file.

### A Google OAuth client

1. In Google Cloud Console, create an OAuth client of type **Web application**.
2. Add the authorized redirect URI `<publicUrl>/oauth/google/callback`.
3. Enable the Google Calendar API for the project, and add the Calendar scope to the consent screen.
4. Put the client id and secret under `oauth.google` as above.

## Connecting a device

1. On the device: **Settings → Sekund server**, enter the server's address, Save. (Or set
   `"server": {"url": "https://sekund.example.org"}` in config.json.)
2. The device shows a claim code, e.g. `K7QM-3XRP` (also written to its log, handy for a Raspberry Pi set up
   over SSH).
3. Signed in to the server, the companion sends the code (`POST /v1/devices/claim`). The device now belongs
   to you and keeps a device token (encrypted) for later connections.

To move a device to another account, remove it (`DELETE /v1/devices/{id}`); it then shows a new code.

## The server's API (for the companion)

All JSON; errors have the same shape as the device API's (`{"error": {"code", "message"}}`).

| Route | Body → answer |
|---|---|
| `POST /v1/users` | `{username, password}` → `201 {token, user}`. Usernames: 3–40 letters, digits, `.`, `-`, `_` (stored lower case). Passwords: 8+ characters. |
| `POST /v1/sessions` | `{username, password}` → `{token, user}` |
| `DELETE /v1/sessions/current` | Signs this session out. |
| `GET /v1/me` | → `{id, username}` |
| `GET /v1/devices` | → `[{id, name, online, appVersion?, claimedAt, lastSeenAt?}]` |
| `POST /v1/devices/claim` | `{code}` → `201` the device. Case, spaces and the dash don't matter. |
| `DELETE /v1/devices/{id}` | Forgets the device and its webhooks; it's disconnected and asked to forget its token. |
| `ANY /v1/devices/{id}/v1/...` | **The device API**, passed through to the device (see [api/device-api.md](api/device-api.md)). `503` while the device is offline. |
| `WS /v1/devices/{id}/v1/events?token=` | The device's events stream. |
| `POST /hooks/{hookId}` | Public webhook URLs (used by other services). |
| `GET /oauth/{provider}/start`, `/callback` | The browser side of signing in through the server. |
| `GET /health` | `ok (api v1)`, for uptime checks. |

Send the session token as `Authorization: Bearer <token>` (WebSockets: `?token=`). Passwords are stored as
Argon2id hashes, and session and device tokens only as SHA-256 hashes.

So a companion needs a single device-API client whose base URL is either `http://<device>:8080` (with a
paired token) or `https://<server>/v1/devices/<deviceId>` (with a server session token).

## Limits

- Messages waiting for an offline device (webhook calls, delivered sign-ins) are kept in memory, at most
  100 per device, so a server restart loses them. Devices keep polling, so nothing is lost for long.
- Sign-ins in progress are also in memory: restarting the server (or the device) mid-sign-in means starting it again.
- One device id can belong to one account. Removing the device from its account frees it.
