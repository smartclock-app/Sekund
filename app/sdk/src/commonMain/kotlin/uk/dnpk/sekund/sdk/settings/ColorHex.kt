package uk.dnpk.sekund.sdk.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Converts between Compose [Color]s and `"#RRGGBB"` / `"#AARRGGBB"` strings, as used in config.json. */
object ColorHex {
    /** Parses `"#RRGGBB"` or `"#AARRGGBB"`. Returns null if [text] isn't in either format. */
    fun parse(text: String): Color? {
        if (!text.startsWith("#")) return null
        val hex = text.substring(1)
        if (hex.length != 6 && hex.length != 8) return null
        val number = hex.toLongOrNull(radix = 16) ?: return null

        // Without an alpha part the colour is fully opaque (alpha = FF).
        val argb = if (hex.length == 6) 0xFF000000L or number else number
        return Color(argb.toInt())
    }

    /** Formats as `"#RRGGBB"`, or `"#AARRGGBB"` if the colour is see-through. */
    fun format(color: Color): String {
        val argb = color.toArgb().toLong() and 0xFFFFFFFFL
        val alpha = argb ushr 24
        if (alpha == 0xFFL) {
            val rgb = argb and 0xFFFFFFL
            return "#" + rgb.toString(16).uppercase().padStart(6, '0')
        }
        return "#" + argb.toString(16).uppercase().padStart(8, '0')
    }
}
