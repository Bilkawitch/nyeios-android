package ru.nya.nyeios.ui.history.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Фиксированные цвета свечей истории БРС.
 *
 * По требованию пользователя рост всегда зелёный, падение всегда красное —
 * палитра НЕ зависит от активной темы (YoRHa / NyC) и не читает `Nier*`-цвета.
 * Оси, сетка, фон и подписи — параметры из темы, их передаёт UI-слой.
 */
object HistoryChartPalette {
    /** Зелёный: закрытие не ниже открытия. */
    val Rising = Color(0xFF22C55E)

    /** Красный: закрытие ниже открытия. */
    val Falling = Color(0xFFEF4444)

    /** Цвет фитиля растущей свечи (совпадает с телом). */
    val BullishWick = Rising

    /** Цвет фитиля падающей свечи (совпадает с телом). */
    val BearishWick = Falling
}

// Внутренние отступы: подписи осей не должны обрезаться краем Canvas.
private val LeftPadding = 8.dp
private val RightPadding = 8.dp
private val BottomPadding = 12.dp
private val TopPadding = 12.dp

/** Сколько подписей дат максимум рисуем на оси X. */
private const val MAX_DATE_LABELS = 5

private const val TREND_LINE_WIDTH_DP = 2f
private const val TREND_POINT_RADIUS_DP = 1.5f
private const val CANDLE_WICK_WIDTH_DP = 1.5f
private const val CANDLE_BODY_WIDTH_FRACTION = 0.65f
private const val CANDLE_BODY_MIN_WIDTH_DP = 3f
private const val SELECTION_STROKE_DP = 1.5f
private const val TREND_GRID_ALPHA = 0.25f
private const val CANDLE_GRID_ALPHA = 0.18f

/** Строка оси: число без лишних знаков, локаль фиксирована (точка как разделитель). */
private fun formatAxisValue(value: Double): String {
    if (!value.isFinite()) return "0"
    val rounded = value.roundToInt()
    return if (abs(value - rounded) < 0.05) {
        rounded.toString()
    } else {
        "%.1f".format(Locale.ROOT, value)
    }
}

/** Измерение однострочной подписи (без переносов) — один и тот же путь для gutter и отрисовки. */
private fun TextMeasurer.measureLabel(text: String, style: TextStyle): TextLayoutResult =
    measure(text = text, style = style, maxLines = 1, softWrap = false)

/** Ширина самой широкой подписи; 0 — если подписей нет. */
private fun TextMeasurer.maxLabelWidthPx(labels: List<String>, style: TextStyle): Int =
    labels.maxOfOrNull { measureLabel(it, style).size.width } ?: 0

/** Высота подписи (0 — если подписей нет). */
private fun TextMeasurer.labelHeightPx(labels: List<String>, style: TextStyle): Int =
    labels.maxOfOrNull { measureLabel(it, style).size.height } ?: 0

private fun DrawScope.drawValueLabels(
    measurer: TextMeasurer,
    labels: List<String>,
    style: TextStyle,
    x: Float,
    topY: Float,
    centerY: Float,
    bottomY: Float
) {
    if (labels.isEmpty()) return
    val height = labels.first().let { measurer.measureLabel(it, style).size.height.toFloat() }
    // labels: index 0 — верх, 1 — середина (если есть), last — низ
    labels.forEachIndexed { index, text ->
        val y = when {
            labels.size == 1 -> centerY - height / 2f
            index == 0 -> topY
            index == labels.lastIndex -> bottomY - height
            else -> centerY - height / 2f
        }
        drawText(measurer.measureLabel(text, style), topLeft = Offset(x, y))
    }
}

/**
 * Обзорный график: несколько серий-полилиний в общей системе координат.
 *
 * * ось Y — общий `ChartMath.bounds` по всем сэмплам всех линий;
 * * ось X — время: от минимального `atMs` до максимального по всем линиям;
 * * цвет линии задаёт [lineColorFor] (UI подставляет зелёный/красный из [HistoryChartPalette]);
 * * сетка — [gridColor] с alpha 0.25, линия 2dp, точки на сэмплах 3dp;
 * * подписи дат снизу (не более 5) и значений слева (min/max), не обрезаются.
 *
 * Пустые данные — ничего не рисуем (empty state показывает UI-слой).
 */
