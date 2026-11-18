# Sekund device API (v1)

The API a companion app uses to set up and control a Sekund device on the local network. Later, the
optional server will relay the **same paths** (`https://server/v1/devices/{deviceId}/…`), so a companion
needs one client with a swappable base URL.

The request and response types are Kotlin data classes in `:core:protocol` (`protocol/api`,
`protocol/config`, `protocol/settings`, `protocol/declarative`, `protocol/auth`). They are the source of
truth; this document describes them.

## Basics

- **Transport:** HTTP/1.1 + JSON on port `8080` (`api.port` in config.json), plus one WebSocket.
- **Version:** everything is under `/v1`. `GET /v1/device` returns `apiVersion` (currently `1`), and so
  does the mDNS TXT record. It only changes for breaking changes.
- **Unknown fields:** ignore fields you don't know; the device does the same.
- **Authentication:** everything except pairing needs `Authorization: Bearer <token>`.

### Discovery (mDNS / DNS-SD)

Devices announce `<name>._sekund._tcp.local` on the API port, with TXT records:

| Key | Example | Meaning |
|---|---|---|
| `id` | `dev_komaxzAJ5tZS` | Permanent device id (also in `GET /v1/device`) |
| `name` | `Kitchen` | `api.deviceName` from config.json |
| `api` | `1` | API version |
| `ver` | `1.2.0` | App version |

Try it with `dns-sd -B _sekund._tcp` (macOS) or `avahi-browse -r _sekund._tcp` (Linux).

### Errors

Every error has an HTTP status and this body:

```json
{ "error": { "code": "not_found", "message": "No instance called 'x'", "details": null } }
```

| `code` | Status | When |
|---|---|---|
| `bad_request` | 400 | Malformed JSON, missing field, impossible request |
| `unauthorized` | 401 | Missing/invalid token, or wrong PIN |
| `not_found` | 404 | No such instance, account, definition… |
| `conflict` | 409 | Duplicate id, or config.json on disk is broken and can't be saved |
| `precondition_failed` | 412 | `If-Match` doesn't match the current config |
| `invalid` | 422 | Validation failed; `details` lists the issues (`[{path, message}]`) |
| `precondition_required` | 428 | `PUT`/`PATCH /v1/config` without `If-Match` |
| `too_many_attempts` | 429 | Too many pairing requests or wrong PINs |
| `unavailable` | 501 | Not supported yet (e.g. custom sign-in handlers) |
| `internal` | 500 | A bug; the device log has details |

## Pairing

Only someone who can see the screen can pair.

1. `POST /v1/pair` with `{"clientName": "Danny's phone"}` → `{"pairingId", "expiresAt"}`.
   The device shows "Pair with Danny's phone? PIN 499 852" at the top of the sidebar.
2. The person types the PIN into the companion.
3. `POST /v1/pair/confirm` with `{"pairingId", "pin": "499852"}` → `{"token", "clientId", "deviceId"}`.

Store the token securely: it's shown only once (the device keeps only its SHA-256 hash). A pairing lasts
5 minutes and allows 5 wrong PINs; at most 3 can be waiting at once.

| Route | Does |
|---|---|
| `GET /v1/clients` | Paired companions: `[{id, name, pairedAt, lastSeenAt}]` |
| `DELETE /v1/clients/{id}` | Unpair; that token stops working immediately |

```sh
curl -X POST http://sekund.local:8080/v1/pair -H 'content-type: application/json' -d '{"clientName":"curl"}'
curl -X POST http://sekund.local:8080/v1/pair/confirm -H 'content-type: application/json' \
     -d '{"pairingId":"tC_qPXMtX3d2","pin":"499852"}'
export TOKEN=...   # from the answer
curl http://sekund.local:8080/v1/device -H "Authorization: Bearer $TOKEN"
```

## Device and catalog

`GET /v1/device` → `DeviceInfo`:

```json
{ "id": "dev_komaxzAJ5tZS", "name": "Kitchen", "appVersion": "1.2.0", "apiVersion": 1, "platform": "android",
  "capabilities": ["DisplayControl", "Kiosk"], "paths": { "config": "...", "data": "...", "logs": "..." } }
```

