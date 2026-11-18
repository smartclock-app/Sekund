package uk.dnpk.sekund.plugins.declarative

import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.JsonObject
import uk.dnpk.sekund.protocol.declarative.DeclarativeDefinition
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.photos.Photo
import uk.dnpk.sekund.sdk.photos.PhotoSource
import kotlin.time.Instant

/**
 * Photos for the slideshow clock face from a declarative definition. Each mapped row becomes a [Photo]:
 * `url` (required), and optionally `id`, `caption` and `takenAt`.
 */
class DeclarativePhotoSource(
    private val definition: DeclarativeDefinition,
    context: InstanceContext<JsonObject>,
) : PhotoSource {
    private val templates = TemplateEngine(
        lookups = definition.lookups,
        timeZone = { context.time.timeZone.value },
        preferences = { context.time.preferences.value },
    )
    private val runner = SourceRunner(definition, context.settings, context.http, context.auth, templates)
    private val timeZone: () -> TimeZone = { context.time.timeZone.value }

    override val photos: StateFlow<Loadable<List<Photo>>> =
        context.refresher.poll(Refresh.policyFor(definition.source)) { toPhotos(runner.run(definition.source)) }

    /** Fetches the picture with the definition's host check and sign-in, like the `image` view does. */
    override suspend fun download(photo: Photo): ByteArray {
        val response = runner.clientForImage(photo.url).get(photo.url)
        if (response.status.value !in 200..299) {
            throw IllegalStateException("Photo ${photo.id}: HTTP ${response.status.value}")
        }
        return response.readRawBytes()
    }

    private fun toPhotos(data: MappedData): List<Photo> {
        val result = mutableListOf<Photo>()
        for (row in data.rows) {
            val url = row["url"]
            if (url.isNullOrEmpty()) continue // a row without a picture can't be shown
            result.add(
                Photo(
                    id = row["id"]?.ifEmpty { null } ?: url,
                    url = url,
                    caption = row["caption"]?.ifEmpty { null },
                    takenAt = takenAt(row["takenAt"]),
                ),
            )
        }
        return result
    }

    private fun takenAt(text: String?): Instant? {
        if (text.isNullOrEmpty()) return null
        val zone = timeZone()
        val local = ValueFormat.parseDateTime(text, zone) ?: return null
        return local.toInstant(zone)
    }
}
