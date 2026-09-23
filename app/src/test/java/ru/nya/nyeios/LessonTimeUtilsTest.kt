package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.ui.schedule.LessonTimeUtils
import java.time.LocalTime

class LessonTimeUtilsTest {

    @Test
    fun testComputeLessonProgress_duringLesson() {
        val timeStr = "1 пара 08:30 - 10:00"
        val now = LocalTime.of(9, 15)
        val info = LessonTimeUtils.computeLessonProgress(timeStr, isToday = true, now = now)

        assertNotNull(info)
        assertTrue(info!!.isOngoing)
        assertFalse(info.isFinished)
        assertFalse(info.isUpcoming)
        assertEquals(0.5f, info.progress, 0.001f)
        assertEquals(2700L, info.elapsedSeconds)
        assertEquals(2700L, info.remainingSeconds)
        assertEquals("45 мин 0 с", info.elapsedText)
        assertEquals("45 мин 0 с", info.remainingText)
    }

    @Test
    fun testComputeLessonProgress_atStart() {
        val timeStr = "08:30 - 10:00"
        val now = LocalTime.of(8, 30)
        val info = LessonTimeUtils.computeLessonProgress(timeStr, isToday = true, now = now)

        assertNotNull(info)
        assertTrue(info!!.isOngoing)
        assertEquals(0.0f, info.progress, 0.001f)
        assertEquals(0L, info.elapsedSeconds)
        assertEquals(5400L, info.remainingSeconds)
        assertEquals("0 с", info.elapsedText)
        assertEquals("1 ч 30 мин 0 с", info.remainingText)
    }

    @Test
    fun testComputeLessonProgress_beforeLesson() {
        val timeStr = "2 пара 10:15 - 11:45"
        val now = LocalTime.of(9, 30)
        val info = LessonTimeUtils.computeLessonProgress(timeStr, isToday = true, now = now)

        assertNotNull(info)
        assertFalse(info!!.isOngoing)
        assertTrue(info.isUpcoming)
        assertFalse(info.isFinished)
        assertEquals(0.0f, info.progress, 0.001f)
        assertTrue(info.remainingText.startsWith("До начала: 45 мин"))
    }

    @Test
    fun testComputeLessonProgress_afterLesson() {
        val timeStr = "1 пара 08:30 - 10:00"
        val now = LocalTime.of(10, 1)
        val info = LessonTimeUtils.computeLessonProgress(timeStr, isToday = true, now = now)

        assertNotNull(info)
        assertFalse(info!!.isOngoing)
        assertTrue(info.isFinished)
        assertFalse(info.isUpcoming)
        assertEquals(1.0f, info.progress, 0.001f)
        assertEquals("Завершена", info.remainingText)
        assertEquals("1 ч 30 мин 0 с", info.elapsedText)
    }

    @Test
    fun testComputeLessonProgress_notToday() {
        val timeStr = "1 пара 08:30 - 10:00"
        val now = LocalTime.of(9, 0)
        val info = LessonTimeUtils.computeLessonProgress(timeStr, isToday = false, now = now)

        assertNotNull(info)
        assertFalse(info!!.isOngoing)
        assertFalse(info.isFinished)
        assertFalse(info.isUpcoming)
        assertEquals(0f, info.progress, 0.001f)
    }

    @Test
    fun testComputeLessonProgress_invalidString() {
        val info = LessonTimeUtils.computeLessonProgress("Некорректное время")
        assertNull(info)
    }

    @Test
    fun testFormatLessonDuration() {
        assertEquals("0 с", LessonTimeUtils.formatLessonDuration(0))
        assertEquals("45 с", LessonTimeUtils.formatLessonDuration(45))
        assertEquals("1 мин 0 с", LessonTimeUtils.formatLessonDuration(60))
        assertEquals("1 мин 15 с", LessonTimeUtils.formatLessonDuration(75))
        assertEquals("1 ч 0 мин 0 с", LessonTimeUtils.formatLessonDuration(3600))
        assertEquals("1 ч 29 мин 45 с", LessonTimeUtils.formatLessonDuration(5385))
    }
}
