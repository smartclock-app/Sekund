package uk.dnpk.sekund.protocol.update

/**
 * A release version like `2.1.0` (a leading `v`, as in git tags, is ignored). Missing parts count as 0,
 * so `2.1` equals `2.1.0`. Anything after a `-` or `+` (`2.1.0-beta`) is ignored.
 */
class AppVersion private constructor(private val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val longest = maxOf(parts.size, other.parts.size)
        for (index in 0 until longest) {
            val mine = parts.getOrElse(index) { 0 }
            val theirs = other.parts.getOrElse(index) { 0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }
        return 0
    }

    override fun toString(): String = parts.joinToString(".")

    companion object {
        /** Null for anything that isn't numbers separated by dots, e.g. `dev`. */
        fun parse(text: String): AppVersion? {
            val core = text.trim().removePrefix("v").substringBefore('-').substringBefore('+')
            if (core.isEmpty()) return null
            val parts = mutableListOf<Int>()
            for (piece in core.split(".")) {
                val number = piece.toIntOrNull() ?: return null
                if (number < 0) return null
                parts.add(number)
            }
            return AppVersion(parts)
        }
    }
}
