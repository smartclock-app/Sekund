package uk.dnpk.sekund.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import uk.dnpk.sekund.ui.theme.Sekund

/**
 * The rounded card most sidebar content sits in. Text inside it automatically uses the card text colour.
 *
 * @param padded false for content that should reach the edges (e.g. a photo).
 */
@Composable
fun SekundCard(
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val theme = Sekund.theme
    var boxModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(theme.cardRadius))
        .background(theme.colors.card)
    if (padded) {
        boxModifier = boxModifier.padding(theme.cardPadding)
    }

    CompositionLocalProvider(LocalContentColor provides theme.colors.onCard) {
        Box(modifier = boxModifier, content = content)
    }
}
