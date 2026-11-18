package ru.nya.nyeios.data.reminders

import ru.nya.nyeios.data.AppLocale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Чистые функции форматирования текста напоминаний (без Android API).
 *
 * Язык берётся через [ru.nya.nyeios.data.AppLocale]: слой данных не имеет доступа к
 * локализованному Context, а `LanguageManager` держит `Locale.getDefault()` в синхронизации
 * с выбором языка в приложении (AGENTS.md §12.1). Тесты фиксируют локаль через
 * `Locale.setDefault(Locale("ru"))` в `@Before` и восстанавливают её в `@After` (§12.5).
 */
object ReminderFormatter {

    const val MAX_NOTIFICATION_BODY_CHARS = 240

    private const val ELLIPSIS = "…"
    private const val LIST_SEPARATOR = " · "

    private val htmlTagRegex = Regex("<[^>]*>")
    private val whitespaceRegex = Regex("\\s+")
    private val triggerFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm", Locale.ROOT)

    private fun homeworkLabel(): String = AppLocale.pick("ДЗ", "HW")

    private fun bodyPrefix(): String = AppLocale.pick("ДЗ: ", "HW: ")

    private fun fallbackContent(): String = AppLocale.pick("домашнее задание", "homework")

    private fun lessonLabel(number: String, time: String): String = when {
        number.isNotEmpty() && time.isNotEmpty() -> AppLocale.pick("ПАРА $number$LIST_SEPARATOR$time", "LESSON $number$LIST_SEPARATOR$time")
        number.isNotEmpty() -> AppLocale.pick("ПАРА $number", "LESSON $number")
        else -> time
    }

    /** Заголовок уведомления: «ДЗ · <предмет>» или «ДЗ · <пара · время>». */
    fun notificationTitle(reminder: Reminder): String =
        homeworkLabel() + LIST_SEPARATOR + listTitle(reminder)

    /** Тело уведомления: «ДЗ: <текст заметки>», обрезанное до [MAX_NOTIFICATION_BODY_CHARS]. */
    fun notificationBody(reminder: Reminder): String {
        val prefix = bodyPrefix()
        val budget = (MAX_NOTIFICATION_BODY_CHARS - prefix.length).coerceAtLeast(1)
        return prefix + sanitizeNotificationText(listContent(reminder), budget)
    }

    /** Заголовок строки в списке напоминаний: предмет или «ПАРА N · время». */
    fun listTitle(reminder: Reminder): String {
        val subject = normalizeNoteText(reminder.subject)
        if (subject.isNotEmpty()) return subject

        val number = normalizeNoteText(reminder.lessonNumber)
        val time = normalizeNoteText(reminder.lessonTime)
        if (number.isNotEmpty() || time.isNotEmpty()) return lessonLabel(number, time)

        return fallbackContent()
    }

    /** Содержимое строки в списке напоминаний (снимок текста заметки). */
    fun listContent(reminder: Reminder): String {
        val text = normalizeNoteText(reminder.noteText)
        return if (text.isEmpty()) fallbackContent() else text
    }

    /** «12.11.2026 · 09:00» — момент срабатывания в человекочитаемом виде. */
    fun formatTrigger(triggerAtUtcMs: Long, zone: ZoneId): String =
        triggerFormatter.format(Instant.ofEpochMilli(triggerAtUtcMs).atZone(zone))

    /**
     * Обратный отсчёт: «2 д 3 ч», «15 мин», «< 1 мин», «время истекло».
     * Отрицательная разница считается истёкшим временем.
     */
    fun formatCountdown(remainingMs: Long): String {
        if (remainingMs <= 0L) return AppLocale.pick("время истекло", "overdue")

        val totalMinutes = remainingMs / 60_000L
        if (totalMinutes < 1L) return AppLocale.pick("< 1 мин", "< 1 min")

        val days = remainingMs / 86_400_000L
        val hours = (remainingMs / 3_600_000L) % 24L
        val minutes = totalMinutes % 60L

        return when {
            days >= 1L -> AppLocale.pick("$days д $hours ч", "${days}d ${hours}h")
            hours >= 1L -> AppLocale.pick("$hours ч $minutes мин", "${hours}h ${minutes}m")
            else -> AppLocale.pick("$minutes мин", "${minutes}m")
        }
    }

    /** Убирает HTML-теги и лишние пробелы из сырого текста заметки. */
    fun normalizeNoteText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val withoutTags = raw.replace(htmlTagRegex, " ")
        val decoded = decodeEntities(withoutTags)
        return decoded.replace(whitespaceRegex, " ").trim()
    }

    /** Схлопывает переводы строк, обрезает по границе слова и добавляет многоточие. */
    fun sanitizeNotificationText(
        text: String,
        maxChars: Int = MAX_NOTIFICATION_BODY_CHARS
    ): String {
        if (maxChars <= 0) return ""
        val flat = text.replace(whitespaceRegex, " ").trim()
        if (flat.length <= maxChars) return flat
        if (maxChars == 1) return ELLIPSIS

        val slice = flat.substring(0, maxChars - 1)
        val lastSpace = slice.lastIndexOf(' ')
        val cut = if (lastSpace > maxChars / 3) slice.substring(0, lastSpace) else slice
        val trimmed = cut.trimEnd(' ', ',', '.', ';', ':', '—', '-')
        return (if (trimmed.isEmpty()) cut.trim() else trimmed) + ELLIPSIS
    }

    private fun decodeEntities(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
}
