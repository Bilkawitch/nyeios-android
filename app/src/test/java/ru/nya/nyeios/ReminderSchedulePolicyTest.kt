package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.reminders.Reminder
import ru.nya.nyeios.data.reminders.ReminderAlarmMode
import ru.nya.nyeios.data.reminders.ReminderScheduleNotice
import ru.nya.nyeios.data.reminders.ReminderSchedulePolicy
import ru.nya.nyeios.data.reminders.ReminderTriggerCheck

class ReminderSchedulePolicyTest {

    private fun reminder(id: String, triggerAtUtcMs: Long) =
        Reminder(id = id, triggerAtUtcMs = triggerAtUtcMs)

    // --- checkTrigger ---

    @Test
    fun triggerInPastIsRejected() {
        assertEquals(ReminderTriggerCheck.InPast, ReminderSchedulePolicy.checkTrigger(1_000L, 5_000L))
    }

    @Test
    fun triggerEqualToNowIsRejected() {
        assertEquals(ReminderTriggerCheck.InPast, ReminderSchedulePolicy.checkTrigger(5_000L, 5_000L))
    }

    @Test
    fun triggerCloserThanMinimumLeadIsTooSoon() {
        assertEquals(
            ReminderTriggerCheck.TooSoon,
            ReminderSchedulePolicy.checkTrigger(5_000L + ReminderSchedulePolicy.MIN_LEAD_MS - 1, 5_000L)
        )
    }

    @Test
    fun triggerExactlyAtMinimumLeadIsValid() {
        assertEquals(
            ReminderTriggerCheck.Valid,
            ReminderSchedulePolicy.checkTrigger(5_000L + ReminderSchedulePolicy.MIN_LEAD_MS, 5_000L)
        )
    }

    @Test
    fun triggerFarInFutureIsValid() {
        assertEquals(ReminderTriggerCheck.Valid, ReminderSchedulePolicy.checkTrigger(5_000L + 86_400_000L, 5_000L))
    }

    // --- modeFor ---

    @Test
    fun exactModeWhenAllowed() {
        assertEquals(ReminderAlarmMode.EXACT, ReminderSchedulePolicy.modeFor(exactAllowed = true))
    }

    @Test
    fun inexactModeWhenExactNotAllowed() {
        assertEquals(ReminderAlarmMode.INEXACT, ReminderSchedulePolicy.modeFor(exactAllowed = false))
    }

    // --- futureReminders ---

    @Test
    fun futureRemindersKeepsOnlyFutureOnesOrderedByTrigger() {
        val now = 2_000L
        val list = listOf(
            reminder("past", 1_500L),
            reminder("later", 3_000L),
            reminder("now", 2_000L),
            reminder("soon", 2_500L)
        )
        assertEquals(listOf("soon", "later"), ReminderSchedulePolicy.futureReminders(list, now).map { it.id })
    }

    @Test
    fun futureRemindersIsStableForEqualTriggers() {
        val list = listOf(reminder("a", 5_000L), reminder("b", 5_000L))
        assertEquals(listOf("a", "b"), ReminderSchedulePolicy.futureReminders(list, 1_000L).map { it.id })
    }

    @Test
    fun futureRemindersOfEmptyListIsEmpty() {
        assertTrue(ReminderSchedulePolicy.futureReminders(emptyList(), 1_000L).isEmpty())
    }

    // --- nextReminder ---

    @Test
    fun nextReminderReturnsEarliestFuture() {
        val list = listOf(reminder("later", 9_000L), reminder("past", 500L), reminder("soon", 3_000L))
        assertEquals("soon", ReminderSchedulePolicy.nextReminder(list, 1_000L)?.id)
    }

    @Test
    fun nextReminderIsNullWhenNothingIsFuture() {
        val list = listOf(reminder("past", 500L))
        assertNull(ReminderSchedulePolicy.nextReminder(list, 1_000L))
    }

    @Test
    fun nextReminderIsNullForEmptyList() {
        assertNull(ReminderSchedulePolicy.nextReminder(emptyList(), 1_000L))
    }

    // --- sortForList ---

    @Test
    fun sortForListPutsFutureAscendingThenExpiredDescending() {
        val now = 2_000L
        val list = listOf(
            reminder("expired-old", 1_000L),
            reminder("future-later", 3_000L),
            reminder("expired-new", 1_500L),
            reminder("future-soon", 2_500L)
        )
        assertEquals(
            listOf("future-soon", "future-later", "expired-new", "expired-old"),
            ReminderSchedulePolicy.sortForList(list, now).map { it.id }
        )
    }

    @Test
    fun sortForListEmptyStaysEmpty() {
        assertTrue(ReminderSchedulePolicy.sortForList(emptyList(), 1_000L).isEmpty())
    }

    // --- noticeFor (сообщение пользователю о результате постановки будильника) ---

    @Test
    fun noticeForScheduledExactIsExact() {
        assertEquals(
            ReminderScheduleNotice.EXACT,
            ReminderSchedulePolicy.noticeFor(scheduled = true, mode = ReminderAlarmMode.EXACT)
        )
    }

    @Test
    fun noticeForScheduledInexactIsInexact() {
        assertEquals(
            ReminderScheduleNotice.INEXACT,
            ReminderSchedulePolicy.noticeFor(scheduled = true, mode = ReminderAlarmMode.INEXACT)
        )
    }

    @Test
    fun noticeForFailedScheduleIsFailedRegardlessOfMode() {
        assertEquals(
            ReminderScheduleNotice.FAILED,
            ReminderSchedulePolicy.noticeFor(scheduled = false, mode = ReminderAlarmMode.EXACT)
        )
        assertEquals(
            ReminderScheduleNotice.FAILED,
            ReminderSchedulePolicy.noticeFor(scheduled = false, mode = ReminderAlarmMode.INEXACT)
        )
    }
}