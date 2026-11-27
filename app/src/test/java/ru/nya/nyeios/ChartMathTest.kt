package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.ui.history.chart.ChartMath
import ru.nya.nyeios.ui.history.chart.OhlcCandle
import ru.nya.nyeios.ui.history.chart.ScoreSample
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * JVM-проверки чистой математики графиков истории БРС ([ChartMath]).
 *
 * Покрывают: группировку сэмплов по календарным дням и OHLC, ось значений
 * (`bounds`) с 8% «воздухом» и защитой от нулевого диапазона, равномерные
 * индексы подписей дат и формат `dd.MM`, не зависящий от текущей локали.
 */
class ChartMathTest {

    private val moscow: ZoneId = ZoneId.of("Europe/Moscow")
    private val utc: ZoneId = ZoneId.of("UTC")

    /** Момент времени из локальной ISO-строки в заданной зоне. */
    private fun at(localIso: String, zone: ZoneId): Long =
        LocalDateTime.parse(localIso).atZone(zone).toInstant().toEpochMilli()

    private fun startOfDay(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun assertClose(expected: Double, actual: Double, epsilon: Double = 1e-6) {
        assertEquals("expected $expected but was $actual", expected, actual, epsilon)
    }

    // --- candles: группировка по дням ---------------------------------------

    @Test
    fun `candles groups samples by calendar day and computes ohlc`() {
        val samples = listOf(
            // день 15.03 — намеренно не отсортированы по времени
            ScoreSample(at("2024-03-15T18:00", moscow), 4.0),
            ScoreSample(at("2024-03-15T09:00", moscow), 3.0),
            ScoreSample(at("2024-03-15T12:00", moscow), 5.0),
            // день 16.03
            ScoreSample(at("2024-03-16T11:00", moscow), 2.0),
            ScoreSample(at("2024-03-16T10:00", moscow), 6.0)
        )

        val candles = ChartMath.candles(samples, moscow)

        assertEquals(2, candles.size)

        val first = candles[0]
        assertEquals(startOfDay(LocalDate.of(2024, 3, 15), moscow), first.dayStartMs)
        assertClose(3.0, first.open)   // первое значение дня по времени
        assertClose(5.0, first.high)
        assertClose(3.0, first.low)
        assertClose(4.0, first.close)  // последнее значение дня по времени
        assertTrue(first.isRising)

        val second = candles[1]
        assertEquals(startOfDay(LocalDate.of(2024, 3, 16), moscow), second.dayStartMs)
        assertClose(6.0, second.open)
        assertClose(6.0, second.high)
        assertClose(2.0, second.low)
        assertClose(2.0, second.close)
        assertFalse(second.isRising)
    }

    @Test
    fun `candles sorted by day even when days arrive out of order`() {
        val samples = listOf(
            ScoreSample(at("2024-05-20T12:00", moscow), 7.0),
            ScoreSample(at("2024-05-18T12:00", moscow), 2.0),
            ScoreSample(at("2024-05-19T12:00", moscow), 5.0)
        )

        val candles = ChartMath.candles(samples, moscow)

        assertEquals(3, candles.size)
        assertEquals(
            listOf(
                startOfDay(LocalDate.of(2024, 5, 18), moscow),
                startOfDay(LocalDate.of(2024, 5, 19), moscow),
                startOfDay(LocalDate.of(2024, 5, 20), moscow)
            ),
            candles.map { it.dayStartMs }
        )
        assertEquals(listOf(2.0, 5.0, 7.0), candles.map { it.close })
    }

    @Test
    fun `candles single sample produces degenerate candle with equal prices`() {
        val candles = ChartMath.candles(
            listOf(ScoreSample(at("2024-03-15T10:00", moscow), 8.5)),
            moscow
        )

        assertEquals(1, candles.size)
        val candle = candles.single()
        assertClose(8.5, candle.open)
        assertClose(8.5, candle.high)
        assertClose(8.5, candle.low)
        assertClose(8.5, candle.close)
        assertTrue(candle.isRising) // close >= open при равенстве
    }

    @Test
    fun `candles empty input returns empty list`() {
        assertTrue(ChartMath.candles(emptyList(), moscow).isEmpty())
    }

    @Test
    fun `candles day boundary depends on zone`() {
        // 23:30 UTC 14.03 = 02:30 МСК 15.03
        val instantMs = at("2024-03-14T23:30", utc)
        val sample = listOf(ScoreSample(instantMs, 4.0))

        val moscowCandles = ChartMath.candles(sample, moscow)
        val utcCandles = ChartMath.candles(sample, utc)

        assertEquals(startOfDay(LocalDate.of(2024, 3, 15), moscow), moscowCandles.single().dayStartMs)
        assertEquals(startOfDay(LocalDate.of(2024, 3, 14), utc), utcCandles.single().dayStartMs)
    }

    // --- bounds: ось значений ------------------------------------------------

    @Test
    fun `bounds adds eight percent air on both sides`() {
        val bounds = ChartMath.bounds(listOf(10.0, 20.0))

        // диапазон 10 -> воздух 0.8 с каждой стороны
        assertClose(9.2, bounds.min)
        assertClose(20.8, bounds.max)
        assertTrue(bounds.min < 10.0)
        assertTrue(bounds.max > 20.0)
    }

    @Test
    fun `bounds zero range is expanded and value stays in the middle`() {
        val bounds = ChartMath.bounds(listOf(5.0))

        assertTrue("min must be below max, was ${bounds.min}..${bounds.max}", bounds.min < bounds.max)
        assertTrue(bounds.min < 5.0)
        assertTrue(bounds.max > 5.0)
        // единственное значение должно попасть в середину оси
        assertEquals(0.5f, bounds.yRatio(5.0), 1e-4f)
    }

    @Test
    fun `bounds zero range at zero does not collapse`() {
        val bounds = ChartMath.bounds(listOf(0.0))

        assertTrue(bounds.min < 0.0)
        assertTrue(bounds.max > 0.0)
        assertEquals(0.5f, bounds.yRatio(0.0), 1e-4f)
    }

    @Test
    fun `bounds handles negative values`() {
        val bounds = ChartMath.bounds(listOf(-10.0, -2.0))

        assertClose(-10.64, bounds.min)
        assertClose(-1.36, bounds.max)
        assertTrue(bounds.yRatio(-10.0) > 0f)
        assertTrue(bounds.yRatio(-2.0) < 1f)
        assertTrue(bounds.yRatio(-2.0) > bounds.yRatio(-10.0))
    }

    @Test
    fun `bounds empty input returns safe unit range`() {
        val bounds = ChartMath.bounds(emptyList())

        assertTrue(bounds.min < bounds.max)
        assertEquals(0f, bounds.yRatio(bounds.min), 1e-4f)
        assertEquals(1f, bounds.yRatio(bounds.max), 1e-4f)
    }

    @Test
    fun `bounds yRatio maps min to zero and max to one and clamps outside`() {
        val bounds = ChartMath.ValueBounds(0.0, 100.0)

        assertEquals(0f, bounds.yRatio(0.0), 1e-6f)
        assertEquals(1f, bounds.yRatio(100.0), 1e-6f)
        assertEquals(0.5f, bounds.yRatio(50.0), 1e-6f)
        assertEquals(0f, bounds.yRatio(-50.0), 1e-6f)
        assertEquals(1f, bounds.yRatio(150.0), 1e-6f)
    }

    // --- dateLabelIndices: подписи оси дат -----------------------------------

    @Test
    fun `dateLabelIndices zero count is empty`() {
        assertTrue(ChartMath.dateLabelIndices(0).isEmpty())
        assertTrue(ChartMath.dateLabelIndices(-3).isEmpty())
    }

    @Test
    fun `dateLabelIndices single candle is zero`() {
        assertEquals(listOf(0), ChartMath.dateLabelIndices(1))
    }

    @Test
    fun `dateLabelIndices fewer candles than limit lists all indices`() {
        assertEquals(listOf(0, 1, 2), ChartMath.dateLabelIndices(3))
        assertEquals(listOf(0, 1, 2, 3, 4), ChartMath.dateLabelIndices(5))
    }

    @Test
    fun `dateLabelIndices hundred candles yields five unique even indices`() {
        val indices = ChartMath.dateLabelIndices(100, maxLabels = 5)

        assertEquals(5, indices.size)
        assertEquals(0, indices.first())
        assertEquals(99, indices.last())
        assertEquals(indices.distinct().size, indices.size)
        assertEquals(indices.sorted(), indices)
    }

    @Test
    fun `dateLabelIndices six candles with five labels keeps first and last`() {
        val indices = ChartMath.dateLabelIndices(6, maxLabels = 5)

        assertEquals(5, indices.size)
        assertEquals(0, indices.first())
        assertEquals(5, indices.last())
        assertEquals(indices.distinct().size, indices.size)
        assertEquals(indices.sorted(), indices)
    }

    @Test
    fun `dateLabelIndices respects custom max labels`() {
        assertEquals(listOf(0), ChartMath.dateLabelIndices(10, maxLabels = 1))
        assertTrue(ChartMath.dateLabelIndices(10, maxLabels = 0).isEmpty())
        assertTrue(ChartMath.dateLabelIndices(10, maxLabels = -1).isEmpty())

        val three = ChartMath.dateLabelIndices(30, maxLabels = 3)
        assertEquals(listOf(0, 15, 29), three)
    }

    // --- axisDate: формат dd.MM ----------------------------------------------

    @Test
    fun `axisDate formats day and month with leading zeros`() {
        assertEquals("15.03", ChartMath.axisDate(startOfDay(LocalDate.of(2024, 3, 15), moscow), moscow))
        assertEquals("01.01", ChartMath.axisDate(startOfDay(LocalDate.of(2024, 1, 1), moscow), moscow))
        assertEquals("09.11", ChartMath.axisDate(startOfDay(LocalDate.of(2024, 11, 9), moscow), moscow))
    }

    @Test
    fun `axisDate uses the provided zone not UTC`() {
        // 14.03 22:30 UTC == 15.03 01:30 МСК
        val instantMs: Long = Instant.parse("2024-03-14T22:30:00Z").toEpochMilli()

        assertEquals("15.03", ChartMath.axisDate(instantMs, moscow))
        assertEquals("14.03", ChartMath.axisDate(instantMs, utc))
    }

    @Test
    fun `axisDate ignores default locale`() {
        val dayStart = startOfDay(LocalDate.of(2024, 3, 15), moscow)
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("ar", "SA"))
            // При арабской локали DateTimeFormatter.ofPattern без Locale.ROOT
            // выдал бы арабо-индийские цифры или другой разделитель.
            assertEquals("15.03", ChartMath.axisDate(dayStart, moscow))
        } finally {
            Locale.setDefault(previous)
        }
    }

    // --- candle ordering inside TrendLine data -------------------------------

    @Test
    fun `candles keep chronological order inside a day regardless of input order`() {
        val samples = listOf(
            ScoreSample(at("2024-04-01T20:00", moscow), 9.0),
            ScoreSample(at("2024-04-01T06:00", moscow), 1.0),
            ScoreSample(at("2024-04-01T13:00", moscow), 4.0)
        )

        val candle: OhlcCandle = ChartMath.candles(samples, moscow).single()

        assertClose(1.0, candle.open)
        assertClose(9.0, candle.close)
        assertClose(9.0, candle.high)
        assertClose(1.0, candle.low)
    }
}
