package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.notes.LessonNoteKey

class LessonNoteKeyTest {

    @Test
    fun canonicalDate_reducesToDayMonth() {
        assertEquals("27.10", LessonNoteKey.canonicalDate("27.10"))
        assertEquals("27.10", LessonNoteKey.canonicalDate("27.10.2026"))
        assertEquals("27.10", LessonNoteKey.canonicalDate("27/10/2026"))
        assertEquals("27.10", LessonNoteKey.canonicalDate("27-10-2026"))
        assertEquals("27.10", LessonNoteKey.canonicalDate(" 27.10.2026 "))
        assertEquals("07.09", LessonNoteKey.canonicalDate("7.9"))
        assertEquals("27.10", LessonNoteKey.canonicalDate("2026-10-27"))
    }

    @Test
    fun canonicalDate_keepsUnparseableValueAndEmpty() {
        assertEquals("", LessonNoteKey.canonicalDate(""))
        assertEquals("", LessonNoteKey.canonicalDate("   "))
        assertEquals("сегодня", LessonNoteKey.canonicalDate("сегодня"))
    }

    @Test
    fun canonicalTime_normalizesSpacingAndDashes() {
        assertEquals("10:10-11:40", LessonNoteKey.canonicalTime("10:10 - 11:40"))
        assertEquals("10:10-11:40", LessonNoteKey.canonicalTime(" 10:10-11:40 "))
        assertEquals("10:10-11:40", LessonNoteKey.canonicalTime("10:10\u201311:40"))
        assertEquals("10:10-11:40", LessonNoteKey.canonicalTime("10:10 \u2014 11:40"))
        assertEquals("13:50-15:20", LessonNoteKey.canonicalTime("13:50\u00A0-\u00A015:20"))
    }

    @Test
    fun canonicalSubject_casefoldsYoAndPunctuation() {
        assertEquals(
            "физическая_культура_и_спорт",
            LessonNoteKey.canonicalSubject("Физическая культура и спорт")
        )
        assertEquals(
            "элективные_дисциплины_по_физической_куль",
            LessonNoteKey.canonicalSubject("Элективные дисциплины по физической культуре")
        )
        assertEquals(
            LessonNoteKey.canonicalSubject("Всё о чёте"),
            LessonNoteKey.canonicalSubject("Все о чете")
        )
        assertEquals(
            LessonNoteKey.canonicalSubject("Пакеты прикладных программ (современного учителя)"),
            LessonNoteKey.canonicalSubject("пакеты   прикладных программ, современного учителя!")
        )
        assertEquals(
            LessonNoteKey.canonicalSubject("Производственная практика (педагогическая практика)"),
            LessonNoteKey.canonicalSubject("ПРОИЗВОДСТВЕННАЯ ПРАКТИКА (ПЕДАГОГИЧЕСКАЯ ПРАКТИКА)")
        )
    }

    @Test
    fun canonicalSubject_truncatesToFortyCharacters() {
        val long = "Очень длинное название дисциплины, которое заведомо длиннее сорока символов"
        assertTrue(LessonNoteKey.canonicalSubject(long).length <= 40)
    }

    @Test
    fun buildKey_cacheAndSanitizedSourcesProduceSameKey() {
        // Сырой кэш старого формата: пара записана как "10", дата с годом, время с пробелами.
        val fromRawCache = LessonNoteKey.buildKey(
            dateString = "20.10.2026",
            time = "10:10 - 11:40",
            lessonNumber = "10",
            subject = "Пакеты прикладных программ современного учителя"
        )
        // Санитизированное боевое расписание: номер пары пересчитан из времени, дата без года.
        val fromLiveSchedule = LessonNoteKey.buildKey(
            dateString = "20.10",
            time = "10:10-11:40",
            lessonNumber = "2",
            subject = "пакеты прикладных программ современного учителя"
        )
        assertEquals(fromRawCache, fromLiveSchedule)
    }

    @Test
    fun buildKey_missingLessonNumberUsesTimeGrid() {
        val inferred = LessonNoteKey.buildKey(
            dateString = "22.10",
            time = "12:10 - 13:40",
            lessonNumber = "",
            subject = "Аналитическая химия"
        )
        val explicit = LessonNoteKey.buildKey(
            dateString = "22.10",
            time = "12:10 - 13:40",
            lessonNumber = "3",
            subject = "Аналитическая химия"
        )
        assertEquals(explicit, inferred)
    }

    @Test
    fun buildKey_blankFieldsStayDeterministic() {
        val first = LessonNoteKey.buildKey("", "", "", "Аналитическая химия")
        val second = LessonNoteKey.buildKey("", "", "", "Аналитическая химия")
        assertEquals(first, second)
        assertEquals("_0__аналитическая_химия", first)
    }

    @Test
    fun buildKey_realisticTimetableHasNoCrossLessonCollisions() {
        // Реалистичные пары из отчёта пользователя: разные дни/пары/дисциплины.
        data class Row(val date: String, val time: String, val number: String, val subject: String)

        val rows = listOf(
            Row("27.10", "10:10 - 11:40", "2", "Пакеты прикладных программ современного учителя"),
            Row("27.10", "12:10 - 13:40", "3", "Элективные дисциплины по физической культуре и спорту (элективные дисциплины)"),
            Row("29.10", "10:10 - 11:40", "2", "Аналитическая химия"),
            Row("21.10", "08:30 - 10:00", "1", "Теория и методика обучения немецкому языку"),
            Row("20.10", "13:50 - 15:20", "4", "Производственная практика (педагогическая практика)"),
            Row("20.10", "", "", "Внеучебное мероприятие"),
            Row("", "10:10 - 11:40", "2", "Дисциплина без даты"),
            Row("27.10", "10:10 - 11:40", "2", "Всё о чёте")
        )

        val keys = rows.map { LessonNoteKey.buildKey(it.date, it.time, it.number, it.subject) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun buildKey_normalizedSubjectVariantsOfSamePairShareKey() {
        assertEquals(
            LessonNoteKey.buildKey("21.10", "08:30 - 10:00", "1", "Всё о чёте"),
            LessonNoteKey.buildKey("21.10", "08:30 - 10:00", "1", "ВСЕ О ЧЕТЕ")
        )
    }

    @Test
    fun buildKey_differentLessonNumberOrTimeOrSubjectProducesDifferentKey() {
        val base = LessonNoteKey.buildKey("20.10", "10:10 - 11:40", "2", "Аналитическая химия")
        val otherNumber = LessonNoteKey.buildKey("20.10", "10:10 - 11:40", "3", "Аналитическая химия")
        val otherTime = LessonNoteKey.buildKey("20.10", "12:10 - 13:40", "2", "Аналитическая химия")
        val otherSubject = LessonNoteKey.buildKey("20.10", "10:10 - 11:40", "2", "Физическая культура")
        val otherDate = LessonNoteKey.buildKey("21.10", "10:10 - 11:40", "2", "Аналитическая химия")
        assertNotEquals(base, otherNumber)
        assertNotEquals(base, otherTime)
        assertNotEquals(base, otherSubject)
        assertNotEquals(base, otherDate)
    }
}
