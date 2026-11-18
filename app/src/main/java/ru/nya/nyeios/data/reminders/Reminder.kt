package ru.nya.nyeios.data.reminders

/**
 * Domain-модель напоминания о домашнем задании.
 *
 * Хранится как снимок: содержимое заметки копируется в момент создания напоминания,
 * чтобы дальнейшие правки заметки не меняли уже доставленное уведомление.
 */
data class Reminder(
    val id: String = "",
    val noteKey: String = "",
    val subject: String = "",
    val lessonDate: String = "",
    val lessonTime: String = "",
    val lessonNumber: String = "",
    val noteText: String = "",
    /** Момент срабатывания в UTC-миллисекундах (epoch). */
    val triggerAtUtcMs: Long = 0L,
    val createdAtUtcMs: Long = 0L,
    val updatedAtUtcMs: Long = 0L
) {
    fun normalized(): Reminder = copy(
        id = id.orEmpty(),
        noteKey = noteKey.orEmpty(),
        subject = subject.orEmpty(),
        lessonDate = lessonDate.orEmpty(),
        lessonTime = lessonTime.orEmpty(),
        lessonNumber = lessonNumber.orEmpty(),
        noteText = noteText.orEmpty()
    )
}

/** Режим, которым система поставила будильник. */
enum class ReminderAlarmMode { EXACT, INEXACT }

/** Результат постановки будильника: [scheduled] = false, если система отказала. */
data class ReminderScheduleResult(
    val scheduled: Boolean,
    val mode: ReminderAlarmMode,
    val triggerAtUtcMs: Long
)

/** Проверка выбранного пользователем момента перед постановкой напоминания. */
sealed class ReminderTriggerCheck {
    data object Valid : ReminderTriggerCheck()
    data object InPast : ReminderTriggerCheck()
    data object TooSoon : ReminderTriggerCheck()
}

/** Что сообщить пользователю после попытки поставить будильник. */
enum class ReminderScheduleNotice {
    /** Будильник поставлен точным — сработает ровно в выбранное время. */
    EXACT,

    /** Поставлен неточный будильник: время срабатывания может сдвинуться. */
    INEXACT,

    /** Поставить будильник не удалось — напоминание не сработает. */
    FAILED
}

/** Результат сохранения напоминания: запись и статус её будильника. */
data class ReminderSaveResult(
    val reminder: Reminder,
    val schedule: ReminderScheduleResult
) {
    val notice: ReminderScheduleNotice
        get() = ReminderSchedulePolicy.noticeFor(schedule.scheduled, schedule.mode)
}
