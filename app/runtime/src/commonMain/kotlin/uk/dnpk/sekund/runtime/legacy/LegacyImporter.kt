package uk.dnpk.sekund.runtime.legacy

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import uk.dnpk.sekund.protocol.config.UpdatesConfig
import uk.dnpk.sekund.protocol.config.ApiConfig
import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.protocol.config.ClockConfig
import uk.dnpk.sekund.protocol.config.InstanceConfig
import uk.dnpk.sekund.protocol.config.LayoutConfig
import uk.dnpk.sekund.protocol.config.Orientation
import uk.dnpk.sekund.runtime.auth.Credential
import kotlin.time.Instant

/**
 * Turns the old Tauri app's config.json into the new format (a [LegacyImportPlan]). Nothing is changed
 * here; [LegacyImport] applies the plan.
 *
 * What moves where:
 * - `orientation`, `clock.format`/`showSeconds` and `remoteConfig` (port, name) keep their meaning.
 * - Each old widget that was **in use** (in the layout, a calendar extension, or the clock theme) becomes
 *   an instance with the same id. Unused ones are skipped (listed in the notes).
 * - Every credential found (API keys, OAuth tokens) becomes a connected account, even for widgets that
 *   weren't in use, so nothing secret is lost. Config files then only refer to the account's id.
 *
 * The new plugin types and setting keys are listed in [Types] and in each `*Instance` function below.
 * Plugins that don't exist yet (they arrive in later phases) show as "not installed" until they do.
 */
object LegacyImporter {

    /** The new types the old widgets map to. Keep in step with the plugins as they're written. */
    object Types {
        const val CLOCK = "digital-clock"
        const val CALENDAR = "calendar"
        const val WEATHER = "weather"
        const val GOOGLE_CALENDAR = "google-calendar"
        const val TRAKT = "trakt"
        const val SLIDESHOW = "slideshow"
        const val IMMICH_PHOTOS = "immich-photos"
        const val ACTUAL_BUDGET = "actualbudget"
        const val HOME_ASSISTANT = "homeassistant"
        const val ALEXA = "alexa"
    }

    /** True if [json] looks like an old Tauri config: it has `widgets` and no `instances`. */
    fun isLegacy(json: JsonElement): Boolean {
        if (json !is JsonObject) return false
        return json.containsKey("widgets") && !json.containsKey("instances")
    }

    fun plan(old: JsonObject): LegacyImportPlan {
        val builder = PlanBuilder(old)
        builder.build()
        return LegacyImportPlan(builder.config(), builder.accounts, builder.notes)
    }

    /** Collects the plan step by step. One function per old widget keeps each mapping easy to find. */
    private class PlanBuilder(private val old: JsonObject) {
        val accounts = mutableListOf<ImportedAccount>()
        val notes = mutableListOf<String>()
        private val instances = mutableListOf<InstanceConfig>()
        private var updates = UpdatesConfig()
        private val widgets = LegacyJson.obj(old, "widgets")

        private val layoutMain = LegacyJson.texts(LegacyJson.obj(old, "layout"), "main")
        private val layoutSidebar = LegacyJson.texts(LegacyJson.obj(old, "layout"), "sidebar")
        private val calendarExtensions = LegacyJson.texts(LegacyJson.obj(old, "calendar"), "extensions")
        private val clockTheme = LegacyJson.text(old, "clockTheme") ?: "default"

        /** Which old widgets were actually shown somewhere. */
        private val inUse: Set<String> = (layoutMain + layoutSidebar + calendarExtensions + listOf(clockTheme)).toSet()

        fun build() {
            instances.add(InstanceConfig(id = "clock", type = Types.CLOCK))
            if ("calendar" in inUse) calendarInstance()

            weather()
            google()
            trakt()
            photos()
            actualBudget()
            homeAssistant()
            alexa()
            updater()

            val known = setOf("calendar", "default", "weather", "google", "trakt", "photos", "actualbudget", "homeassistant", "alexa", "updater")
            for (name in inUse - known) {
                notes.add("'$name' isn't a widget Sekund knows about, so it was left out")
            }
            if (LegacyJson.text(LegacyJson.obj(old, "remoteConfig"), "password") != null) {
                notes.add("The old remote-config password isn't used any more: companions now pair with a PIN shown on screen")
            }
        }

