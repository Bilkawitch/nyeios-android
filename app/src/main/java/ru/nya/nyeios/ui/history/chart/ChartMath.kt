package ru.nya.nyeios.ui.history.chart

import ru.nya.nyeios.data.history.ScoreSeriesKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Одна точка истории: значение балла на момент времени. */
data class ScoreSample(val atMs: Long, val value: Double)

/** OHLC-свеча за календарный день (в зоне, в которой считались свечи). */
data class OhlcCandle(
    val dayStartMs: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
) {
    /** Свеча «вверх» — закрытие не ниже открытия (в терминах графика — зелёная). */
    val isRising: Boolean get() = close >= open
}

/**
 * Линия тренда одной серии для обзорного графика.
 *
 * [samples] ожидаются по возрастанию [ScoreSample.atMs] (как отдаёт DAO);
 * Canvas-слой на всякий случай сортирует их повторно.
 */
data class TrendLine(
    val key: ScoreSeriesKey,
    val label: String,                 // subject
    val samples: List<ScoreSample>,    // по возрастанию atMs
    val rising: Boolean                // последняя >= первой
)

/**
 * Чистая математика графиков истории БРС: только `java.time` и коллекции,
 * без Android-API — покрывается обычными JVM-тестами.
 */
object ChartMath {

    /** Доля «воздуха» над и под фактическим диапазоном значений. */
    private const val AIR_RATIO = 0.08

    /** Запасной диапазон, если значений нет вовсе (ось не должна вырождаться). */
    private val EMPTY_BOUNDS = ValueBounds(0.0, 1.0)

    /** Потокобезопасный (immutable) формат даты оси; локаль зафиксирована. */
    private val AXIS_DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd.MM", Locale.ROOT)

    /**
     * Группирует сэмплы по календарным дням в [zone] и считает OHLC.
     *
     * `open` — первое по времени значение дня, `close` — последнее, `high`/`low` —
     * экстремумы дня. Сэмплы сортируются по [ScoreSample.atMs]; дни возвращаются
     * по возрастанию [OhlcCandle.dayStartMs].
     */
    fun candles(samples: List<ScoreSample>, zone: ZoneId): List<OhlcCandle> {
        if (samples.isEmpty()) return emptyList()
        return samples
            .sortedBy { it.atMs }
            .groupBy { Instant.ofEpochMilli(it.atMs).atZone(zone).toLocalDate() }
            .toSortedMap()
            .map { (day, daySamples) ->
                // daySamples сохраняет порядок обхода, т.е. уже по возрастанию atMs
                OhlcCandle(
                    dayStartMs = day.atStartOfDay(zone).toInstant().toEpochMilli(),
                    open = daySamples.first().value,
                    high = daySamples.maxOf { it.value },
                    low = daySamples.minOf { it.value },
                    close = daySamples.last().value
                )
            }
    }

    /**
     * Ось значений: min/max с «воздухом» 8% с каждой стороны.
     *
     * Защита от нулевого диапазона: при `min == max` диапазон раздвигается на ±10%
     * (или на ±1 от нуля). Пустой список даёт безопасный единичный диапазон `0..1`.
     */
    fun bounds(values: List<Double>): ValueBounds {
        if (values.isEmpty()) return EMPTY_BOUNDS

        var rawMin = Double.POSITIVE_INFINITY
        var rawMax = Double.NEGATIVE_INFINITY
        for (value in values) {
            if (!value.isFinite()) continue
            if (value < rawMin) rawMin = value
            if (value > rawMax) rawMax = value
        }
        if (!rawMin.isFinite() || !rawMax.isFinite()) return EMPTY_BOUNDS

        if (rawMin == rawMax) {
            val pad = if (rawMin == 0.0) 1.0 else abs(rawMin) * 0.1
            rawMin -= pad
            rawMax += pad
        }

        val air = (rawMax - rawMin) * AIR_RATIO
        return ValueBounds(rawMin - air, rawMax + air)
    }

    /** Ось значений: `min..max` без вырождения; [yRatio] — доля от низа (0) до верха (1). */
    data class ValueBounds(val min: Double, val max: Double) {
        /** 0 — низ оси, 1 — верх; значения вне диапазона обрезаются. */
        fun yRatio(value: Double): Float {
            val span = max - min
            if (!span.isFinite() || span <= 0.0 || !value.isFinite()) return 0.5f
            return ((value - min) / span).toFloat().coerceIn(0f, 1f)
        }
    }

    /**
     * Индексы свечей для подписей оси дат: не более [maxLabels] штук, равномерно,
     * включая первый (0) и последний (`candleCount - 1`).
     */
    fun dateLabelIndices(candleCount: Int, maxLabels: Int = 5): List<Int> {
        if (candleCount <= 0 || maxLabels <= 0) return emptyList()
        if (candleCount == 1 || maxLabels == 1) return listOf(0)
        if (candleCount <= maxLabels) return (0 until candleCount).toList()

        val lastIndex = candleCount - 1
        return (0 until maxLabels).map { i ->
            (i.toDouble() * lastIndex / (maxLabels - 1)).roundToInt()
        }.distinct()
    }

    /** Формат даты оси "dd.MM" в [zone]; локаль фиксирована (Locale.ROOT), не зависит от устройства. */
    fun axisDate(dayStartMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(dayStartMs).atZone(zone).format(AXIS_DATE_FORMATTER)
}
