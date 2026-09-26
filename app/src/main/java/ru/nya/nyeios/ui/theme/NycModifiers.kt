package ru.nya.nyeios.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// NyC-modern surface modifiers approximating the Night Skeuomorph mockup.
// Single-pass approximations of the dual-light CSS neumorphism (sh-out / sh-in):
// real-time dual blur is not available in Compose, so depth is conveyed through
// layered gradients + ambient shadows + hairline strokes. Legacy themes never
// call these helpers.

private val nycEdge = Color(150, 185, 235, alpha = 19) // --edge rgba(150,185,235,.075)
private val nycEdgeStrong = Color(150, 185, 235, alpha = 33) // --edge-strong .13
private val nycTopHi = Color(205, 230, 255, alpha = 13) // inset 0 1px 0 rgba(205,230,255,.05)

/** Raised card: gradient ink-hi -> surf-lo, ambient shadow, edge stroke, top highlight. */
fun Modifier.nycCard(
    corner: Dp = NycShapes.card,
    edge: Color = nycEdge
): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.62f), spotColor = Color.Black.copy(alpha = 0.62f))
        .background(
            Brush.linearGradient(
                colors = listOf(Color(0xFF1E2A3B), Color(0xFF121B28)),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            ),
            shape
        )
        .border(1.dp, edge, shape)
}

/** Raised small control: gradient surf-hi -> surf-lo (logo, icon buttons, arrows). */
fun Modifier.nycRaised(
    corner: Dp = NycShapes.button,
    edge: Color = nycEdge
): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.55f), spotColor = Color.Black.copy(alpha = 0.55f))
        .background(
            Brush.linearGradient(
                colors = listOf(Color(0xFF1A2534), Color(0xFF121B28)),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            ),
            shape
        )
        .border(1.dp, edge, shape)
}

/** Inset well: dark gradient ink-lo -> #0E1622 with sunken top edge. */
fun Modifier.nycWell(
    corner: Dp = NycShapes.well,
    bg: Color = Color(0xFF0A0F16)
): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .background(
            Brush.linearGradient(
                colors = listOf(bg, Color(0xFF0E1622)),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            ),
            shape
        )
        .border(1.dp, Color.Black.copy(alpha = 0.25f), shape)
}

/** Cyan glow ring for active/selected accents. */
fun Modifier.nycCyanRing(corner: Dp, alpha: Float = 0.45f): Modifier =
    this.border(1.dp, NycCyan.copy(alpha = alpha), RoundedCornerShape(corner))

@Composable
fun NycLed(
    color: Color,
    diameter: Dp = 7.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(diameter)
            .shadow(4.dp, CircleShape, ambientColor = color.copy(alpha = 0.8f), spotColor = color.copy(alpha = 0.8f))
            .background(color, CircleShape)
    )
}

/** Type-badge background/border pair from the mockup (10% tint + 1dp outline). */
fun nycBadgeBg(color: Color): Color = color.copy(alpha = 0.10f)

fun nycBadgeShape(): Shape = RoundedCornerShape(7.dp)
