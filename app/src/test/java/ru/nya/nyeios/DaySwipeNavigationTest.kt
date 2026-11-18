package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.nya.nyeios.ui.schedule.DaySwipeDirection
import ru.nya.nyeios.ui.schedule.DaySwipeNavigation

/**
 * Свайп влево-вправо по области расписания переключает выбранный день недели.
 *
 * Решение о переключении принимается по суммарному горизонтальному смещению и
 * по скорости жеста (быстрый короткий флик тоже должен работать). Направление:
 * палец влево — следующий день, палец вправо — предыдущий (контент уезжает влево).
 *
 * Переключение возможно только между днями, которые реально показаны в ряду:
 * воскресенье заменено ячейкой напоминаний и в список выбора не попадает.
 */
class DaySwipeNavigationTest {

    // Плотность 2.0: 1 dp = 2 px.
    private val density = 2f
    private val thresholdPx = DaySwipeNavigation.DISTANCE_THRESHOLD_DP * density
    private val velocityThreshold = DaySwipeNavigation.VELOCITY_THRESHOLD_DP_PER_SECOND

    /** Обычная неделя: понедельник..суббота выбираемы (0..5), воскресенье (6) — напоминания. */
    private val weekdayIndexes = listOf(0, 1, 2, 3, 4, 5)

    // ---------- directionOf ----------

    @Test
    fun `long drag left selects next day`() {
        val direction = DaySwipeNavigation.directionOf(
            totalDragXPx = -thresholdPx * 1.5f,
            velocityXPxPerSecond = 0f,
            density = density
        )
        assertEquals(DaySwipeDirection.NEXT, direction)
    }

    @Test
    fun `long drag right selects previous day`() {
        val direction = DaySwipeNavigation.directionOf(
            totalDragXPx = thresholdPx * 1.5f,
            velocityXPxPerSecond = 0f,
            density = density
        )
        assertEquals(DaySwipeDirection.PREVIOUS, direction)
    }

    @Test
    fun `drag exactly at distance threshold commits`() {
        val direction = DaySwipeNavigation.directionOf(
            totalDragXPx = -thresholdPx,
            velocityXPxPerSecond = 0f,
            density = density
        )
        assertEquals(DaySwipeDirection.NEXT, direction)
    }

    @Test
    fun `deliberate medium swipe commits after touch slop is excluded`() {
        // Детектор начинает считать ход только после touch slop (~8 dp), поэтому
        // палец, прошедший ~56 dp, оставляет ~48 dp измеренного смещения.
        // Такой осознанный жест обязан переключать день, а не «глохнуть».
        val direction = DaySwipeNavigation.directionOf(
            totalDragXPx = -48f * density,
            velocityXPxPerSecond = 0f,
            density = density
        )
        assertEquals(DaySwipeDirection.NEXT, direction)
    }

    @Test
    fun `short slow drag does not change day`() {
        assertNull(
            DaySwipeNavigation.directionOf(
                totalDragXPx = -thresholdPx * 0.4f,
                velocityXPxPerSecond = 0f,
                density = density
            )
        )
    }

    @Test
    fun `fast short flick left still selects next day`() {
        val direction = DaySwipeNavigation.directionOf(
            totalDragXPx = -thresholdPx * 0.25f,
            velocityXPxPerSecond = -(velocityThreshold + 100f) * density,
            density = density
        )
        assertEquals(DaySwipeDirection.NEXT, direction)
    }

    @Test
    fun `fast short flick right still selects previous day`() {
        val direction = DaySwipeNavigation.directionOf(
            totalDragXPx = thresholdPx * 0.25f,
            velocityXPxPerSecond = (velocityThreshold + 100f) * density,
            density = density
        )
        assertEquals(DaySwipeDirection.PREVIOUS, direction)
    }

    @Test
    fun `flick against drag direction does not change day`() {
        // Палец ушёл немного вправо, но отпущен резко влево — это не осознанный свайп.
        assertNull(
            DaySwipeNavigation.directionOf(
                totalDragXPx = thresholdPx * 0.25f,
                velocityXPxPerSecond = -(velocityThreshold + 100f) * density,
                density = density
            )
        )
    }