Hide features the device lacks: `capabilities` can include `DisplayControl`, `Kiosk`, `SelfUpdate`,
`OpenBrowser`, `VideoPlayback`.

`GET /v1/catalog` → `Catalog`: everything that can be configured, with the forms to configure it. **Render
the whole settings UI from this**, so new plugins and declarative widgets need no companion update.

```jsonc
{
  "plugins": [{ "manifest": { "id": "weather", "name": "Weather", "version": "1.0.0", "requires": { "auth": [...] } },
                "declarative": true,
                "auth": [{ "provider": "openweather", "optional": false, "providerInstalled": true, "accountsConnected": 1 }] }],
  "types": [{ "type": "weather", "pluginId": "weather", "name": "Weather", "extensionPoint": "widget",
              "locations": ["main", "sidebar"], "settings": { "fields": [ /* FieldDescriptor */ ] },
              "unavailable": null }],
  "extensionPoints": [{ "id": "widget", "name": "Widgets" }, { "id": "clockFace", "name": "Clock faces" }, ...],
  "problems": ["widgets/broken.widget.json: view A widget needs a view"]
}
```

### Settings forms (`SettingsDescriptor`)

`settings.fields` is a list of fields, each with `kind`, `key`, `label`, optional `description`, and `required`.

| `kind` | Extra keys | Value stored in settings |
|---|---|---|
| `text` | `default`, `placeholder`, `multiline` | string |
| `number` | `default`, `min`, `max` | whole number |
| `toggle` | `default` | boolean |
| `choice` | `options: [{value, label}]`, `default` | one option's `value` |
| `duration` | `default`, `min` | string such as `"15m"`, `"1h 30m"` |
| `color` | `default` | `"#RRGGBB"` or `"#AARRGGBB"` |
| `textList` | `default` | list of strings |
| `objectList` | `item` (a nested SettingsDescriptor) | list of objects |
| `account` | `provider` | an account id from `GET /v1/accounts` (or null) |
| `instanceRefs` | `extensionPoint` | list of instance ids whose type has that extension point |

Invalid values never break anything: the device falls back to the default and reports an issue.

## Config

`GET /v1/config` → the whole config.json (`AppConfig`), with an `ETag` header:

```jsonc
{
  "version": 1,
  "orientation": "landscape",            // or "portrait"
  "clock": { "use24Hour": false, "showSeconds": true },
  "clockFace": "clock",                  // instance id of a clock face
  "layout": { "main": ["weather"], "sidebar": ["calendar"] },
  "instances": [{ "id": "weather", "type": "weather", "settings": { ... }, "refreshInterval": "30m" }],
  "api": { "enabled": true, "port": 8080, "deviceName": "Kitchen" }
}
```

Changing it:

| Route | Body | Notes |
|---|---|---|
| `PATCH /v1/config` | JSON Merge Patch (RFC 7386) | **`If-Match` required.** `{"clock": {"use24Hour": true}}` changes only that. `null` removes a key. |
| `PUT /v1/config` | a whole `AppConfig` | **`If-Match` required.** |
| `PUT /v1/layout` | `{"main": [...], "sidebar": [...]}` | `If-Match` optional |

Every change answers `ConfigSaved`: `{"etag", "issues": [{instanceId, path, message}]}` and backs up the
previous config.json (the last 5 are kept). If someone else changed the config since your `GET`, you get
`412` and should fetch it again. Send `If-Match: *` to overwrite regardless. Changes apply live on the
device; nothing restarts except the instances whose settings changed. `api` changes apply after a restart.

### Instances

| Route | Body → answer |
|---|---|
| `GET /v1/instances` | → `[InstanceStatus]`: `{config, running, problem, issues}` |
| `GET /v1/instances/{id}` | → `InstanceStatus` |
| `POST /v1/instances` | `{type, id?, settings?, refreshInterval?, location?}` → `201` + the new `InstanceConfig`. Missing settings take defaults; `location` (`"main"`/`"sidebar"`) places a widget at the end of that zone. |
| `PUT /v1/instances/{id}` | `{settings, refreshInterval?}` → `ConfigSaved` |
| `DELETE /v1/instances/{id}` | → `ConfigSaved`; also removes it from the layout |

