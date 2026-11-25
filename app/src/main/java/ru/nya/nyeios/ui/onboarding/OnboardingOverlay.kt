package ru.nya.nyeios.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.R
import ru.nya.nyeios.ui.theme.NierBorder
import ru.nya.nyeios.ui.theme.NierBorderLight
import ru.nya.nyeios.ui.theme.NierDark
import ru.nya.nyeios.ui.theme.NierDarkSecondary
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NierGreen
import ru.nya.nyeios.ui.theme.NierPanel
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily
import ru.nya.nyeios.ui.theme.appButtonShape
import ru.nya.nyeios.ui.theme.appCardShape
import ru.nya.nyeios.ui.theme.isNycModern
import ru.nya.nyeios.ui.theme.rememberUiInfiniteTransition
import ru.nya.nyeios.ui.theme.selectionAccent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun OnboardingOverlay(
    isActive: Boolean,
    stepIndex: Int,
    registry: OnboardingTargetRegistry,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isActive) return
    val step = OnboardingSteps.getOrNull(stepIndex) ?: return

    BackHandler(enabled = true) {
        if (stepIndex > 0) onPrev() else onSkip()
    }

    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val topSafePx = insets.getTop(density).toFloat()
    val bottomSafePx = insets.getBottom(density).toFloat()

    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    val rawWindowBounds = registry.boundsFor(step.targetId)
        ?: registry.boundsFor(step.fallbackTargetId)

    val targetRect: Rect? = remember(rawWindowBounds, overlayOffset) {
        rawWindowBounds?.translate(-overlayOffset.x, -overlayOffset.y)?.takeIf {
            it.width > 0f && it.height > 0f
        }
    }

    val padPx = with(density) { 6.dp.toPx() }
    val gapPx = with(density) { 38.dp.toPx() }
    val marginPx = with(density) { 12.dp.toPx() }
    val cutoutCornerPx = with(density) { if (isNycModern) 12.dp.toPx() else 0.dp.toPx() }

    val cutoutRect: Rect? = remember(targetRect, overlaySize, padPx) {
        if (targetRect == null || overlaySize.width == 0 || overlaySize.height == 0) null
        else Rect(
            left = (targetRect.left - padPx).coerceAtLeast(2f),
            top = (targetRect.top - padPx).coerceAtLeast(2f),
            right = (targetRect.right + padPx).coerceAtMost(overlaySize.width - 2f),
            bottom = (targetRect.bottom + padPx).coerceAtMost(overlaySize.height - 2f)
        )
    }

    val cardTopPx: Float = remember(cutoutRect, overlaySize, cardSize, topSafePx, bottomSafePx) {
        val safeTop = topSafePx + marginPx
        val safeBottom = (overlaySize.height - bottomSafePx - marginPx).coerceAtLeast(safeTop + 100f)
        val ch = cardSize.height.toFloat()

        if (cutoutRect == null || ch <= 0f) {
            (safeTop + safeBottom - ch) / 2f
        } else {
            val spaceAbove = cutoutRect.top - safeTop
            val spaceBelow = safeBottom - cutoutRect.bottom
            if (spaceBelow >= ch + gapPx) {
                cutoutRect.bottom + gapPx
            } else if (spaceAbove >= ch + gapPx) {
                cutoutRect.top - gapPx - ch
            } else if (spaceBelow >= spaceAbove) {
                (cutoutRect.bottom + gapPx).coerceAtMost((safeBottom - ch).coerceAtLeast(safeTop))
            } else {
                (cutoutRect.top - gapPx - ch).coerceAtLeast(safeTop)
            }
        }
    }

    val cardLeftPx: Float = remember(overlaySize, cardSize) {
        ((overlaySize.width - cardSize.width) / 2f).coerceAtLeast(0f)
    }

    val accent = if (isNycModern) NycCyan else selectionAccent()
    val pulseTransition = rememberUiInfiniteTransition("onboarding_pulse")
    val pulseAlpha = pulseTransition?.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spotlight_alpha"
    )?.value ?: 0.9f

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                overlayOffset = coords.positionInWindow()
                overlaySize = coords.size
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        // Scrim with cutout + animated highlight border + curved arrow
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(color = Color.Black.copy(alpha = 0.74f))

            if (cutoutRect != null) {
                val corner = CornerRadius(cutoutCornerPx, cutoutCornerPx)
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(cutoutRect.left, cutoutRect.top),
                    size = Size(cutoutRect.width, cutoutRect.height),
                    cornerRadius = corner,
                    blendMode = BlendMode.Clear
                )

                drawRoundRect(
                    color = accent.copy(alpha = pulseAlpha),
                    topLeft = Offset(cutoutRect.left, cutoutRect.top),
                    size = Size(cutoutRect.width, cutoutRect.height),
                    cornerRadius = corner,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Draw arrow connecting tooltip card to cutout rect when separated
                if (cardSize.width > 0 && cardSize.height > 0) {
                    val cardRect = Rect(
                        left = cardLeftPx,
                        top = cardTopPx,
                        right = cardLeftPx + cardSize.width,
                        bottom = cardTopPx + cardSize.height
                    )
                    val arrowOffsetPx = 6.dp.toPx()
                    val cardEdgePad = 24.dp.toPx()
                    val targetX = cutoutRect.center.x

                    val (start, end) = when {
                        cardRect.top >= cutoutRect.bottom + arrowOffsetPx * 2f -> {
                            val sx = targetX.coerceIn(
                                cardRect.left + cardEdgePad,
                                (cardRect.right - cardEdgePad).coerceAtLeast(cardRect.left + cardEdgePad)
                            )
                            Offset(sx, cardRect.top) to Offset(targetX, cutoutRect.bottom + arrowOffsetPx)
                        }
                        cardRect.bottom <= cutoutRect.top - arrowOffsetPx * 2f -> {
                            val sx = targetX.coerceIn(
                                cardRect.left + cardEdgePad,
                                (cardRect.right - cardEdgePad).coerceAtLeast(cardRect.left + cardEdgePad)
                            )
                            Offset(sx, cardRect.bottom) to Offset(targetX, cutoutRect.top - arrowOffsetPx)
                        }
                        else -> null to null
                    }

                    if (start != null && end != null) {
                        val midY = (start.y + end.y) / 2f
                        val curveOffset = if (kotlin.math.abs(end.x - start.x) < 28.dp.toPx()) {
                            val nudge = 28.dp.toPx()
                            if (start.x > size.width / 2f) -nudge else nudge
                        } else {
                            0f
                        }
                        val control = Offset(
                            x = (start.x + end.x) / 2f + curveOffset,
                            y = midY
                        )

                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            quadraticBezierTo(control.x, control.y, end.x, end.y)
                        }

                        drawPath(
                            path = path,
                            color = accent,
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Arrowhead aligned with tangent at t=1 (from control -> end)
                        val angle = atan2((end.y - control.y).toDouble(), (end.x - control.x).toDouble())
                        val headLen = 11.dp.toPx()
                        val spread = Math.toRadians(28.0)
                        val leftWing = Offset(
                            x = end.x - (headLen * cos(angle - spread)).toFloat(),
                            y = end.y - (headLen * sin(angle - spread)).toFloat()
                        )
                        val rightWing = Offset(
                            x = end.x - (headLen * cos(angle + spread)).toFloat(),
                            y = end.y - (headLen * sin(angle + spread)).toFloat()
                        )
                        val headPath = Path().apply {
                            moveTo(leftWing.x, leftWing.y)
                            lineTo(end.x, end.y)
                            lineTo(rightWing.x, rightWing.y)
                        }
                        drawPath(
                            path = headPath,
                            color = accent,
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }

        // Tooltip Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, cardTopPx.roundToInt()) }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            OnboardingTooltipCard(
                step = step,
                totalSteps = OnboardingSteps.size,
                canGoBack = stepIndex > 0,
                onPrev = onPrev,
                onNext = onNext,
                onSkip = onSkip,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .onSizeChanged { cardSize = it }
            )
        }
    }
}

