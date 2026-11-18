# :app:runtime

The engine that makes the SDK's promises true: plugin registry and instance lifecycle, config store, auth registry
and secret store, scheduler, storage, LAN API server, mDNS, relay client and platform services (display, kiosk,
updates). Everything is assembled by hand in `AppGraph` (no dependency-injection framework), so missing pieces
are compile errors.

## Auth (`runtime/auth`)

- `AuthRegistry`: providers, connected accounts (`<data>/accounts.json`), credential refresh (one at a time per
  account) and the central "reconnect" alert.
- `SignedInClient` / `PluginAuthAccess`: what plugins get as `context.auth`. Adds credentials, refreshes, retries
  once on 401, refuses hosts outside `baseUrls` and providers missing from the manifest.
- `DeviceSignIn`, `AuthCodeSignIns`: the OAuth sign-in flows. `AuthCodeSignIns` is driven by the companion
  app through the device API (`api/AccountFlows`).
- `AuthProviderLoader`: built-in `*.auth.json` files (`composeResources/files/auth-providers`, listed in
  `BuiltInAuthProviders`) plus the user's `<config>/auth-providers/`. `AuthProviderFiles` loads them into the
  registry and installs or removes the user's files (from the device API).
- `secrets/`: `EncryptedFileSecretStore` (one AES-GCM file in `AppPaths.secretsDir`) with a platform
  `SecretCipher`: `AndroidKeystoreCipher` or `DesktopSecretCipher` (OS keychain, or an owner-only key file).

The definition format is documented in `docs/plugins/authoring.md`.

## Config (`runtime/config`)

- `ConfigRepository`: `config.json` as a `StateFlow`, saved atomically, never overwritten while broken. ETags
  (`etagOf`) and `updateIfMatch` let the device API refuse stale writes.
- `ConfigEdits`: everyday changes as plain functions (add/remove instances, move things in the layout), shared
  by the settings screens and the device API. `JsonMergePatch` is for `PATCH /v1/config`.

The config data classes (`AppConfig`...) live in `:core:protocol`, because the API and the companion use them too.

## Declarative widgets (`runtime/declarative`)

`DeclarativeWidgets` loads `<config>/widgets/*.json` (plus built-in definitions), and registers each one as an
"extra" plugin in `PluginRegistry`, using the engine passed in from `plugins/declarative`. Installing or removing
one takes effect immediately.

## Device API (`runtime/api`)

The LAN API the companion app uses, documented in `docs/api/device-api.md`.

- `DeviceApi`: everything the API can do, without HTTP. Throws `ApiProblem` for errors.
- `DeviceApiRoutes.kt`: the Ktor routes (thin: read the request, call `DeviceApi`, answer) and `TokenAuth`.
- `Pairing` + `ApiClients`: PIN pairing; tokens are stored only as hashes in `<data>/api-clients.json`.
- `AccountFlows`, `DeviceActions`, `CatalogBuilder`, `ConfigIssues`, `EventStream`: the parts behind the routes.
- `LanApiServer`: starts the Ktor CIO server (checks the port first, so a busy port can't crash the app).

## Relay to the Sekund server (`runtime/relay`)

The optional server link (docs/server.md, docs/api/relay-protocol.md), on when config.json has `server.url`.

- `RelayClient`: the outbound WebSocket. Claiming (shows the code as an alert), the device token (in the secret
  store), reconnecting with backoff, `RelayStatus` for settings, events while the server asks for them.
- `LocalApi`: answers tunnelled device API requests by making the same request to the device API over loopback,
  authorised with `DeviceApi.localToken` (made fresh at every start).
- `RelayFeature`: how other parts use the link. `RelayWebhooks` (what plugins get as `context.webhooks`) and
  `ServerSignIns` (signing in with the server's OAuth clients; such credentials are `viaServer` and
  `AuthRegistry` refreshes them through it).

## Discovery and logging

- `discovery/ServiceAdvertiser`: mDNS `_sekund._tcp`; `JmDnsAdvertiser` (desktop) and `NsdAdvertiser` (Android).
- `logging/LogBuffer` keeps recent log lines for the API; `LogFile` writes `<logs>/sekund.log` (rolled at 1 MB).