        fun config(): AppConfig {
            val placed = instances.map { instance -> instance.id }.toSet()
            return AppConfig(
                orientation = if (LegacyJson.text(old, "orientation") == "portrait") Orientation.Portrait else Orientation.Landscape,
                clock = ClockConfig(
                    use24Hour = LegacyJson.text(LegacyJson.obj(old, "clock"), "format") == "24h",
                    showSeconds = LegacyJson.bool(LegacyJson.obj(old, "clock"), "showSeconds") ?: true,
                ),
                clockFace = if (clockTheme == "photos") "photos" else "clock",
                layout = LayoutConfig(
                    main = layoutMain.filter { id -> id in placed },
                    sidebar = layoutSidebar.filter { id -> id in placed },
                ),
                instances = instances,
                api = apiConfig(),
                updates = updates,
            )
        }

        private fun apiConfig(): ApiConfig {
            val remote = LegacyJson.obj(old, "remoteConfig") ?: return ApiConfig()
            return ApiConfig(
                enabled = LegacyJson.bool(remote, "enabled") ?: true,
                port = LegacyJson.number(remote, "port")?.toInt() ?: 8080,
                deviceName = LegacyJson.text(remote, "bonjourName") ?: "Sekund",
            )
        }

        // ---- One function per old widget ------------------------------------------------------

        private fun calendarInstance() {
            val calendar = LegacyJson.obj(old, "calendar")
            val titles = LegacyJson.obj(calendar, "titles")
            val settings = settingsOf(
                "maxEvents" to number(LegacyJson.number(calendar, "maxEvents")?.toInt() ?: 50),
                "oddWeekTitle" to text(LegacyJson.text(titles, "odd") ?: ""),
                "evenWeekTitle" to text(LegacyJson.text(titles, "even") ?: ""),
                "titleFilters" to texts(LegacyJson.texts(calendar, "eventFilter")),
                // Calendar extensions become the calendar's sources, by instance id.
                "sources" to texts(calendarExtensions),
            )
            instances.add(InstanceConfig(id = "calendar", type = Types.CALENDAR, settings = settings))
        }

        private fun weather() {
            val widget = LegacyJson.obj(widgets, "weather") ?: return
            val account = account("openweather", "apiKey", "OpenWeather (imported)", emptyMap(), Credential(apiKey = LegacyJson.text(widget, "apiKey")))
            if ("weather" !in inUse) return
            instances.add(
                InstanceConfig(
                    id = "weather",
                    type = Types.WEATHER,
                    settings = settingsOf(
                        "account" to optional(account),
                        "postcode" to text(LegacyJson.text(widget, "postcode") ?: ""),
                        "country" to text(LegacyJson.text(widget, "country") ?: "GB"),
                        // The old app always labelled wind "mph"; temperature followed its units setting.
                        "temperatureUnit" to text(if (LegacyJson.text(widget, "units") == "imperial") "F" else "C"),
                        "windUnit" to text("mph"),
                    ),
                ),
            )
        }

        private fun google() {
            val widget = LegacyJson.obj(widgets, "google") ?: return
            val credential = Credential(
                accessToken = LegacyJson.text(widget, "accessToken"),
                refreshToken = LegacyJson.text(widget, "refreshToken"),
                expiresAtEpochSeconds = epochSeconds(LegacyJson.text(widget, "tokenExpiry")),
                clientId = LegacyJson.text(widget, "clientId"),
                clientSecret = LegacyJson.text(widget, "clientSecret"),
            )
            val account = account("google", "oauth2.authorizationCode", "Google (imported)", emptyMap(), credential)
            if ("google" !in inUse) return
            instances.add(InstanceConfig(id = "google", type = Types.GOOGLE_CALENDAR, settings = settingsOf("account" to optional(account))))
        }

