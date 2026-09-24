package ru.nya.nyeios.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.nya.nyeios.ui.theme.NierDark

/**
 * Geometric square checkbox from NieR: Automata / YoRHa OS.
 * When [checked] is true, draws an exactly centered diagonal cross [✕] with Canvas,
 * eliminating font baseline and ascent/descent offsets.
 */
@Composable
fun NierCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
    color: Color = NierDark,
    boxColor: Color = Color.Transparent,
    size: Dp = 14.dp,
    borderWidth: Dp = 1.2.dp,
    strokeWidth: Dp = 1.5.dp
) {
    Canvas(
        modifier = modifier
            .size(size)
            .background(boxColor)
            .border(
                width = borderWidth,
                color = color,
                shape = RoundedCornerShape(0.dp)
            )
    ) {
        if (checked) {
            val strokePx = strokeWidth.toPx()
            val pad = 3.dp.toPx()
            val w = this.size.width
            val h = this.size.height

            // Line 1: top-left to bottom-right
            drawLine(
                color = color,
                start = Offset(pad, pad),
                end = Offset(w - pad, h - pad),
                strokeWidth = strokePx,
                cap = StrokeCap.Square
            )
            // Line 2: top-right to bottom-left
            drawLine(
                color = color,
                start = Offset(w - pad, pad),
                end = Offset(pad, h - pad),
                strokeWidth = strokePx,
                cap = StrokeCap.Square
            )
        }
    }
}