@Composable
private fun OnboardingTooltipCard(
    step: OnboardingStepSpec,
    totalSteps: Int,
    canGoBack: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isModern = isNycModern
    val cardShape = appCardShape()
    val buttonShape = appButtonShape()
    val titleFamily = if (isModern) NycSansFamily else RajdhaniFamily
    val monoFamily = if (isModern) NycMonoFamily else ShareTechMonoFamily
    val bodyFamily = if (isModern) NycSansFamily else ShareTechMonoFamily

    val cardBg = if (isModern) Color(0xFF131D2B) else NierPanel
    val borderColor = if (isModern) NycCyan.copy(alpha = 0.35f) else NierBorderLight
    val accent = if (isModern) NycCyan else selectionAccent()

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(cardBg, cardShape)
            .border(1.dp, borderColor, cardShape)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .clip(buttonShape)
                    .background(accent.copy(alpha = 0.14f), buttonShape)
                    .border(1.dp, accent.copy(alpha = 0.45f), buttonShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_step_fmt, step.stepNumber, totalSteps),
                    fontFamily = monoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp,
                    color = accent
                )
            }

            Text(
                text = stringResource(R.string.onboarding_skip),
                fontFamily = titleFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = NierDim,
                modifier = Modifier
                    .clip(buttonShape)
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        if (step.isFinal) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(buttonShape)
                        .background(NierGreen.copy(alpha = 0.15f), buttonShape)
                        .border(1.dp, NierGreen.copy(alpha = 0.45f), buttonShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = NierGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(step.textRes),
                    fontFamily = titleFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = NierDark,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = stringResource(step.textRes),
                fontFamily = bodyFamily,
                fontWeight = if (isModern) FontWeight.Medium else FontWeight.Normal,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = NierDark
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canGoBack) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(buttonShape)
                        .border(1.dp, NierBorder, buttonShape)
                        .clickable(onClick = onPrev)
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_back),
                        fontFamily = titleFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = NierDarkSecondary
                    )
                }
            }

            val primaryColor = if (step.isFinal) NierGreen else accent
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(buttonShape)
                    .background(primaryColor.copy(alpha = 0.16f), buttonShape)
                    .border(1.dp, primaryColor, buttonShape)
                    .clickable(onClick = onNext)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(if (step.isFinal) R.string.onboarding_done else R.string.onboarding_next),
                    fontFamily = titleFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = primaryColor
                )
            }
        }
    }
}