        private fun trakt() {
            val widget = LegacyJson.obj(widgets, "trakt") ?: return
            val auth = LegacyJson.obj(widget, "auth")
            val credential = Credential(
                accessToken = LegacyJson.text(auth, "accessToken"),
                refreshToken = LegacyJson.text(auth, "refreshToken"),
                clientId = LegacyJson.text(auth, "clientId"),
                clientSecret = LegacyJson.text(auth, "clientSecret"),
            )
            val account = account("trakt", "oauth2.deviceCode", "Trakt (imported)", emptyMap(), credential)
            if ("trakt" !in inUse) return
            instances.add(
                InstanceConfig(
                    id = "trakt",
                    type = Types.TRAKT,
                    settings = settingsOf(
                        "account" to optional(account),
                        "listId" to text(LegacyJson.text(widget, "listId") ?: ""),
                        "includeWatchlist" to bool(LegacyJson.bool(widget, "includeWatchlist") ?: false),
                        "episodesAsShow" to bool(LegacyJson.bool(widget, "includeEpisodesAsShow") ?: false),
                        "prefix" to text(LegacyJson.text(widget, "prefix") ?: ""),
                        "color" to text(colorOf(LegacyJson.text(widget, "color"))),
                        "maxItems" to number(LegacyJson.number(widget, "maxItems")?.toInt() ?: 50),
                    ),
                ),
            )
        }

        /** The old photo clock theme becomes the slideshow clock face plus an Immich photo source. */
        private fun photos() {
            val widget = LegacyJson.obj(widgets, "photos") ?: return
            val server = LegacyJson.text(widget, "immichUrl")
            val fields = if (server == null) emptyMap() else mapOf("instance_url" to server)
            val account = account("immich", "apiKey", "Immich (imported)", fields, Credential(apiKey = LegacyJson.text(widget, "immichAccessToken")))
            if ("photos" !in inUse) return

            // The old interval was a count of 30-second ticks.
            val ticks = LegacyJson.number(widget, "refreshInterval")?.toInt() ?: 1
            instances.add(
                InstanceConfig(
                    id = "photos",
                    type = Types.SLIDESHOW,
                    settings = settingsOf(
                        "sources" to texts(listOf("immich")),
                        "rotateEvery" to text("${ticks.coerceAtLeast(1) * 30}s"),
                        "cacheToDisk" to bool(LegacyJson.bool(widget, "savePhotosToDisk") ?: false),
                    ),
                ),
            )
            instances.add(
                InstanceConfig(
                    id = "immich",
                    type = Types.IMMICH_PHOTOS,
                    settings = settingsOf(
                        "account" to optional(account),
                        "serverUrl" to text((server ?: "").trimEnd('/')),
                        "albumId" to text(LegacyJson.text(widget, "immichAlbumId") ?: ""),
                    ),
                ),
            )
            // The account's API key is enough now; a share key isn't needed.
            if (LegacyJson.bool(widget, "useStaticLinks") == true) {
                notes.add("The photo clock used a fixed list of image links; that option isn't carried over, so it now shows the Immich album")
            }
        }

        private fun actualBudget() {
            val widget = LegacyJson.obj(widgets, "actualbudget") ?: return
            if ("actualbudget" !in inUse) return
            // One entry per account to show, each remembering which budget it's in.
            val accountRows = mutableListOf<JsonElement>()
            for (budget in LegacyJson.array(widget, "budgets")) {
                val syncId = LegacyJson.text(budget, "syncId") ?: continue
                for (entry in LegacyJson.array(budget, "accounts")) {
                    accountRows.add(
                        settingsOf(
                            "syncId" to text(syncId),
                            "accountId" to text(LegacyJson.text(entry, "id") ?: ""),
                            "name" to text(LegacyJson.text(entry, "name") ?: "Account"),
                        ),
                    )
                }
            }
            instances.add(
                InstanceConfig(
                    id = "actualbudget",
                    type = Types.ACTUAL_BUDGET,
                    settings = settingsOf(
                        "serverUrl" to text(LegacyJson.text(widget, "actualHttpApiUrl") ?: ""),
                        "accounts" to JsonArray(accountRows),
                    ),
                ),
            )
            notes.add("Actual Budget now signs in with an API key: connect an account for it under Accounts")
        }

