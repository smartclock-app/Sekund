package uk.dnpk.sekund.plugins.declarative

import uk.dnpk.sekund.protocol.declarative.DeclarativeParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The loader rejects definitions with mistakes, and says exactly where they are. */
class DefinitionValidationTest {
    @Test
    fun reportsMistakesWithTheirPaths() {
        val parsed = DeclarativeParser.parse(
            """
            {
              // comments and trailing commas are fine
              "manifest": { "id": "Bad Id", "name": "Broken", "version": "1" },
              "hosts": ["ftp://files.example.com"],
              "source": {
                "url": "https://api.example.com/{settings.missing}",
                "auth": "example",
                "refresh": "10s",
                "map": { "fields": { "a": "{$.a|shout}", "b": "{$.b|convert:C:parsecs}", "c": "{$.c|convert:C:settings.nope}" } },
              },
            }
            """,
        )

        assertNull(parsed.definition)
        val paths = parsed.issues.map { issue -> issue.path }.toSet()
        assertEquals(
            setOf("manifest.id", "hosts[0]", "view", "source.url", "source.refresh", "source.auth", "source.map.fields.a", "source.map.fields.b", "source.map.fields.c"),
            paths,
        )
    }

    @Test
    fun aPhotoSourceMustMapUrls() {
        val parsed = DeclarativeParser.parse(
            """
            { "manifest": { "id": "pics", "name": "Pics", "version": "1" }, "kind": "photoSource",
              "hosts": ["https://x.example.com/"],
              "source": { "url": "https://x.example.com/", "map": { "rows": { "path": "$", "fields": { "id": "{$.id}" } } } } }
            """,
        )
        assertEquals(listOf("source.map.rows"), parsed.issues.map { issue -> issue.path })
    }
}
