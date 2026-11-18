package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.reminders.ReminderSetupStatus
import ru.nya.nyeios.data.reminders.ReminderTriggerCheck
import ru.nya.nyeios.ui.notes.reminder.ReminderDraftModel
import ru.nya.nyeios.ui.notes.reminder.ReminderDraftValidation
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Чистая логика диалога напоминания: предзаполнение, округление, фолбэк содержания
 * и маппинг результатов проверки на состояние UI. Android API не участвует.
 */
class ReminderDraftModelTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private fun msk(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0
    ): Long = LocalDateTime.of(year, month, day, hour, minute, second)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun local(utcMs: Long): LocalDateTime =
        Instant.ofEpochMilli(utcMs).atZone(zone).toLocalDateTime()

    // --- Предзаполнение момента срабатывания ---

    @Test
    fun `default prefill uses lesson date and start time when in future`() {
        val now = msk(2026, 11, 18, 10, 0)

        val trigger = ReminderDraftModel.defaultTriggerAtUtcMs(
            dateString = "18.11.2026",
            lessonTime = "13:40-15:10",
            nowUtcMs = now,
            zone = zone
        )

        assertEquals(msk(2026, 11, 18, 13, 40), trigger)
    }

    @Test
    fun `default prefill keeps lesson instant when exactly one minute ahead`() {
        val now = msk(2026, 11, 18, 13, 39, 0)

        val trigger = ReminderDraftModel.defaultTriggerAtUtcMs("18.11.2026", "13:40-15:10", now, zone)

        assertEquals(msk(2026, 11, 18, 13, 40), trigger)
    }

    @Test
    fun `default prefill falls back to now plus hour when lesson instant is too soon`() {
        val now = msk(2026, 11, 18, 13, 39, 30)

        val trigger = ReminderDraftModel.defaultTriggerAtUtcMs("18.11.2026", "13:40-15:10", now, zone)

        // now + 1 h = 14:39:30 -> вверх до 5-минутной границы
        assertEquals(msk(2026, 11, 18, 14, 40), trigger)
    }

    @Test
    fun `default prefill falls back to now plus hour when lesson time already passed`() {
        val now = msk(2026, 11, 18, 14, 2)

        val trigger = ReminderDraftModel.defaultTriggerAtUtcMs("18.11.2026", "13:40-15:10", now, zone)

        // now + 1 h = 15:02 -> вверх до 15:05
        assertEquals(msk(2026, 11, 18, 15, 5), trigger)
    }

    @Test
    fun `default prefill keeps exact five minute boundary after adding hour`() {
        val now = msk(2026, 11, 18, 10, 0)

        val trigger = ReminderDraftModel.defaultTriggerAtUtcMs("01.01.2020", "13:40-15:10", now, zone)

        assertEquals(msk(2026, 11, 18, 11, 0), trigger)
    }

    @Test
    fun `default prefill falls back when date cannot be parsed`() {
        val now = msk(2026, 11, 18, 10, 0)

        val trigger = ReminderDraftModel.defaultTriggerAtUtcMs("18/11/2026", "13:40-15:10", now, zone)

        assertEquals(msk(2026, 11, 18, 11, 0), trigger)
    }

    @Test
    fun `default prefill falls back when time cannot be parsed`() {
        val now = msk(2026, 11, 18, 10, 2)

        val trigger = ReminderDraftModel.defaultTriggerAtUtcMs("18.11.2026", "", now, zone)

        assertEquals(msk(2026, 11, 18, 11, 5), trigger)
    }

    @Test
    fun `default prefill is always at least one minute ahead of now`() {
        val now = msk(2026, 11, 18, 10, 0, 30)

        val trigger = ReminderDraftModel.defaultTriggerAtUtcMs("18.11.2026", "13:40-15:10", now, zone)

        assertTrue(trigger - now >= ReminderDraftModel.MIN_LEAD_MS)
    }

    // --- Округление ---

    @Test
    fun `roundUpToStep keeps instant already on a five minute boundary`() {
        assertEquals(msk(2026, 11, 18, 11, 0), ReminderDraftModel.roundUpToStep(msk(2026, 11, 18, 11, 0), zone))
    }

    @Test
    fun `roundUpToStep moves partial minute to the next boundary`() {
        assertEquals(msk(2026, 11, 18, 11, 5), ReminderDraftModel.roundUpToStep(msk(2026, 11, 18, 11, 0, 30), zone))
        assertEquals(msk(2026, 11, 18, 11, 5), ReminderDraftModel.roundUpToStep(msk(2026, 11, 18, 11, 3), zone))
        assertEquals(msk(2026, 11, 18, 11, 5), ReminderDraftModel.roundUpToStep(msk(2026, 11, 18, 11, 5), zone))
        assertEquals(msk(2026, 11, 18, 11, 10), ReminderDraftModel.roundUpToStep(msk(2026, 11, 18, 11, 6), zone))
    }

    @Test
    fun `roundUpToStep never returns a past instant`() {
        val input = msk(2026, 11, 18, 11, 5, 59)

        assertTrue(ReminderDraftModel.roundUpToStep(input, zone) > input)
    }

    // --- Разбор данных пары ---

    @Test
    fun `parse lesson start time from a range`() {
        assertEquals(LocalTime.of(13, 40), ReminderDraftModel.parseLessonStartTime("13:40-15:10"))
        assertEquals(LocalTime.of(9, 5), ReminderDraftModel.parseLessonStartTime("9:05–10:35"))
        assertNull(ReminderDraftModel.parseLessonStartTime(""))
    }

    @Test
    fun `parse lesson date in dd dot mm dot yyyy format`() {
        assertEquals(LocalDate.of(2026, 11, 18), ReminderDraftModel.parseLessonDate("18.11.2026"))
        assertNull(ReminderDraftModel.parseLessonDate("2026-11-18"))
    }

    // --- Содержание ---

    @Test
    fun `initial content prefers trimmed editor text`() {
        assertEquals("Сдать отчёт", ReminderDraftModel.initialContent("  Сдать отчёт  ", "Физика"))
    }

    @Test
    fun `initial content falls back to subject when editor is blank`() {
        assertEquals("Физика", ReminderDraftModel.initialContent("   ", "Физика"))
        assertEquals("Физика", ReminderDraftModel.initialContent(null, " Физика "))
    }

    @Test
    fun `initial content is empty when editor and subject are empty`() {
        assertEquals("", ReminderDraftModel.initialContent(null, null))
        assertEquals("", ReminderDraftModel.initialContent("", "  "))
    }

    // --- Валидация ---

    @Test
    fun `validation maps trigger checks to dialog state`() {
        assertEquals(ReminderDraftValidation.VALID, ReminderDraftModel.validationFor(ReminderTriggerCheck.Valid))
        assertEquals(ReminderDraftValidation.IN_PAST, ReminderDraftModel.validationFor(ReminderTriggerCheck.InPast))
        assertEquals(ReminderDraftValidation.TOO_SOON, ReminderDraftModel.validationFor(ReminderTriggerCheck.TooSoon))
    }

    @Test
    fun `combine builds utc instant from local date and time`() {
        val trigger = ReminderDraftModel.combine(
            date = LocalDate.of(2026, 11, 18),
            hour = 13,
            minute = 40,
            zone = zone
        )

        assertEquals(msk(2026, 11, 18, 13, 40), trigger)
        assertEquals(LocalDate.of(2026, 11, 18), ReminderDraftModel.dateOf(trigger, zone))
        assertEquals(LocalTime.of(13, 40), ReminderDraftModel.timeOf(trigger, zone))
        assertEquals(13, local(trigger).hour)
    }

    // --- Разрешения ---

    @Test
    fun `setup issues mirror system status`() {
        val allGood = ReminderDraftModel.setupIssues(
            ReminderSetupStatus(
                notificationsEnabled = true,
                exactAlarmsAllowed = true,
                batteryOptimizationIgnored = true
            )
        )
        assertFalse(allGood.hasIssues)

        val batteryOnly = ReminderDraftModel.setupIssues(
            ReminderSetupStatus(
                notificationsEnabled = true,
                exactAlarmsAllowed = true,
                batteryOptimizationIgnored = false
            )
        )
        assertTrue(batteryOnly.hasIssues)
        assertFalse(batteryOnly.notificationsEnabled.not())
        assertFalse(batteryOnly.batteryOptimizationIgnored)
    }
}