    @Test
    fun `distance wins over opposite velocity`() {
        // Смещение за порогом всегда переключает день, даже если отпустили против движения.
        val direction = DaySwipeNavigation.directionOf(
            totalDragXPx = -thresholdPx * 1.5f,
            velocityXPxPerSecond = (velocityThreshold + 100f) * density,
            density = density
        )
        assertEquals(DaySwipeDirection.NEXT, direction)
    }

    @Test
    fun `non finite velocity is treated as no swipe`() {
        assertNull(
            DaySwipeNavigation.directionOf(
                totalDragXPx = -thresholdPx * 0.25f,
                velocityXPxPerSecond = Float.NaN,
                density = density
            )
        )
    }

    @Test
    fun `non positive density cannot produce a direction`() {
        assertNull(
            DaySwipeNavigation.directionOf(
                totalDragXPx = -thresholdPx * 2f,
                velocityXPxPerSecond = 0f,
                density = 0f
            )
        )
    }

    // ---------- targetIndex ----------

    @Test
    fun `next target is the next selectable day`() {
        assertEquals(
            2,
            DaySwipeNavigation.targetIndex(weekdayIndexes, currentIndex = 1, direction = DaySwipeDirection.NEXT)
        )
    }

    @Test
    fun `previous target is the previous selectable day`() {
        assertEquals(
            1,
            DaySwipeNavigation.targetIndex(weekdayIndexes, currentIndex = 2, direction = DaySwipeDirection.PREVIOUS)
        )
    }

    @Test
    fun `swipe back from first day does nothing`() {
        assertNull(
            DaySwipeNavigation.targetIndex(weekdayIndexes, currentIndex = 0, direction = DaySwipeDirection.PREVIOUS)
        )
    }

    @Test
    fun `swipe forward from last selectable day does nothing`() {
        // Суббота — последний день с парами; воскресенье открывает напоминания, а не расписание.
        assertNull(
            DaySwipeNavigation.targetIndex(weekdayIndexes, currentIndex = 5, direction = DaySwipeDirection.NEXT)
        )
    }

    @Test
    fun `targets skip indexes that are not selectable`() {
        // Воскресенье в середине списка (нестандартный порядок) всё равно пропускается.
        val withSundayInMiddle = listOf(0, 1, 3, 4, 5)
        assertEquals(
            3,
            DaySwipeNavigation.targetIndex(withSundayInMiddle, currentIndex = 1, direction = DaySwipeDirection.NEXT)
        )
        assertEquals(
            1,
            DaySwipeNavigation.targetIndex(withSundayInMiddle, currentIndex = 3, direction = DaySwipeDirection.PREVIOUS)
        )
    }

    @Test
    fun `swipe back from reminders cell selects the last shown day`() {
        // В воскресенье выбранным может оказаться день, который в ряду заменён
        // ячейкой напоминаний: свайп вправо обязан вести на субботу, а не «в никуда».
        assertEquals(
            5,
            DaySwipeNavigation.targetIndex(weekdayIndexes, currentIndex = 6, direction = DaySwipeDirection.PREVIOUS)
        )
    }

    @Test
    fun `swipe forward from the last day does nothing`() {
        assertNull(
            DaySwipeNavigation.targetIndex(weekdayIndexes, currentIndex = 6, direction = DaySwipeDirection.NEXT)
        )
    }

    @Test
    fun `current index outside selectable list does nothing without neighbours`() {
        // Индекс за пределами недели: соседей в указанном направлении нет.
        assertNull(
            DaySwipeNavigation.targetIndex(weekdayIndexes, currentIndex = 42, direction = DaySwipeDirection.NEXT)
        )
        assertNull(
            DaySwipeNavigation.targetIndex(weekdayIndexes, currentIndex = -1, direction = DaySwipeDirection.PREVIOUS)
        )
    }

    @Test
    fun `empty selectable list does nothing`() {
        assertNull(
            DaySwipeNavigation.targetIndex(emptyList(), currentIndex = 0, direction = DaySwipeDirection.NEXT)
        )
    }
}
