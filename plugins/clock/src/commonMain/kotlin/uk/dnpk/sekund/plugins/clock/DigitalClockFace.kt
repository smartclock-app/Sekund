package uk.dnpk.sekund.plugins.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import uk.dnpk.sekund.sdk.clock.ClockFace
import uk.dnpk.sekund.sdk.context.TimeService
import uk.dnpk.sekund.sdk.time.TimeFormat
import uk.dnpk.sekund.ui.theme.Sekund

/**
 * Big digital time, with seconds and AM/PM beside it and the long date underneath:
 *
 * ```
 *  09:41  41
 *         PM
 *  Monday 3rd March 2025
 * ```
 *
 * Text sizes are worked out from the space available, so it fills the main square on any screen.
 * The theme's `clockScale` makes it bigger or smaller.
 */
class DigitalClockFace(private val time: TimeService) : ClockFace {

    @Composable
    override fun Content() {
        val preferences by time.preferences.collectAsState()
        // Only redraw every minute if seconds aren't shown.
        val nowFlow = if (preferences.showSeconds) time.everySecond else time.everyMinute
        val now by nowFlow.collectAsState()

        val clock = TimeFormat.clock(now, preferences)
        val date = TimeFormat.longDate(now.date)
        val colors = Sekund.theme.colors
        val scale = Sekund.theme.clockScale

        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Size everything relative to the width of the area we've been given.
            val timeSize = textSize(maxWidth * 0.26f * scale)
            val sideSize = textSize(maxWidth * 0.09f * scale)
            val dateSize = textSize(maxWidth * 0.055f * scale)
            val gap = maxWidth * 0.02f

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = clock.hoursAndMinutes,
                        fontSize = timeSize,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground,
                    )
                    Column(
                        modifier = Modifier.padding(start = gap),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val seconds = clock.seconds
                        if (seconds != null) {
                            Text(seconds, fontSize = sideSize, fontFamily = FontFamily.Monospace, color = colors.onBackground)
                        }
                        val amPm = clock.amPm
                        if (amPm != null) {
                            Text(amPm, fontSize = sideSize, fontFamily = FontFamily.Monospace, color = colors.onBackground)
                        }
                    }
                }

                val dateText = buildAnnotatedString {
                    append("${date.weekday} ${date.day}")
                    // The ordinal ("rd") is smaller and raised, like the old app's <sup>.
                    withStyle(SpanStyle(fontSize = 0.6.em, baselineShift = BaselineShift(0.9f))) {
                        append(date.ordinal)
                    }
                    append(" ${date.month} ${date.year}")
                }
                Text(text = dateText, fontSize = dateSize, color = colors.onBackground)
            }
        }
    }

    /** Converts a size in Dp (which scales with the screen) into a font size. */
    @Composable
    private fun textSize(size: Dp): TextUnit {
        val density = LocalDensity.current
        return with(density) { size.toSp() }
    }
}
