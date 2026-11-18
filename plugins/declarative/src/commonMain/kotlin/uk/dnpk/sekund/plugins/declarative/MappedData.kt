package uk.dnpk.sekund.plugins.declarative

/**
 * What a source's `map` step produced: named values for the whole answer, plus rows (one per list entry).
 * Views fill their templates from these, e.g. `{temp}` or, inside a list row, `{title}`.
 */
data class MappedData(
    val fields: Map<String, String> = emptyMap(),
    val rows: List<Map<String, String>> = emptyList(),
) {
    /** Combines two results: later fields win, rows are joined. Used for pages and `forEach` entries. */
    fun plus(other: MappedData): MappedData = MappedData(fields = fields + other.fields, rows = rows + other.rows)
}
