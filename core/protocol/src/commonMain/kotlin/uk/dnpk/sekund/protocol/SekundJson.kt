package uk.dnpk.sekund.protocol

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration used everywhere in Sekund (files, device API, relay).
 *
 * - `ignoreUnknownKeys`: newer companions/servers may send fields we don't know yet. Ignore them, don't crash.
 * - `encodeDefaults`: write every field, so files and API responses are self-describing.
 * - `classDiscriminator = "kind"`: sealed classes are written as `{"kind": "text", ...}`.
 */
val SekundJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "kind"
}

/** Same as [SekundJson] but indented. Used for files people edit by hand (e.g. config.json). */
val SekundPrettyJson: Json = Json(from = SekundJson) {
    prettyPrint = true
}
