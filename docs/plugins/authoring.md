# Writing plugins

This guide grows phase by phase. Right now it covers **signing in to services**.

If all you need is "call an API and show the result", you may not need Kotlin at all: see
[declarative widgets](declarative-widgets.md).

## Using accounts from a plugin

1. Declare the provider in your manifest, so Sekund can check it's installed and show
   "connect an account first" instead of failing later:

   ```kotlin
   override val manifest = PluginManifest(
       id = "watchlist",
       name = "Watchlist",
       version = "1.0.0",
       requires = Requirements(auth = listOf(AuthRequirement(provider = "trakt"))),
   )
   ```

   If a required (non-`optional`) provider isn't installed, your instances fail with that reason.
   Asking for an account of a provider you didn't declare throws `ProviderNotDeclaredException`.

2. Add an account setting: `val account = account("account", "Trakt account", provider = "trakt")`.
   Settings only hold a reference (`AccountRef`), never a token.

3. Get a signed-in client from the instance context and use it like any Ktor client:

   ```kotlin
   val ref = context.settings.account ?: return Loadable.Failed("Pick a Trakt account in settings")
   val trakt = context.auth.client(ref)          // get once, reuse
   val text = trakt.get("https://api.trakt.tv/users/me/watchlist").bodyAsText()
   ```

   The client adds the credential, refreshes it shortly before it expires, and on a `401` refreshes once and
   retries (concurrent requests share one refresh). It refuses URLs outside the provider's `baseUrls`.
   Watch `context.auth.state(ref)` if you want to show "reconnect your account". You don't need to show an
   alert yourself: Sekund shows one central "Reconnect …" alert.

## Auth provider definitions (`*.auth.json`)

A provider says *how* to sign in to a service. It's data, not code, so one definition is shared by every plugin
and declarative widget that talks to that service. Built-in definitions live in
`app/runtime/src/commonMain/composeResources/files/auth-providers/` (and must be listed in
`BuiltInAuthProviders.kt`). Users can add or override providers by dropping files into
`<config folder>/auth-providers/`; a file with the same `id` as a built-in replaces it. Comments and trailing
commas are allowed. Broken files are skipped and listed on the Accounts screen.

```jsonc
{
  "id": "immich",                         // lowercase letters, digits, dashes
  "name": "Immich",
  "icon": "photo",                         // optional
  "fields": [                              // extra things the user types in, usable as {key}
    { "key": "instance_url", "label": "Server address", "placeholder": "http://immich.local:2283" }
  ],
  "methods": [ { "type": "apiKey" } ],     // one or more ways to sign in (below)
  "inject": { "type": "header", "name": "x-api-key", "format": "{api_key}" },
  "extraHeaders": { },                     // added to every signed-in request
  "accountInfo": { "url": "{instance_url}/api/users/me", "label": "$.email" },   // optional account name
  "baseUrls": ["{instance_url}/"]          // the only places credentials are ever sent
}
```

**Placeholders**: `{access_token}`, `{api_key}`, `{client_id}`, `{client_secret}`, plus every field `key`.
Unknown placeholders are rejected when the file loads. Trailing `/` is removed from address fields, so write
`{instance_url}/api`.

**`inject`** (default `Authorization: Bearer {access_token}`):
- `{"type": "header", "name": "...", "format": "..."}`
- `{"type": "query", "name": "appid", "format": "{api_key}"}`

**`accountInfo.label`** is a simple path: `$.email`, `$.user.username`.

### Methods

| `type` | Fields | Notes |
|---|---|---|
| `apiKey` | `label`, `help` | Pasted key, available as `{api_key}`. |
| `bearer` | `label`, `help` | Pasted long-lived token, stored as `{access_token}`. |
| `oauth2.deviceCode` | `deviceCodeUrl`, `tokenUrl`, `revokeUrl`, `scopes`, `scopeSeparator`, `client`, `clientAuth`, `bodyEncoding`, `refresh`, `pollStatusMap`, `deviceCodeParam` | RFC 8628. **Preferred** (see below). |
| `oauth2.authorizationCode` | `authorizeUrl`, `tokenUrl`, `revokeUrl`, `scopes`, `scopeSeparator`, `pkce` (default true), `extraAuthorizeParams`, `client`, `clientAuth`, `bodyEncoding`, `refresh`, `companion` | Needs a browser: done through the companion app. |
| `custom` | `handler` | Kotlin code a plugin registers (e.g. Alexa). Not wired up yet. |

Shared OAuth options:
- `client`: `{"mode": "byo"}` (user enters their own client id/secret), `{"mode": "fixed", "id": "...", "secret": "..."}`
  (placeholders allowed), or `{"mode": "byoOrServer"}` (own client, or the optional server's once it exists).
- `clientAuth`: `body` (client id/secret in the body, default), `basic` (HTTP Basic header), `none` (only `client_id`).
- `bodyEncoding`: `form` (default) or `json` (Trakt).
- `refresh`: `{"skewSeconds": 60, "rotateRefreshToken": false, "url": "...", "extraParams": {}}`. `url` is for
  services that refresh at a different endpoint than the one that issued the token. A new refresh token is always
  saved when one comes back; otherwise the old one is kept.
- `pollStatusMap` (device code): for services that answer polls with status codes rather than the standard
  `{"error": "authorization_pending"}` body. Values: `pending`, `slowDown`, `expired`, `denied`, `invalid`.
- `deviceCodeParam`: the name of the device code in poll requests (`device_code`; Trakt uses `code`).

### Which method to choose

Sekund runs on a wall display with no keyboard and often no browser, so:

1. **Use `oauth2.deviceCode` whenever the service supports it** for the scopes you need. The user scans a
   code or opens a short URL on their phone and types a few characters. Nothing else is needed.
2. Use `apiKey` or `bearer` when the service offers keys or long-lived tokens (self-hosted services usually do).
3. Use `oauth2.authorizationCode` only when neither is possible. It needs the companion app to do the browser part.

Check the scope list before choosing device code. **Google**, for example, only allows a few scopes on its device
flow (`openid`, `email`, `profile`, `drive.appdata`, `drive.file`, `youtube`, `youtube.readonly`); Calendar and
Photos aren't among them, so the Google definition uses the authorization-code flow.

### Security rules

- Credentials are stored encrypted (Android Keystore; on desktop the OS keychain or an owner-only key file),
  never in `config.json` or `accounts.json`.
- Plugins never see credentials, only signed-in clients.
- Credentials are only sent to URLs starting with one of `baseUrls`.