## Declarative widgets and auth providers

Both are definition files, sent as the request body (comments and trailing commas allowed). Formats:
[declarative widgets](../plugins/declarative-widgets.md), [auth providers](../plugins/authoring.md).

| Route | Does |
|---|---|
| `GET /v1/declarative-widgets` | `[{id, name, kind, builtIn}]` |
| `GET /v1/declarative-widgets/{id}` | The definition text |
| `PUT /v1/declarative-widgets/{id}` | Install or replace. → `{"saved": true, "issues": []}`, or `422` with the issues in `details`. Takes effect immediately. |
| `DELETE /v1/declarative-widgets/{id}` | Remove the user's file (a built-in one with the same id comes back) |
| `GET /v1/auth-providers` | `[{id, name, kind: "authProvider", builtIn}]` |
| `GET /v1/auth-providers/{id}` | The definition |
| `PUT /v1/auth-providers/{id}` | Install or replace (same answers as above) |
| `DELETE /v1/auth-providers/{id}` | Remove the user's file |

The `id` in the path must match the id inside the definition.

## Accounts

Accounts hold credentials; settings only ever store an account's id. Credentials never leave the device.

| Route | Body → answer |
|---|---|
| `GET /v1/accounts` | → `[{id, provider, label, state, reason?}]`; `state` is `valid`, `refreshing`, `needsReauth` or `notFound` |
| `POST /v1/accounts` | `ConnectAccount` → `AccountFlow` |
| `GET /v1/accounts/flows/{flowId}` | → `AccountFlow` |
| `POST /v1/accounts/flows/{flowId}/code` | `{code, state}` → `AccountFlow` |
| `PUT /v1/accounts/{id}/credential` | `{accessToken?, refreshToken?, expiresInSeconds?, apiKey?}` → `AccountSummary`. Last resort, for providers a companion signs in to itself. |
| `DELETE /v1/accounts/{id}` | Revokes the token with the service (if it can) and forgets the account |

`ConnectAccount`: `{provider, method?, fields?, key?, clientId?, clientSecret?, redirectUri?, reconnect?}`.
`method` is the index into the provider's `methods` (default `0`, the preferred one). `fields` fills the
provider's extra fields (e.g. `instance_url`). `reconnect` updates an existing account instead of adding one.

`AccountFlow` (by `kind`):

| `kind` | Means |
|---|---|
| `connected` | Done: `{account}` |
| `deviceCode` | `{flowId, userCode, verificationUrl, qrUrl}`. The device also shows the code. Watch `accountsChanged` events or poll the flow. |
| `authorizationCode` | `{flowId, authorizeUrl, redirectUri, expiresAt}`. See below. |
| `failed` | `{reason}` |

### By method

- **API key / token** (`apiKey`, `bearer`): send `key`; the answer is `connected`.
- **Device code** (`oauth2.deviceCode`, e.g. Trakt): the device polls the service; nothing more to send.
- **Authorization code, companion-assisted** (`oauth2.authorizationCode`, e.g. Google, with no server):
  1. `POST /v1/accounts` with `redirectUri` set to the companion's registered redirect (custom scheme or
     app link). Send `clientId`/`clientSecret` if the provider uses the person's own OAuth client.
  2. The device makes the PKCE verifier and `state` (it keeps the verifier) and returns `authorizeUrl`.
  3. Open `authorizeUrl` in Custom Tabs / ASWebAuthenticationSession.
  4. When the browser comes back to `redirectUri`, send its `code` and `state` to
     `POST /v1/accounts/flows/{flowId}/code`.
  5. The device checks `state`, swaps the code for tokens and answers `connected`. Flows expire after 10 minutes.

## Actions

`GET /v1/actions` → `[{name, description}]`. `POST /v1/actions/{name}` → `{name, ok, message?}`.

