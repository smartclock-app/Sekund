package uk.dnpk.sekund.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sekund's design tokens: the values every screen and plugin should use instead of hard-coding
 * colours and sizes, so the whole app can be re-themed in one place.
 *
 * (These replace the old app's CSS variables. Read them in a composable with `Sekund.theme`.)
 */
data class SekundThemeTokens(
    val colors: SekundColors = SekundColors(),
    val cardRadius: Dp = 16.dp,
    val cardPadding: Dp = 16.dp,
    /** Gap between cards in the sidebar. */
    val spacing: Dp = 12.dp,
    /** Multiplies the size of the clock face's text. */
    val clockScale: Float = 1f,
)

data class SekundColors(
    val background: Color = Color(0xFF000000),
    val onBackground: Color = Color(0xFFF8F8F8),
    val card: Color = Color(0xFFECECEC),
    val onCard: Color = Color(0xFF151515),
    val onCardMuted: Color = Color(0xFF5C5C5C),
    val accent: Color = Color(0xFF3B82F6),
    val warning: Color = Color(0xFFE0A100),
    val error: Color = Color(0xFFCD4545),
)

private val LocalSekundTheme = staticCompositionLocalOf { SekundThemeTokens() }

/** Read the current theme tokens: `Sekund.theme.colors.card`. */
object Sekund {
    val theme: SekundThemeTokens
        @Composable
        get() = LocalSekundTheme.current
}

/** Makes [tokens] available to everything inside [content] (like a React context provider). */
@Composable
fun SekundTheme(
    tokens: SekundThemeTokens = SekundThemeTokens(),
    content: @Composable () -> Unit,
) {
    val materialColors = darkColorScheme(
        primary = tokens.colors.accent,
        background = tokens.colors.background,
        onBackground = tokens.colors.onBackground,
        surface = tokens.colors.card,
        onSurface = tokens.colors.onCard,
        error = tokens.colors.error,
    )
    CompositionLocalProvider(LocalSekundTheme provides tokens) {
        MaterialTheme(colorScheme = materialColors, content = content)
    }
}
