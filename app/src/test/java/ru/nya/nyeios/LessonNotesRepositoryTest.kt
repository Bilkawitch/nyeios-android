package ru.nya.nyeios

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.notes.LessonNote
import ru.nya.nyeios.data.notes.LessonNotesRepository
import ru.nya.nyeios.ui.notes.editor.RichTextEditorState
import ru.nya.nyeios.ui.notes.editor.RichTextFormatter

class LessonNotesRepositoryTest {

    @Test
    fun testBuildKey_generatesConsistentAndUniqueKeys() {
        val key1 = LessonNotesRepository.buildKey(
            dateString = "20.10.2026",
            time = "13:50-15:28",
            lessonNumber = "1",
            subject = "Производственная практика (педагогическая практика)"
        )

        val key2 = LessonNotesRepository.buildKey(
            dateString = "27.10.2026", // Следующая неделя!
            time = "13:50-15:28",
            lessonNumber = "1",
            subject = "Производственная практика (педагогическая практика)"
        )

        val keySame = LessonNotesRepository.buildKey(
            dateString = "20.10.2026",
            time = "13:50-15:28",
            lessonNumber = "1",
            subject = "Производственная практика (педагогическая практика)"
        )

        assertEquals("Ключи для одинаковой даты и пары должны совпадать", key1, keySame)
        assertNotEquals("Ключи для разных дат должны быть изолированы", key1, key2)
        // Дата канонизируется до dd.MM (год отбрасывается), нормализация дисциплины
        // схлопывает пунктуацию и пробелы.
        assertTrue(key1.startsWith("20.10_1_13:50-15:28_"))
        assertTrue(key2.startsWith("27.10_1_13:50-15:28_"))
    }

    @Test
    fun testRichTextFormatter_htmlToAnnotatedString_parsesStyles() {
        val html = "Текст с <b>жирным</b>, <i>курсивом</i>, <u>подчеркиванием</u> и <mark>маркером</mark>."
        val annotated = RichTextFormatter.htmlToAnnotatedString(html)

        assertEquals(
            "Текст с жирным, курсивом, подчеркиванием и маркером.",
            annotated.text
        )

        // Проверяем наличие стилей
        val boldSpan = annotated.spanStyles.find { it.item.fontWeight == FontWeight.Bold }
        assertTrue("Должен присутствовать стиль Bold", boldSpan != null)
        assertEquals("жирным", annotated.text.substring(boldSpan!!.start, boldSpan.end))

        val italicSpan = annotated.spanStyles.find { it.item.fontStyle == FontStyle.Italic }
        assertTrue("Должен присутствовать стиль Italic", italicSpan != null)
        assertEquals("курсивом", annotated.text.substring(italicSpan!!.start, italicSpan.end))

        val underlineSpan = annotated.spanStyles.find { it.item.textDecoration == TextDecoration.Underline }
        assertTrue("Должен присутствовать стиль Underline", underlineSpan != null)
        assertEquals("подчеркиванием", annotated.text.substring(underlineSpan!!.start, underlineSpan.end))

        val highlightSpan = annotated.spanStyles.find { it.item.background != Color.Unspecified }
        assertTrue("Должен присутствовать стиль Highlight", highlightSpan != null)
        assertEquals("маркером", annotated.text.substring(highlightSpan!!.start, highlightSpan.end))
    }

    @Test
    fun testRichTextFormatter_roundtrip() {
        val originalHtml = "<b>Важное задание</b>: подготовить конспект <u>главы 4</u>"
        val annotated = RichTextFormatter.htmlToAnnotatedString(originalHtml)
        val serializedHtml = RichTextFormatter.annotatedStringToHtml(annotated)

        assertTrue(serializedHtml.contains("<b>Важное задание</b>"))
        assertTrue(serializedHtml.contains("<u>главы 4</u>"))
    }

    @Test
    fun testExtractPlainSnippet_cleansFormatting() {
        val raw = "<b>ДЗ на 20.10</b>:<br/>• пункт 1<br/>• пункт 2<br/><mark>до 18:00</mark>"
        val snippet = RichTextFormatter.extractPlainSnippet(raw, maxLength = 50)

        assertFalse("Не должно быть HTML тегов", snippet.contains("<") || snippet.contains(">"))
        assertFalse("Не должно быть переносов br", snippet.contains("br"))
        assertTrue("Должен содержать чистый текст", snippet.contains("ДЗ на 20.10"))
        assertTrue("Должен содержать пункт", snippet.contains("пункт 1"))
    }

    @Test
    fun testRichTextEditorState_listsAndUndo() {
        val state = RichTextEditorState(initialRawText = "Первая строка\nВторая строка")
        // Выделяем весь текст для форматирования списка
        val fullLen = state.textFieldValue.text.length
        state.textFieldValue = state.textFieldValue.copy(
            selection = androidx.compose.ui.text.TextRange(0, fullLen)
        )
        state.toggleBulletList()

        assertTrue("Должен добавиться маркер к первой строке", state.textFieldValue.text.contains("• Первая строка"))
        assertTrue("Должен добавиться маркер ко второй строке", state.textFieldValue.text.contains("• Вторая строка"))
        assertTrue("Должна быть доступна отмена", state.canUndo)

        state.undo()
        assertEquals("После отмены текст должен восстановиться", "Первая строка\nВторая строка", state.textFieldValue.text)
        assertTrue("Должен быть доступен повтор", state.canRedo)

        state.redo()
        assertTrue("После повтора маркеры должны вернуться", state.textFieldValue.text.contains("• Первая строка"))
        assertTrue("После повтора маркеры должны вернуться", state.textFieldValue.text.contains("• Вторая строка"))
    }

