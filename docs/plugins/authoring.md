# Writing plugins

A plugin is a Kotlin module in `plugins/` that adds things to Sekund: widgets, clock faces, calendar sources,
photo sources. It's written against `:app:sdk` only, never the runtime, so it can be tested on its own.

If all you need is "call an API and show the result", you may not need Kotlin at all: see
[declarative widgets](declarative-widgets.md).

This guide walks through a complete plugin, a **quote of the day** widget. The code is real: it lives in
[`plugins/example`](../../plugins/example), and is compiled and tested with everything else, so it can't go
out of date. After that come [accounts](#using-accounts-from-a-plugin) and [webhooks](#webhooks-being-told-when-something-changes).

## A complete example: the quote widget

### 1. The module

Create `plugins/example/build.gradle.kts`. Any folder in `plugins/` with a build file joins the build by itself.

```kotlin
plugins {
    id("sekund.plugin")   // Kotlin Multiplatform + Compose + :app:sdk, set up for you
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(project(":app:sdk-testing"))   // fakes for tests
        }
    }
}
```

Put the code in `src/commonMain/kotlin/...`, so it runs on Android and desktop alike.

### 2. The settings

Declare each setting once. From that, Sekund reads `config.json` (bad values fall back to defaults and show a
warning instead of crashing), generates the settings screen, and describes the settings to the companion.

```kotlin
data class QuoteSettings(
    val url: String,
    val showAuthor: Boolean,
)

object QuoteSettingsSchema : SettingsSchema<QuoteSettings>() {
    private val url = text(
        "url",
        label = "Quote API",
        description = "Returns JSON like {\"quote\": \"...\", \"author\": \"...\"}.",
        default = "https://quotes.example.com/today",
        required = true,
    )
    private val showAuthor = toggle("showAuthor", label = "Show the author", default = true)

    override fun build(values: SettingsValues) = QuoteSettings(
        url = values[url].trim(),
        showAuthor = values[showAuthor],
    )
}
```

The other kinds are `number`, `choice` (an enum), `duration`, `color`, `textList`, `objectList`, `account`
(a signed-in account, see below) and `instanceRefs` (other instances, like the calendar's sources). A plugin
with nothing to set uses `NoSettings`.

### 3. The widget

A widget is a class that gets an `InstanceContext` (its settings plus everything Sekund offers: HTTP, time,
storage, alerts...) and draws itself in `Content`. Sekund creates one per configured instance, and throws it
away when the instance is removed or its settings change.

```kotlin
@Serializable
data class Quote(val quote: String, val author: String = "")

class QuoteWidget(private val context: InstanceContext<QuoteSettings>) : Widget {
    private val json = Json { ignoreUnknownKeys = true }

    val quote: StateFlow<Loadable<Quote>> = context.refresher.poll(RefreshPolicy(every = 6.hours)) { fetchQuote() }

    private suspend fun fetchQuote(): Quote {
        val response = context.http.get(context.settings.url)
        if (!response.status.isSuccess()) {
            throw IllegalStateException("The quote API answered ${response.status.value}")
        }
        return json.decodeFromString<Quote>(response.bodyAsText())
    }

    @Composable
    override fun Content(location: WidgetLocation) {
        when (val state = quote.collectAsState().value) {
            is Loadable.Loading -> {}
            is Loadable.Failed -> ErrorCard(title = "No quote", message = state.message)
            is Loadable.Ready -> QuoteCard(state.data, context.settings.showAuthor)
        }
    }
}
```

- **`context.refresher.poll`** is how almost every plugin loads data. You write the fetch; Sekund runs it now
  and then every 6 hours, again when the network returns or the display wakes, pauses it while the screen is
  off, retries failures with growing delays, and keeps the last good data when a refresh fails. Throw an
  exception with a readable message when something's wrong: it becomes `Loadable.Failed` (or a stale `Ready`).
- **`StateFlow`** is a value that changes over time (think: a small zustand store). `collectAsState()` makes
  the composable redraw when it changes.
- **Never start work outside `context.scope`**. If you need your own coroutine, `context.scope.launch { }`;
  Sekund cancels it with the instance.
- **Draw with Sekund's components and tokens** (`SekundCard`, `ErrorCard`, `Sekund.theme.colors...`) so the
  widget matches the rest of the screen. `QuoteCard` is in [`QuoteWidget.kt`](../../plugins/example/src/commonMain/kotlin/uk/dnpk/sekund/plugins/example/QuoteWidget.kt).

### 4. The plugin object

The plugin says who it is and what it contributes. `register` only registers; work starts when instances
are created.

```kotlin
object QuotePlugin : SekundPlugin {
    const val QUOTE = "quote"

    override val manifest = PluginManifest(
        id = "example",
        name = "Example",
        version = "1.0.0",
        description = "A quote of the day. The example from the plugin guide.",
    )

    override fun register(registrar: PluginRegistrar) {
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.Widgets,
                type = QUOTE,
                name = "Quote",
                description = "A quote, refreshed every few hours.",
                settings = QuoteSettingsSchema,
                widgetLocations = setOf(WidgetLocation.Sidebar),
                create = { context -> QuoteWidget(context) },
            ),
        )
    }
}
```

The extension points are `Widgets`, `ClockFaces`, `CalendarSources` and `PhotoSources`. A plugin can
contribute to several, as many times as it likes (`plugins/google` adds a calendar source; `plugins/slideshow`
a clock face).

### 5. A test

`:app:sdk-testing` has a `FakeInstanceContext` where nothing happens by itself: you choose what the API
answers and when data refreshes.

```kotlin
@Test
fun `loads the quote from the configured address`() = runTest {
    val api = FakeApi { request ->
        if (request.url.toString() == "https://quotes.test/today") {
            json("""{"quote": "Simplicity is prerequisite for reliability.", "author": "Dijkstra", "id": 7}""")
        } else {
            null // Anything else answers 404.
        }
    }
    val context = FakeInstanceContext(settings, backgroundScope, api = api)
    val widget = QuoteWidget(context)

    context.refresher.runAll()

    val state = assertIs<Loadable.Ready<Quote>>(widget.quote.value)
    assertEquals(Quote("Simplicity is prerequisite for reliability.", "Dijkstra"), state.data)
}
```

Run it with `./gradlew :plugins:example:jvmTest`. The fake context also records alerts, overlays and log
lines, and has fake time (`context.time.set(...)`), storage and accounts.

### 6. Adding it to the app

The example stops here on purpose. For a real plugin:

1. Add the module to the dependencies in `app/shared/build.gradle.kts`: `implementation(project(":plugins:quote"))`.
2. Add the plugin object to the list in `app/shared/.../BuiltInPlugins.kt`.
3. Run the app (`./gradlew :app:desktopApp:run`), long-press, and add the widget in Settings. Or add it to
   `config.json` by hand:

   ```json
   "instances": [
     { "id": "quote-1", "type": "quote", "settings": { "url": "https://quotes.example.com/today" } }
   ],
   "layout": { "sidebar": ["quote-1"] }
   ```

### What else the context offers

| `context.` | For |
|---|---|
| `time` | The current time, ticking each second or minute, and the 12/24-hour preference. |
| `storage` | A small key/value store kept across restarts (cache the last data for a quick start). |
| `files` | A folder for downloads, e.g. photos for offline use. May be cleared. |
| `alerts` | An alert card at the top of the sidebar ("Your Trakt account needs signing in again"). |
| `overlays` | A full-screen overlay, e.g. a doorbell camera. |
| `events` | Messages between plugins, and Sekund's own events (display on/off, network changed). |
| `instances` | Other instances, like the calendar sources a calendar widget shows. |
| `auth` | Signed-in HTTP clients; see below. |
| `webhooks` | Public URLs other services can call; see below. |
| `log` | Sekund's log, tagged with the instance id. |

Each is documented in `app/sdk/src/commonMain/.../sdk/context/`. The plugins in `plugins/` are worked examples:
`calendar` (combining other instances), `trakt` (accounts + calendar source), `slideshow` (clock face +
photo sources), `homeassistant` (WebSocket, alerts, overlays, video), `alexa` (a custom sign-in).

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

4. Rarely, a connection carries the token *inside* it rather than on HTTP requests: Home Assistant's WebSocket
   wants it in its first message. For that one case, `context.auth.connectionToken(ref, url)` returns the token,
   but only if the provider's definition says `"connectionToken": true` and `url` is within its `baseUrls`
   (`ws://`/`wss://` count as `http://`/`https://`). Everything else should use the client.

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
  "baseUrls": ["{instance_url}/"],         // the only places credentials are ever sent
  "connectionToken": false                 // optional: see "connectionToken" below
}
```

**Placeholders**: `{access_token}`, `{api_key}`, `{client_id}`, `{client_secret}`, plus every field `key`.
Unknown placeholders are rejected when the file loads. Trailing `/` is removed from address fields, so write
`{instance_url}/api`.

**`inject`** (default `Authorization: Bearer {access_token}`):
- `{"type": "header", "name": "...", "format": "..."}`
- `{"type": "query", "name": "appid", "format": "{api_key}"}`

**`accountInfo.label`** is a simple path: `$.email`, `$.user.username`.

**`connectionToken`** (default `false`): lets plugins read the token with `context.auth.connectionToken` for a
connection that carries it inside (Home Assistant's WebSocket). Leave it off unless the service needs it.

### Methods

| `type` | Fields | Notes |
|---|---|---|
| `apiKey` | `label`, `help` | Pasted key, available as `{api_key}`. |
| `bearer` | `label`, `help` | Pasted long-lived token, stored as `{access_token}`. |
| `oauth2.deviceCode` | `deviceCodeUrl`, `tokenUrl`, `revokeUrl`, `scopes`, `scopeSeparator`, `client`, `clientAuth`, `bodyEncoding`, `refresh`, `pollStatusMap`, `deviceCodeParam` | RFC 8628. **Preferred** (see below). |
| `oauth2.authorizationCode` | `authorizeUrl`, `tokenUrl`, `revokeUrl`, `scopes`, `scopeSeparator`, `pkce` (default true), `extraAuthorizeParams`, `client`, `clientAuth`, `bodyEncoding`, `refresh`, `companion` | Needs a browser: done through the companion app. |
| `custom` | `handler`, optional `label`, `help` | Kotlin code a plugin registers (e.g. Amazon for Alexa). See below. |

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

### Custom sign-in code

When a service can't be described in JSON (Amazon swaps a refresh token for cookies and a CSRF token),
its definition uses `{"type": "custom", "handler": "amazon-cookie"}` and a plugin registers the code:

```kotlin
override fun register(registrar: PluginRegistrar) {
    registrar.authHandler("amazon-cookie", AmazonCookieHandler)
}
```

A `CustomAuthHandler` has two functions: `refresh` turns the pasted secret into a session (throw
`CustomAuthRejectedException` if the secret itself is refused), and `sign` puts the session on a request
(with `headers.set`, as it runs again before a retry). Storage, encryption, one refresh at a time, the
retry after `401` and the reconnect alert are shared with every other method.

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

## Webhooks: being told when something changes

When a device is connected to a Sekund server that relays webhooks ([docs/server.md](../server.md)), an
instance can get public URLs that other services call when something changes:

```kotlin
// Tell the service where to call whenever the URL is (re)issued; null means there's no server.
context.scope.launch {
    context.webhooks.url("changes").collectLatest { url ->
        if (url != null) api.subscribe(callbackUrl = url)
    }
}
// Refresh as soon as it calls.
context.scope.launch {
    context.webhooks.calls("changes").collect { call -> context.refresher.refreshNow() }
}
```

- The same instance and name keep the same URL, across restarts too.
- **Keep polling.** Without a server there are no URLs, and a call can be missed; webhooks only make updates
  arrive sooner.
- Calls can arrive twice (delivery is at-least-once), and anyone who learns the URL can call it: check what
  you can (Google's channel token, a signature header) and treat a call as "go and look", not as data to trust.
- In tests, `FakeInstanceContext.webhooks` (`FakeWebhooks`) lets you `setUrl` and `call` by hand.

`plugins/google/GoogleCalendarPush.kt` is a complete example.