        private fun homeAssistant() {
            val widget = LegacyJson.obj(widgets, "homeassistant") ?: return
            val server = LegacyJson.text(widget, "url")
            val fields = if (server == null) emptyMap() else mapOf("instance_url" to server)
            val account = account("homeassistant", "bearer", "Home Assistant (imported)", fields, Credential(accessToken = LegacyJson.text(widget, "token")))
            if ("homeassistant" !in inUse) return
            val cameras = LegacyJson.array(widget, "cameras").map { camera ->
                settingsOf(
                    "entityId" to text(LegacyJson.text(camera, "id") ?: ""),
                    "trigger" to text(LegacyJson.text(camera, "trigger") ?: ""),
                    "streamUrl" to text(LegacyJson.text(camera, "streamUri") ?: ""),
                    "aspectRatio" to text((LegacyJson.number(camera, "aspectRatio") ?: (16.0 / 9.0)).toString()),
                )
            }
            instances.add(
                InstanceConfig(
                    id = "homeassistant",
                    type = Types.HOME_ASSISTANT,
                    settings = settingsOf(
                        "account" to optional(account),
                        "cameras" to JsonArray(cameras),
                        "showCameraFor" to text("${LegacyJson.number(widget, "cameraWaitTime")?.toInt() ?: 5}s"),
                    ),
                ),
            )
        }

        private fun alexa() {
            val widget = LegacyJson.obj(widgets, "alexa") ?: return
            // The old app only ever used amazon.co.uk.
            val fields = mapOf("domain" to "amazon.co.uk")
            // The old app kept Amazon's refresh token as "token"; its cookies are made again from it.
            val account = account("amazon", "custom", "Amazon (imported)", fields, Credential(refreshToken = LegacyJson.text(widget, "token")))
            if ("alexa" !in inUse) return
            val features = LegacyJson.obj(widget, "features")
            instances.add(
                InstanceConfig(
                    id = "alexa",
                    type = Types.ALEXA,
                    settings = settingsOf(
                        "account" to optional(account),
                        "showNowPlaying" to bool(LegacyJson.bool(features, "nowplaying") ?: true),
                        "showAlarms" to bool(LegacyJson.bool(features, "alarms") ?: true),
                        "showTimers" to bool(LegacyJson.bool(features, "timers") ?: true),
                        "devices" to texts(LegacyJson.texts(widget, "devices")),
                        "radioProviders" to texts(LegacyJson.texts(widget, "radioProviders")),
                    ),
                ),
            )
        }

        /** Updates are part of the app now, not a widget: the old widget's interval becomes a setting. */
        private fun updater() {
            val widget = LegacyJson.obj(widgets, "updater") ?: return
            if ("updater" !in inUse) return
            // The old setting was in minutes.
            val minutes = LegacyJson.number(widget, "updateInterval")?.toInt() ?: return
            updates = UpdatesConfig(checkEvery = "${minutes}m")
        }

        // ---- Helpers --------------------------------------------------------------------------

        /**
         * Plans an account if [credential] has anything in it, and returns its id (or null if there was
         * nothing to import). Ids are fixed (`acc_legacy_<provider>`), so importing twice replaces
         * rather than duplicates.
         */
        private fun account(provider: String, methodType: String, label: String, fields: Map<String, String>, credential: Credential): String? {
            val hasSecret = credential.apiKey != null || credential.accessToken != null || credential.refreshToken != null
            if (!hasSecret) return null
            val id = "acc_legacy_" + provider.replace('-', '_')
            accounts.add(ImportedAccount(id, provider, methodType, label, fields, credential))
            return id
        }

        private fun epochSeconds(iso: String?): Long? {
            if (iso == null) return null
            return try {
                Instant.parse(iso).epochSeconds
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        /** The old app stored `#AARRGGBB`-ish strings; keep anything that looks like a colour, else white. */
        private fun colorOf(text: String?): String {
            if (text == null || !text.startsWith("#")) return "#FFFFFF"
            return text.uppercase()
        }

        private fun settingsOf(vararg values: Pair<String, JsonElement>): JsonObject = JsonObject(linkedMapOf(*values))

        private fun text(value: String): JsonElement = JsonPrimitive(value)

        private fun number(value: Int): JsonElement = JsonPrimitive(value)

        private fun bool(value: Boolean): JsonElement = JsonPrimitive(value)

        private fun texts(values: List<String>): JsonElement = JsonArray(values.map { value -> JsonPrimitive(value) })

        private fun optional(value: String?): JsonElement = if (value == null) JsonNull else JsonPrimitive(value)
    }
}
