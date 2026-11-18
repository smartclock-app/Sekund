package uk.dnpk.sekund.plugins.alexa

/** One line of synced lyrics: it starts at [startMs] into the song. */
data class LyricLine(val startMs: Long, val text: String)

/** The lines around the current position, for the three-line lyrics view. */
data class LyricsWindow(val previous: String?, val current: String, val next: String?)

/**
 * Synced lyrics in LRC format, e.g.
 * ```
 * [00:12.34] First line
 * [00:15.00][01:20.50] A line that's sung twice
 * ```
 */
class Lyrics(val lines: List<LyricLine>) {

    /** The current line at [progressMs] with its neighbours. Before the first line, the first line is "current". */
    fun windowAt(progressMs: Long): LyricsWindow? {
        if (lines.isEmpty()) return null
        var index = 0
        for (i in lines.indices) {
            if (lines[i].startMs <= progressMs) index = i else break
        }
        return LyricsWindow(
            previous = lines.getOrNull(index - 1)?.text,
            current = lines[index].text,
            next = lines.getOrNull(index + 1)?.text,
        )
    }

    companion object {
        private val timestamp = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")

        /** Parses LRC text. Lines without a timestamp, or with no words, are skipped. Null if nothing usable. */
        fun parse(text: String): Lyrics? {
            val lines = mutableListOf<LyricLine>()
            for (raw in text.lines()) {
                val stamps = timestamp.findAll(raw).toList()
                if (stamps.isEmpty()) continue
                val words = raw.substring(stamps.last().range.last + 1).trim()
                if (words.isEmpty()) continue
                for (stamp in stamps) {
                    val minutes = stamp.groupValues[1].toLong()
                    val seconds = stamp.groupValues[2].toDouble()
                    lines.add(LyricLine(minutes * 60_000 + (seconds * 1000).toLong(), words))
                }
            }
            if (lines.isEmpty()) return null
            return Lyrics(lines.sortedBy { line -> line.startMs })
        }
    }
}
