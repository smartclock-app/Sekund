package uk.dnpk.sekund.runtime.api

import uk.dnpk.sekund.runtime.platform.PlatformServices
import uk.dnpk.sekund.sdk.actions.HasActions
import uk.dnpk.sekund.sdk.actions.PluginAction
import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import uk.dnpk.sekund.protocol.SekundJson
import uk.dnpk.sekund.protocol.api.PairResult
import uk.dnpk.sekund.protocol.api.PairStarted
import uk.dnpk.sekund.protocol.config.ApiConfig
import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.protocol.config.InstanceConfig
import uk.dnpk.sekund.protocol.config.LayoutConfig
import uk.dnpk.sekund.protocol.declarative.DeclarativeDefinition
import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.runtime.AppGraph
import uk.dnpk.sekund.runtime.AppPaths
import uk.dnpk.sekund.runtime.auth.secrets.SecretCipher
import uk.dnpk.sekund.runtime.newTestPaths
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.plugin.PluginRegistrar
import uk.dnpk.sekund.sdk.plugin.SekundPlugin
import uk.dnpk.sekund.sdk.settings.NoSettings
import uk.dnpk.sekund.sdk.settings.SettingsSchema
import uk.dnpk.sekund.sdk.settings.SettingsValues
import uk.dnpk.sekund.sdk.widget.Widget
import uk.dnpk.sekund.sdk.widget.WidgetLocation

/** Not real encryption: tests only need the bytes to come back the same. */
private object PlainCipher : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray = plain

    override fun decrypt(encrypted: ByteArray): ByteArray = encrypted
}

private object NoteSchema : SettingsSchema<Int>() {
    val size = number("size", label = "Size", default = 1, min = 1, max = 5)

    override fun build(values: SettingsValues) = values[size]
}

/** Draws nothing; offers two actions, one that works ("ping") and one that fails ("explode"). */
private object EmptyWidget : Widget, HasActions {
    @Composable
    override fun Content(location: WidgetLocation) {
    }

    override val actions = listOf(
        PluginAction("ping", "Does nothing") {},
        PluginAction("explode", "Always fails") { error("Kaboom") },
    )
}

/** A plugin with one sidebar widget type, "note", that has a single number setting (1 to 5). */
private object NotePlugin : SekundPlugin {
    override val manifest = PluginManifest(id = "notes", name = "Notes", version = "1")

    override fun register(registrar: PluginRegistrar) {
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.Widgets,
                type = "note",
                name = "Note",
                settings = NoteSchema,
                widgetLocations = setOf(WidgetLocation.Sidebar),
                create = { EmptyWidget },
            ),
        )
    }
}

/** Stands in for the declarative engine: each definition becomes one sidebar widget type. */
private fun fakeDeclarativePlugin(definition: DeclarativeDefinition): SekundPlugin = object : SekundPlugin {
    override val manifest = definition.manifest

    override fun register(registrar: PluginRegistrar) {
        registrar.contribute(
            Contribution(
                point = ExtensionPoints.Widgets,
                type = definition.manifest.id,
                name = definition.manifest.name,
                settings = NoSettings,
                widgetLocations = setOf(WidgetLocation.Sidebar),
                create = { EmptyWidget },
            ),
        )
    }
}

/**
 * A real AppGraph in a temporary folder, with one note instance, started and ready. [beforeStart] can
 * put files in place first (e.g. an old config.json). The API server
 * itself isn't started: tests install [deviceApiModule] in Ktor's test engine instead.
 */
fun startedTestGraph(services: PlatformServices = PlatformServices(), beforeStart: (AppPaths) -> Unit = {}): AppGraph {
    val paths = newTestPaths()
    beforeStart(paths)
    val graph = AppGraph(
        paths = paths,
        plugins = listOf(NotePlugin),
        defaultConfig = AppConfig(
            clockFace = "none",
            instances = listOf(InstanceConfig(id = "note", type = "note")),
            layout = LayoutConfig(sidebar = listOf("note")),
            api = ApiConfig(enabled = false),
        ),
        secretCipher = PlainCipher,
        declarativePlugin = ::fakeDeclarativePlugin,
        platform = PlatformInfo(name = "test", appVersion = "0.0.1"),
        services = services,
    )
    runBlocking {
        graph.start().join()
        graph.api.clients.load()
    }
    return graph
}

/** Pairs a client the way a companion would, reading the PIN off the "screen" (the alert). */
suspend fun pair(client: HttpClient, graph: AppGraph): PairResult {
    val answer = client.post("/v1/pair") { jsonBody("""{ "clientName": "Test phone" }""") }
    check(answer.status.value == 200) { "Pairing failed: ${answer.status} ${answer.bodyAsText()}" }
    val started = SekundJson.decodeFromString(PairStarted.serializer(), answer.bodyAsText())
    val pin = pinShownOnScreen(graph)
    val confirmed = client.post("/v1/pair/confirm") { jsonBody("""{ "pairingId": "${started.pairingId}", "pin": "$pin" }""") }
    return SekundJson.decodeFromString(PairResult.serializer(), confirmed.bodyAsText())
}

/** The PIN from the pairing alert, e.g. "PIN 042 917. Enter it..." -> "042917". */
fun pinShownOnScreen(graph: AppGraph): String {
    val alert = graph.alerts.alerts.value.entries.first { entry -> entry.key.startsWith("pairing:") }.value
    return alert.subtitle!!.substringAfter("PIN ").substringBefore('.').replace(" ", "")
}

fun HttpRequestBuilder.jsonBody(json: String) {
    contentType(ContentType.Application.Json)
    setBody(json)
}

fun HttpRequestBuilder.signedIn(pairing: PairResult) {
    bearerAuth(pairing.token)
}
