package ru.nya.nyeios.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.nya.nyeios.ui.theme.NierBg
import ru.nya.nyeios.ui.theme.NierDark
import ru.nya.nyeios.ui.theme.NierDarkSecondary

/**
 * Authentic NieR: Automata background with subtle geometric curve lines and YoRHa watermark.
 */
@Composable
fun NierBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Base background
            drawRect(color = NierBg)

            // Subtle curved geometric arcs characteristic of YoRHa UI
            val strokeWidth = 1f
            val arcColor = NierDark.copy(alpha = 0.12f)

            // Top-left to right curve
            val path1 = Path().apply {
                moveTo(-size.width * 0.1f, size.height * 0.12f)
                quadraticBezierTo(
                    size.width * 0.5f, size.height * 0.45f,
                    size.width * 1.1f, size.height * 0.25f
                )
            }
            drawPath(path1, color = arcColor, style = Stroke(width = strokeWidth))

            // Bottom-right to left curve
            val path2 = Path().apply {
                moveTo(size.width * 1.05f, size.height * 0.58f)
                quadraticBezierTo(
                    size.width * 0.45f, size.height * 0.35f,
                    -size.width * 0.05f, size.height * 0.72f
                )
            }
            drawPath(path2, color = arcColor, style = Stroke(width = strokeWidth))

            // Fine diagonal slash
            drawLine(
                color = arcColor.copy(alpha = 0.08f),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
                strokeWidth = 0.8f
            )
        }

        content()
    }
}

/**
 * Military dot rows that run horizontally across the top and bottom of NieR screens.
 */
@Composable
fun NierDotRow(
    modifier: Modifier = Modifier,
    dotColor: Color = NierDarkSecondary.copy(alpha = 0.65f),
    dotSize: Dp = 2.5.dp,
    spacing: Dp = 5.dp
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        val radius = dotSize.toPx() / 2f
        val step = spacing.toPx() + dotSize.toPx()
        val count = (size.width / step).toInt()
        val startX = (size.width - (count * step)) / 2f

        for (i in 0..count) {
            drawCircle(
                color = dotColor,
                radius = radius,
                center = Offset(startX + i * step, size.height / 2f)
            )
        }
    }
}