| Name | Does |
|---|---|
| `device.reload` | Restart every widget and source |
| `device.refresh` | Fetch fresh data for everything now |
| `update.check` | Look for a new version of Sekund now (fails with the reason if the check fails) |
| `update.install` | Download and install the new version, if there is one (fails if there's none) |
| `display.on` / `display.off` | Turn the screen on or off. Only listed with the `DisplayControl` capability. On Android, `display.off` fails with a message until display control is enabled on the device (long-press menu → *Enable display control*) |
| `kiosk.enter` / `kiosk.exit` | Lock the device to Sekund, or unlock it until the app restarts. Only listed with the `Kiosk` capability (Android device owner) |
| `<instanceId>.refresh` | Fetch fresh data for one instance now |
| `<instanceId>.<name>` | An action the instance's plugin offers, e.g. `photos.skip` (listed by `GET /v1/actions`) |

Display actions on desktop and the Raspberry Pi arrive with the desktop platform phase.

## Logs

`GET /v1/logs?tail=200` → `[{at, level, tag, message}]`, oldest first (info and above, at most 500).
The full log is in `<logs>/sekund.log` on the device.

## Events (`WS /v1/events`)

A WebSocket with one JSON message per event. Authenticate with the `Authorization` header, or (because
browsers can't set headers on WebSockets) `?token=<token>`. On connect you first get the current state
(`configChanged`, `instancesChanged`, `accountsChanged`, and an `alertRaised` per alert), then every
change. Ignore kinds you don't know.

| `kind` | Fields | When |
|---|---|---|
| `configChanged` | `etag` | config.json changed (from anywhere: API, device settings, import) |
| `instancesChanged` | `instances: [InstanceStatus]` | An instance started, failed, was changed or removed |
| `accountsChanged` | `accounts: [AccountSummary]` | An account was added, removed or changed state |
| `alertRaised` | `owner, title, subtitle?` | An alert card appeared (pairing PINs are never sent) |
| `alertCleared` | `owner` | An alert card went away |
| `log` | `line: {at, level, tag, message}` | A log line (info and above) |
| `actionResult` | `result: {name, ok, message?}` | An action finished |

## Importing the old app's config

`POST /v1/import/legacy` with the old Tauri app's config.json as the body → `{instances, accounts, notes, etag}`.

The current config is backed up and replaced. Credentials in the old file (API keys, OAuth tokens) become
accounts; the new config refers to them by id. `notes` lists anything that couldn't be carried over. The
same import happens automatically if an old-format config.json is found when the app starts.

## Companion requirements

What the companion (`SekundCompanion`, Flutter) needs in order to work with this API. The redesign itself
is out of scope here.

1. **Discovery:** browse `_sekund._tcp`; show `name`; remember devices by the TXT `id`. Allow entering an
   address by hand as a fallback.
2. **Pairing:** the PIN flow above; store the token per device in secure storage; handle `401` by offering
   to pair again.
3. **Catalog-driven settings:** render `GET /v1/catalog` types and their `SettingsDescriptor` forms
   generically (all field kinds above); show `issues` next to fields and `unavailable`/`problems` as warnings;
   when adding an instance whose plugin needs an account, walk through connecting one first.
4. **Config editing:** keep the `ETag`; send `If-Match`; on `412`, refetch and reapply. Prefer the targeted
   routes (`/instances`, `/layout`) and `PATCH` over `PUT`.
5. **Accounts:** list and remove; connect with each `AccountFlow` kind, including the companion-assisted
   authorization-code steps. Register a redirect (custom scheme or app link) per provider client, and handle
   the deep link `sekundcompanion://auth?device=<id>&flow=<flowId>` (planned: the device will show it as a QR).
6. **Events:** keep `WS /v1/events` open while a device screen is visible; refresh views on `configChanged`,
   `instancesChanged` and `accountsChanged`; show `accountsChanged` progress during sign-in.
7. **Definitions:** install declarative widgets (paste, file or URL) and auth providers with `PUT`, showing
   the `422` issues.
8. **Legacy import:** offer to send an old config.json to `POST /v1/import/legacy` and show its `notes`.
9. **Relay (later):** the same client with the base URL switched to `https://server/v1/devices/{deviceId}`.

## Not yet available

- The relay through the optional server (phase 9), and with it remote access and server-hosted OAuth.
- Scoped (limited) tokens: every paired client can do everything.
- `PUT /v1/theme` and theme tokens.
- Display control on desktop and the Raspberry Pi.
- `sekund://` deep links on the device.
