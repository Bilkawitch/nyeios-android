package ru.nya.nyeios.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.nya.nyeios.ui.theme.NierDark
import ru.nya.nyeios.ui.theme.NierGreen

/**
 * Segmented level meter inspired by NieR: Automata UI and the YoRHa Retro mockup.
 * Displays a row of discrete ticks/blocks filling up according to [progress] (0f..1f).
 */
@Composable
fun SegmentedMeter(
    progress: Float,
    modifier: Modifier = Modifier,
    activeColor: Color = NierGreen,
    inactiveColor: Color = NierDark.copy(alpha = 0.15f),
    segments: Int = 26,
    height: Dp = 6.dp,
    spacing: Dp = 2.dp
) {
    val clamped = progress.coerceIn(0f, 1f)
    val activeCount = kotlin.math.round(clamped * segments).toInt().coerceIn(0, segments)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        for (i in 0 until segments) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (i < activeCount) activeColor else inactiveColor)
            )
        }
    }
}
