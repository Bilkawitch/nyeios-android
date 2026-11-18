package ru.nya.nyeios

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.nya.nyeios.data.reminders.Reminder
import ru.nya.nyeios.data.reminders.ReminderFormatter
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Тексты напоминаний строятся в слое данных через AppLocale.pick (AGENTS.md §12.1),
 * поэтому каждый тест фиксирует локаль процесса (§12.5) и восстанавливает её после.
 */
class ReminderFormatterTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale("ru"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    private fun reminder(
        subject: String = "Математика",
        lessonNumber: String = "2",
        lessonTime: String = "10:15 - 11:45",
        noteText: String = "Решить задачу 5",
        triggerAtUtcMs: Long = 0L
    ) = Reminder(
        id = "r-1",
        noteKey = "note-1",
        subject = subject,
        lessonDate = "12.11.2026",
        lessonTime = lessonTime,
        lessonNumber = lessonNumber,
        noteText = noteText,
        triggerAtUtcMs = triggerAtUtcMs,
        createdAtUtcMs = 1L,
        updatedAtUtcMs = 2L
    )

    // --- notificationTitle ---

    @Test
    fun notificationTitleUsesSubjectWithHomeworkPrefix() {
        assertEquals("ДЗ · Математика", ReminderFormatter.notificationTitle(reminder()))
    }

    @Test
    fun notificationTitleFallsBackToLessonPairWhenSubjectBlank() {
        assertEquals(
            "ДЗ · ПАРА 2 · 10:15 - 11:45",
            ReminderFormatter.notificationTitle(reminder(subject = "  "))
        )
    }

    @Test
    fun notificationTitleFallsBackToGenericContentWhenEverythingBlank() {
        val empty = reminder(subject = "", lessonNumber = "", lessonTime = "", noteText = "")
        assertEquals("ДЗ · домашнее задание", ReminderFormatter.notificationTitle(empty))
    }

    @Test
    fun notificationTitleIsEnglishWhenLocaleIsEnglish() {
        Locale.setDefault(Locale("en"))
        assertEquals("HW · Math", ReminderFormatter.notificationTitle(reminder(subject = "Math")))
    }

    // --- notificationBody ---

    @Test
    fun notificationBodyPrefixesNormalizedNoteText() {
        val body = ReminderFormatter.notificationBody(
            reminder(noteText = "<b>Решить</b>   задачу\n\n5")
        )
        assertEquals("ДЗ: Решить задачу 5", body)
    }

    @Test
    fun notificationBodyUsesFallbackWhenNoteTextBlank() {
        assertEquals("ДЗ: домашнее задание", ReminderFormatter.notificationBody(reminder(noteText = "   ")))
    }

    @Test
    fun notificationBodyIsTruncatedToLimit() {
        val longText = (1..120).joinToString(" ") { "слово$it" }
        val body = ReminderFormatter.notificationBody(reminder(noteText = longText))
        assertTrue("body too long: ${body.length}", body.length <= ReminderFormatter.MAX_NOTIFICATION_BODY_CHARS)
        assertTrue("body should be truncated: $body", body.endsWith("…"))
        assertTrue(body.startsWith("ДЗ: "))
    }

    @Test
    fun notificationBodyIsEnglishWhenLocaleIsEnglish() {
        Locale.setDefault(Locale("en"))
        assertEquals("HW: Solve task 5", ReminderFormatter.notificationBody(reminder(noteText = "Solve task 5")))
    }

    // --- listTitle / listContent ---

    @Test
    fun listTitlePrefersSubject() {
        assertEquals("Математика", ReminderFormatter.listTitle(reminder()))
    }

    @Test
    fun listTitleFallsBackToLessonPair() {
        assertEquals("ПАРА 2 · 10:15 - 11:45", ReminderFormatter.listTitle(reminder(subject = "")))
    }

    @Test
    fun listContentReturnsNormalizedSnapshot() {
        assertEquals(
            "Решить задачу 5",
            ReminderFormatter.listContent(reminder(noteText = "  Решить   <i>задачу</i> 5 "))
        )
    }

    @Test
    fun listContentFallsBackWhenBlank() {
        assertEquals("домашнее задание", ReminderFormatter.listContent(reminder(noteText = "")))
    }

    // --- formatTrigger ---

    @Test
    fun formatTriggerUsesDayMonthYearAndMoscowTime() {
        val zone = ZoneId.of("Europe/Moscow")
        val trigger = ZonedDateTime.of(2026, 11, 12, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("12.11.2026 · 09:00", ReminderFormatter.formatTrigger(trigger, zone))
    }

    @Test
    fun formatTriggerRespectsProvidedZone() {
        val utc = ZoneId.of("UTC")
        val trigger = ZonedDateTime.of(2026, 1, 2, 3, 4, 0, 0, utc).toInstant().toEpochMilli()
        assertEquals("02.01.2026 · 03:04", ReminderFormatter.formatTrigger(trigger, utc))
    }

    // --- formatCountdown ---

    @Test
    fun countdownDaysAndHours() {
        val remaining = ((2 * 24 + 3) * 3_600_000L) + 45_000L
        assertEquals("2 д 3 ч", ReminderFormatter.formatCountdown(remaining))
    }

    @Test
    fun countdownHoursAndMinutes() {
        val remaining = (1 * 3_600_000L) + (15 * 60_000L)
        assertEquals("1 ч 15 мин", ReminderFormatter.formatCountdown(remaining))
    }

    @Test
    fun countdownMinutes() {
        assertEquals("15 мин", ReminderFormatter.formatCountdown(15 * 60_000L))
    }

    @Test
    fun countdownLessThanMinute() {
        assertEquals("< 1 мин", ReminderFormatter.formatCountdown(30_000L))
    }

    @Test
    fun countdownExpiredForZeroAndNegative() {
        assertEquals("время истекло", ReminderFormatter.formatCountdown(0L))
        assertEquals("время истекло", ReminderFormatter.formatCountdown(-5_000L))
    }

    @Test
    fun countdownIsEnglishWhenLocaleIsEnglish() {
        Locale.setDefault(Locale("en"))
        assertEquals("2d 3h", ReminderFormatter.formatCountdown(((2 * 24 + 3) * 3_600_000L)))
        assertEquals("15m", ReminderFormatter.formatCountdown(15 * 60_000L))
        assertEquals("< 1 min", ReminderFormatter.formatCountdown(30_000L))
        assertEquals("overdue", ReminderFormatter.formatCountdown(0L))
    }

    // --- normalizeNoteText ---

    @Test
    fun normalizeStripsHtmlEntitiesAndWhitespace() {
        assertEquals(
            "Привет мир тест",
            ReminderFormatter.normalizeNoteText("<b>Привет</b>&nbsp;мир\n\n   тест  ")
        )
    }

    @Test
    fun normalizeHandlesNullAndBlank() {
        assertEquals("", ReminderFormatter.normalizeNoteText(null))
        assertEquals("", ReminderFormatter.normalizeNoteText("   \n\t "))
    }

    // --- sanitizeNotificationText ---

    @Test
    fun sanitizeKeepsShortTextAsIs() {
        assertEquals("короткий текст", ReminderFormatter.sanitizeNotificationText("короткий текст"))
    }

    @Test
    fun sanitizeCollapsesNewlines() {
        assertEquals("строка один строка два", ReminderFormatter.sanitizeNotificationText("строка один\nстрока два"))
    }

    @Test
    fun sanitizeTruncatesAtWordBoundaryAndAddsEllipsis() {
        assertEquals("aaa bbb…", ReminderFormatter.sanitizeNotificationText("aaa bbb ccc ddd", maxChars = 12))
    }

    @Test
    fun sanitizeNeverExceedsMaxChars() {
        val text = (1..80).joinToString(" ") { "слово$it" }
        val result = ReminderFormatter.sanitizeNotificationText(text, maxChars = 40)
        assertTrue("length ${result.length}", result.length <= 40)
        assertTrue(result.endsWith("…"))
    }

    @Test
    fun sanitizeHandlesZeroBudget() {
        assertEquals("", ReminderFormatter.sanitizeNotificationText("текст", maxChars = 0))
    }
}
