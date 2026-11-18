package ru.nya.nyeios.data.reminders

/**
 * Чистые правила планирования напоминаний. Android API здесь нет, поэтому
 * вся арифметика времени покрывается JVM-тестами.
 */
object ReminderSchedulePolicy {

    /** Минимальный запас времени: напоминание нельзя ставить «прямо сейчас». */
    const val MIN_LEAD_MS: Long = 60_000L

    /** Проверяет выбранный пользователем момент относительно «сейчас». */
    fun checkTrigger(triggerAtUtcMs: Long, nowUtcMs: Long): ReminderTriggerCheck = when {
        triggerAtUtcMs <= nowUtcMs -> ReminderTriggerCheck.InPast
        triggerAtUtcMs - nowUtcMs < MIN_LEAD_MS -> ReminderTriggerCheck.TooSoon
        else -> ReminderTriggerCheck.Valid
    }

    /** Точный будильник, если система разрешила, иначе — неточный с allow-idle. */
    fun modeFor(exactAllowed: Boolean): ReminderAlarmMode =
        if (exactAllowed) ReminderAlarmMode.EXACT else ReminderAlarmMode.INEXACT

    /**
     * Что сообщить пользователю о результате постановки будильника:
     * неудача важнее неточности, неточность важнее точной постановки.
     */
    fun noticeFor(scheduled: Boolean, mode: ReminderAlarmMode): ReminderScheduleNotice = when {
        !scheduled -> ReminderScheduleNotice.FAILED
        mode == ReminderAlarmMode.INEXACT -> ReminderScheduleNotice.INEXACT
        else -> ReminderScheduleNotice.EXACT
    }

    /** Только будущие напоминания, в порядке срабатывания (для перепланирования после загрузки). */
    fun futureReminders(reminders: List<Reminder>, nowUtcMs: Long): List<Reminder> =
        reminders.filter { it.triggerAtUtcMs > nowUtcMs }.sortedBy { it.triggerAtUtcMs }

    /** Ближайшее будущее напоминание или null. */
    fun nextReminder(reminders: List<Reminder>, nowUtcMs: Long): Reminder? =
        futureReminders(reminders, nowUtcMs).firstOrNull()

    /** Сортировка для списка: сначала будущие по возрастанию, затем истёкшие по убыванию. */
    fun sortForList(reminders: List<Reminder>, nowUtcMs: Long): List<Reminder> {
        val (future, expired) = reminders.partition { it.triggerAtUtcMs > nowUtcMs }
        return future.sortedBy { it.triggerAtUtcMs } + expired.sortedByDescending { it.triggerAtUtcMs }
    }
}