    @Test
    fun testComputeRelativeWeekOffset_pastPresentFuture() {
        val currentMonday = java.time.LocalDate.of(2026, 10, 19)

        assertEquals(0, LessonNotesRepository.computeRelativeWeekOffset("19.10.2026", currentMonday))
        assertEquals(-1, LessonNotesRepository.computeRelativeWeekOffset("12.10.2026", currentMonday))
        assertEquals(-2, LessonNotesRepository.computeRelativeWeekOffset("05.10.2026", currentMonday))
        assertEquals(-3, LessonNotesRepository.computeRelativeWeekOffset("28.09.2026", currentMonday))
        assertEquals(-7, LessonNotesRepository.computeRelativeWeekOffset("31.08.2026", currentMonday))

        assertEquals(1, LessonNotesRepository.computeRelativeWeekOffset("26.10.2026", currentMonday))
        assertEquals(2, LessonNotesRepository.computeRelativeWeekOffset("02.11.2026", currentMonday))
        assertEquals(6, LessonNotesRepository.computeRelativeWeekOffset("30.11.2026", currentMonday))
    }

    @Test
    fun testCalculateWeekItemIndex_respectsCollapsedSections() {
        val dummyLesson = ru.nya.nyeios.data.model.LessonItem(
            time = "10:10 - 11:40",
            lessonNumber = "2",
            subject = "Тестовая дисциплина",
            room = "432",
            teacher = "Иванов И.И.",
            type = ru.nya.nyeios.data.model.LessonType.LECTURE
        )

        val weekPast = ru.nya.nyeios.data.model.WeekSchedule(
            weekTitle = "12.10.2026 — 18.10.2026",
            group = "23АН-о-41",
            startDate = "12.10.2026",
            endDate = "18.10.2026",
            offsetWeeks = -1,
            days = listOf(
                ru.nya.nyeios.data.model.DaySchedule(
                    dayTitle = "Понедельник",
                    dayName = "Пн",
                    dateString = "12.10",
                    lessons = listOf(dummyLesson, dummyLesson)
                )
            )
        )

        val weekCurrent = ru.nya.nyeios.data.model.WeekSchedule(
            weekTitle = "19.10.2026 — 25.10.2026",
            group = "23АН-о-41",
            startDate = "19.10.2026",
            endDate = "25.10.2026",
            offsetWeeks = 0,
            days = listOf(
                ru.nya.nyeios.data.model.DaySchedule(
                    dayTitle = "Вторник",
                    dayName = "Вт",
                    dateString = "20.10",
                    lessons = listOf(dummyLesson)
                )
            )
        )

        val weekFuture = ru.nya.nyeios.data.model.WeekSchedule(
            weekTitle = "26.10.2026 — 01.11.2026",
            group = "23АН-о-41",
            startDate = "26.10.2026",
            endDate = "01.11.2026",
            offsetWeeks = 1,
            days = emptyList()
        )

        val allWeeks = listOf(weekPast, weekCurrent, weekFuture)

        // 1. Когда все недели кроме текущей свернуты:
        // Index 0: WeekDividerCard(weekPast)
        // Index 1: WeekDividerCard(weekCurrent)
        // Index 2: DayHeader(weekCurrent)
        // Index 3: LessonCard(weekCurrent)
        // Index 4: WeekDividerCard(weekFuture)
        val expandedCurrentOnly = setOf("19.10.2026")

        val indexPast = LessonNotesRepository.calculateWeekItemIndex("12.10.2026", allWeeks, expandedCurrentOnly)
        val indexCurrent = LessonNotesRepository.calculateWeekItemIndex("19.10.2026", allWeeks, expandedCurrentOnly)
        val indexFuture = LessonNotesRepository.calculateWeekItemIndex("26.10.2026", allWeeks, expandedCurrentOnly)

        assertEquals(0, indexPast)
        assertEquals(1, indexCurrent)
        assertEquals(4, indexFuture)

        // 2. Если развернута и прошлая неделя:
        // Index 0: WeekDividerCard(weekPast)
        // Index 1: DayHeader(weekPast)
        // Index 2: LessonCard 1 (weekPast)
        // Index 3: LessonCard 2 (weekPast)
        // Index 4: WeekDividerCard(weekCurrent)
        val expandedBoth = setOf("12.10.2026", "19.10.2026")
        val indexCurrentShifted = LessonNotesRepository.calculateWeekItemIndex("19.10.2026", allWeeks, expandedBoth)
        assertEquals(4, indexCurrentShifted)
    }
}