@Composable
fun TrendLinesCanvas(
    lines: List<TrendLine>,
    lineColorFor: (TrendLine) -> Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    labelStyle: TextStyle = TextStyle(color = Color.Unspecified)
) {
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val zone = remember { ZoneId.systemDefault() }

    // Все сэмплы всех линий в хронологическом порядке — общая шкала времени и значений.
    val allSamples = remember(lines) { lines.flatMap { it.samples }.sortedBy { it.atMs } }
    val values = remember(allSamples) { allSamples.map { it.value } }
    val bounds = remember(values) { ChartMath.bounds(values) }
    val rawLow = remember(values) { values.minOrNull() ?: 0.0 }
    val rawHigh = remember(values) { values.maxOrNull() ?: 1.0 }
    val valueLabels = remember(rawLow, rawHigh) {
        if (rawLow == rawHigh) listOf(formatAxisValue(rawLow))
        else listOf(formatAxisValue(rawHigh), formatAxisValue(rawLow))
    }

    val timeMin = remember(allSamples) { allSamples.firstOrNull()?.atMs ?: 0L }
    val timeMax = remember(allSamples) { allSamples.lastOrNull()?.atMs ?: 0L }
    val timeLabels = remember(allSamples, zone) {
        val distinctTimes = allSamples.map { it.atMs }.distinct()
        ChartMath.dateLabelIndices(distinctTimes.size, MAX_DATE_LABELS)
            .map { index -> distinctTimes[index] to ChartMath.axisDate(distinctTimes[index], zone) }
    }

    val baseLabelStyle = MaterialTheme.typography.labelMedium
    val resolvedLabelStyle = remember(labelStyle, gridColor, baseLabelStyle) {
        if (labelStyle.color == Color.Unspecified) baseLabelStyle.copy(color = gridColor) else labelStyle
    }
    val maxValueLabelWidthPx = remember(valueLabels, resolvedLabelStyle, measurer) {
        measurer.maxLabelWidthPx(valueLabels, resolvedLabelStyle)
    }
    val dateLabelHeightPx = remember(timeLabels, resolvedLabelStyle, measurer) {
        measurer.labelHeightPx(timeLabels.map { it.second }, resolvedLabelStyle)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (lines.isEmpty() || allSamples.isEmpty()) return@Canvas

        val plotLeft = maxValueLabelWidthPx + LeftPadding.toPx()
        val plotRight = size.width - RightPadding.toPx()
        val plotTop = TopPadding.toPx()
        val plotBottom = size.height - dateLabelHeightPx - BottomPadding.toPx()
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        fun yFor(value: Double): Float = plotBottom - bounds.yRatio(value) * plotHeight

        val timeSpan = (timeMax - timeMin).toDouble()
        fun xFor(timeMs: Long): Float = if (timeSpan <= 0.0) {
            plotLeft + plotWidth / 2f
        } else {
            plotLeft + ((timeMs - timeMin) / timeSpan).toFloat() * plotWidth
        }

        // Лёгкая сетка: три горизонтали + вертикали под подписями дат.
        val grid = gridColor.copy(alpha = TREND_GRID_ALPHA)
        val gridStroke = 1.dp.toPx()
        for (i in 0..2) {
            val y = plotTop + plotHeight * i / 2f
            drawLine(grid, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = gridStroke)
        }
        for ((timeMs, _) in timeLabels) {
            val x = xFor(timeMs)
            drawLine(grid, Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = gridStroke)
        }

        // Подписи значений слева (max сверху, min снизу).
        val valueLabelX = (LeftPadding / 2).toPx()
        drawValueLabels(
            measurer = measurer,
            labels = valueLabels,
            style = resolvedLabelStyle,
            x = valueLabelX,
            topY = plotTop,
            centerY = plotTop + plotHeight / 2f,
            bottomY = plotBottom
        )

        // Подписи дат снизу, по центру сэмплов, с зажимом внутри Canvas.
        val dateLabelTop = plotBottom + BottomPadding.toPx() / 2f
        for ((timeMs, text) in timeLabels) {
            val layout = measurer.measureLabel(text, resolvedLabelStyle)
            val x = (xFor(timeMs) - layout.size.width / 2f)
                .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            drawText(layout, topLeft = Offset(x, dateLabelTop))
        }

        // Сами линии: полилиния 2dp + точки на сэмплах.
        val lineStroke = Stroke(
            width = TREND_LINE_WIDTH_DP.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        for (line in lines) {
            if (line.samples.isEmpty()) continue
            val color = lineColorFor(line)
            val samples = line.samples.sortedBy { it.atMs }
            val path = Path()
            samples.forEachIndexed { index, sample ->
                val point = Offset(xFor(sample.atMs), yFor(sample.value))
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, color = color, style = lineStroke)
            val pointRadius = TREND_POINT_RADIUS_DP.dp.toPx()
            for (sample in samples) {
                drawCircle(
                    color = color,
                    radius = pointRadius,
                    center = Offset(xFor(sample.atMs), yFor(sample.value))
                )
            }
        }
    }
}

/**
 * Детальный график: японские свечи OHLC по дням.
 *
 * * ось X — равномерные индексы дней с данными (без «дыр» выходных);
 * * ось Y — `ChartMath.bounds` по low/high с подписями min/mid/max слева;
 * * тело свечи 65% шага (минимум 3dp), фитиль 1.5dp, цвет — [HistoryChartPalette];
 * * сетка — [axisColor] с alpha, подписи дат снизу (не более 5);
 * * тап выбирает ближайшую свечу через [onSelectIndex] (индекс всегда в пределах списка);
 * * выбранная свеча обведена 1.5dp рамкой [axisColor].
 *
 * Пустой список — ничего не рисуем; подписи не обрезаются внутренними отступами.
 */
@Composable
fun CandlestickCanvas(
    candles: List<OhlcCandle>,
    axisColor: Color,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
    selectedIndex: Int? = null,
    onSelectIndex: (Int?) -> Unit = {}
) {
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val zone = remember { ZoneId.systemDefault() }

    // Если тема не задала цвет подписей — берём цвет оси, чтобы не рисовать чёрным по тёмному.
    val effectiveLabelStyle = remember(labelStyle, axisColor) {
        if (labelStyle.color == Color.Unspecified) labelStyle.copy(color = axisColor) else labelStyle
    }

    val rawLow = remember(candles) { candles.minOfOrNull { it.low } ?: 0.0 }
    val rawHigh = remember(candles) { candles.maxOfOrNull { it.high } ?: 1.0 }
    val bounds = remember(rawLow, rawHigh) { ChartMath.bounds(listOf(rawLow, rawHigh)) }
    val valueLabels = remember(rawLow, rawHigh) {
        if (rawLow == rawHigh) {
            listOf(formatAxisValue(rawLow))
        } else {
            val mid = (rawLow + rawHigh) / 2.0
            listOf(formatAxisValue(rawHigh), formatAxisValue(mid), formatAxisValue(rawLow))
        }
    }

    val dateEntries = remember(candles, zone) {
        val indices = ChartMath.dateLabelIndices(candles.size, MAX_DATE_LABELS)
        indices.map { index -> index to ChartMath.axisDate(candles[index].dayStartMs, zone) }
    }

    val maxValueLabelWidthPx = remember(valueLabels, effectiveLabelStyle, measurer) {
        measurer.maxLabelWidthPx(valueLabels, effectiveLabelStyle)
    }
    val dateLabelHeightPx = remember(dateEntries, effectiveLabelStyle, measurer) {
        measurer.labelHeightPx(dateEntries.map { it.second }, effectiveLabelStyle)
    }

    val safeSelectedIndex = if (candles.isEmpty()) {
        null
    } else {
        selectedIndex?.coerceIn(0, candles.lastIndex)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(candles, maxValueLabelWidthPx) {
                if (candles.isEmpty()) return@pointerInput
                detectTapGestures { tap ->
                    val plotLeft = maxValueLabelWidthPx + LeftPadding.toPx()
                    val plotRight = size.width - RightPadding.toPx()
                    if (plotRight <= plotLeft) return@detectTapGestures
                    // Тап левее оси значений (подписи/вне графика) не меняет выбор.
                    if (tap.x < plotLeft || tap.x > plotRight) return@detectTapGestures
                    val step = (plotRight - plotLeft) / candles.size
                    val index = ((tap.x - plotLeft) / step).toInt().coerceIn(0, candles.lastIndex)
                    onSelectIndex(index)
                }
            }
    ) {
        if (candles.isEmpty()) return@Canvas

        val plotLeft = maxValueLabelWidthPx + LeftPadding.toPx()
        val plotRight = size.width - RightPadding.toPx()
        val plotTop = TopPadding.toPx()
        val plotBottom = size.height - dateLabelHeightPx - BottomPadding.toPx()
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        fun yFor(value: Double): Float = plotBottom - bounds.yRatio(value) * plotHeight
        val step = plotWidth / candles.size
        fun centerXFor(index: Int): Float = plotLeft + step * (index + 0.5f)

        // Сетка (axisColor, низкая альфа) + вертикали под подписями дат.
        val grid = axisColor.copy(alpha = CANDLE_GRID_ALPHA)
        val gridStroke = 1.dp.toPx()
        for (i in 0..2) {
            val y = plotTop + plotHeight * i / 2f
            drawLine(grid, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = gridStroke)
        }
        for ((index, _) in dateEntries) {
            val x = centerXFor(index)
            drawLine(grid, Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = gridStroke)
        }

        // Подписи оси значений слева: max / mid / min.
        drawValueLabels(
            measurer = measurer,
            labels = valueLabels,
            style = effectiveLabelStyle,
            x = (LeftPadding / 2).toPx(),
            topY = plotTop,
            centerY = plotTop + plotHeight / 2f,
            bottomY = plotBottom
        )

        // Подписи дат снизу — по центру свечи, с зажимом внутри Canvas.
        val dateLabelTop = plotBottom + BottomPadding.toPx() / 2f
        for ((index, text) in dateEntries) {
            val layout = measurer.measureLabel(text, effectiveLabelStyle)
            val x = (centerXFor(index) - layout.size.width / 2f)
                .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            drawText(layout, topLeft = Offset(x, dateLabelTop))
        }

        // Свечи: фитиль 1.5dp, тело 65% шага (мин. 3dp), всегда зелёный/красный.
        val wickStroke = CANDLE_WICK_WIDTH_DP.dp.toPx()
        val minBodyWidth = CANDLE_BODY_MIN_WIDTH_DP.dp.toPx()
        for ((index, candle) in candles.withIndex()) {
            val color = if (candle.isRising) HistoryChartPalette.Rising else HistoryChartPalette.Falling
            val centerX = centerXFor(index)
            val highY = yFor(candle.high)
            val lowY = yFor(candle.low)
            drawLine(color, Offset(centerX, highY), Offset(centerX, lowY), strokeWidth = wickStroke)

            val bodyWidth = (step * CANDLE_BODY_WIDTH_FRACTION).coerceAtLeast(minBodyWidth)
            val openY = yFor(candle.open)
            val closeY = yFor(candle.close)
            val bodyTop = minOf(openY, closeY)
            val bodyHeight = abs(closeY - openY).coerceAtLeast(1.dp.toPx())
            drawRect(
                color = color,
                topLeft = Offset(centerX - bodyWidth / 2f, bodyTop),
                size = Size(bodyWidth, bodyHeight)
            )
        }

        // Выбранная свеча — обводка колонки 1.5dp.
        if (safeSelectedIndex != null) {
            val centerX = centerXFor(safeSelectedIndex)
            val halfSlot = (step / 2f).coerceAtLeast(1.dp.toPx())
            val left = (centerX - halfSlot).coerceAtLeast(plotLeft)
            val right = (centerX + halfSlot).coerceAtMost(plotRight)
            val selectionTopLeft = Offset(left, plotTop)
            val selectionSize = Size((right - left).coerceAtLeast(1f), plotHeight)
            drawRect(
                color = axisColor.copy(alpha = 0.10f),
                topLeft = selectionTopLeft,
                size = selectionSize
            )
            drawRect(
                color = axisColor.copy(alpha = 0.65f),
                topLeft = selectionTopLeft,
                size = selectionSize,
                style = Stroke(width = SELECTION_STROKE_DP.dp.toPx())
            )
        }

        // Отдельная подпись выбранной даты над графиком не нужна: её показывает UI-слой.
    }
}
