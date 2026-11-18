package ru.nya.nyeios.ui.notes.reminder

import ru.nya.nyeios.data.reminders.ReminderSchedulePolicy
import ru.nya.nyeios.data.reminders.ReminderSetupStatus
import ru.nya.nyeios.data.reminders.ReminderTriggerCheck
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Состояние валидации выбранного момента в диалоге напоминания. */
enum class ReminderDraftValidation { VALID, IN_PAST, TOO_SOON }

/**
 * Чистая логика диалога создания напоминания: предзаполнение момента срабатывания,
 * округление фолбэк-времени, содержание по умолчанию и маппинг результатов проверок
 * на состояние UI. Никаких Android API — покрывается JVM-тестами.
 *
 * Вся арифметика дат идёт в явно переданной зоне: экран передаёт
 * `LessonTimeUtils.moscowZone`, тесты — фиксированную зону.
 */
object ReminderDraftModel {

    /** Минимальный запас времени до срабатывания (тот же, что у политики планирования). */
    const val MIN_LEAD_MS: Long = ReminderSchedulePolicy.MIN_LEAD_MS

    /** Шаг, до которого округляется фолбэк-время «сейчас + час». */
    const val ROUNDING_STEP_MINUTES: Long = 5L

    /** Запас, который предлагается, когда время пары уже прошло. */
    const val FALLBACK_DELAY_MS: Long = 60L * 60L * 1000L

    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val timeStartRegex = Regex("""(\d{1,2}):(\d{2})""")

    // --- Разбор данных пары ---

    /** Дата пары из `dateString` (`dd.MM.yyyy`) или null, если формат не тот. */
    fun parseLessonDate(dateString: String?): LocalDate? {
        val raw = dateString?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return try {
            LocalDate.parse(raw, dateFormat)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** Время начала пары из диапазона вида `13:40-15:10` или null. */
    fun parseLessonStartTime(lessonTime: String?): LocalTime? {
        val match = timeStartRegex.find(lessonTime?.trim().orEmpty()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    /** Момент начала пары в UTC-миллисекундах или null, если дата/время не разобрались. */
    fun lessonTriggerAtUtcMs(dateString: String?, lessonTime: String?, zone: ZoneId): Long? {
        val date = parseLessonDate(dateString) ?: return null
        val time = parseLessonStartTime(lessonTime) ?: return null
        return combine(date, time.hour, time.minute, zone)
    }

    // --- Предзаполнение ---

    /**
     * Момент по умолчанию: начало пары, если оно минимум на [MIN_LEAD_MS] в будущем,
     * иначе «сейчас + час», округлённое вверх до [ROUNDING_STEP_MINUTES].
     */
    fun defaultTriggerAtUtcMs(
        dateString: String?,
        lessonTime: String?,
        nowUtcMs: Long,
        zone: ZoneId
    ): Long {
        val lesson = lessonTriggerAtUtcMs(dateString, lessonTime, zone)
        if (lesson != null && lesson - nowUtcMs >= MIN_LEAD_MS) return lesson
        return roundUpToStep(nowUtcMs + FALLBACK_DELAY_MS, zone)
    }

    /**
     * Округляет момент вверх до ближайшей границы [stepMinutes] по настенным часам зоны.
     * Результат всегда строго не раньше входного значения: секунды и доли секунды
     * сдвигают к следующей границе.
     */
    fun roundUpToStep(
        utcMs: Long,
        zone: ZoneId,
        stepMinutes: Long = ROUNDING_STEP_MINUTES
    ): Long {
        require(stepMinutes in 1..60) { "stepMinutes must be in 1..60" }
        val local = Instant.ofEpochMilli(utcMs).atZone(zone)
        val hasSubMinute = local.second != 0 || local.nano != 0
        val truncated = local.withSecond(0).withNano(0)
        val remainder = truncated.minute % stepMinutes.toInt()
        val addMinutes = when {
            remainder != 0 -> stepMinutes - remainder
            hasSubMinute -> stepMinutes
            else -> 0L
        }
        return truncated.plusMinutes(addMinutes).toInstant().toEpochMilli()
    }

    /** Содержание по умолчанию: текст редактора, иначе предмет пары. */
    fun initialContent(editorPlainText: String?, subject: String?): String {
        val text = editorPlainText?.trim().orEmpty()
        return text.ifEmpty { subject?.trim().orEmpty() }
    }

    // --- Валидация ---

    /** Маппинг результата [ReminderSchedulePolicy.checkTrigger] на состояние диалога. */
    fun validationFor(check: ReminderTriggerCheck): ReminderDraftValidation = when (check) {
        ReminderTriggerCheck.Valid -> ReminderDraftValidation.VALID
        ReminderTriggerCheck.InPast -> ReminderDraftValidation.IN_PAST
        ReminderTriggerCheck.TooSoon -> ReminderDraftValidation.TOO_SOON
    }

    // --- Преобразования даты и времени ---

    /** Собирает UTC-момент из локальных даты, часа и минуты в заданной зоне. */
    fun combine(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** Локальная дата момента в заданной зоне. */
    fun dateOf(utcMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(utcMs).atZone(zone).toLocalDate()

    /** Локальное время момента в заданной зоне (с точностью до минуты). */
    fun timeOf(utcMs: Long, zone: ZoneId): LocalTime =
        Instant.ofEpochMilli(utcMs).atZone(zone).toLocalTime().withSecond(0).withNano(0)

    /** Дата в формате портала `dd.MM.yyyy`. */
    fun formatDate(date: LocalDate): String = date.format(dateFormat)

    /** Время в 24-часовом формате `HH:mm` без зависимости от локали устройства. */
    fun formatTime(hour: Int, minute: Int): String =
        String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    // --- Разрешения ---

    /** Переводит системный статус в контракт UI-диалога разрешений. */
    fun setupIssues(status: ReminderSetupStatus): ReminderSetupIssues = ReminderSetupIssues(
        notificationsEnabled = status.notificationsEnabled,
        exactAlarmsAllowed = status.exactAlarmsAllowed,
        batteryOptimizationIgnored = status.batteryOptimizationIgnored
    )
}
