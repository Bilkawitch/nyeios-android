package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.model.DaySchedule
import ru.nya.nyeios.data.model.LessonItem
import ru.nya.nyeios.data.model.LessonType
import ru.nya.nyeios.ui.schedule.DayRowCell
import ru.nya.nyeios.ui.schedule.DayRowCells

/**
 * Ряд дней недели: воскресенье никогда не содержит пар, поэтому его ячейка
 * заменяется действием «напоминания» в той же позиции (1/7 ширины ряда).
 * Индексы исходного списка дней обязаны сохраняться — на них завязан onSelectDay.
 */
class DayRowCellsTest {

    private fun day(
        dayName: String,
        dayTitle: String = dayName,
        dateString: String = "01.09.2026",
        lessons: List<LessonItem> = emptyList()
    ) = DaySchedule(
        dayTitle = dayTitle,
        dayName = dayName,
        dateString = dateString,
        lessons = lessons
    )

    private fun lesson(subject: String = "Математика") = LessonItem(
        time = "08:30 - 10:00",
        lessonNumber = "1",
        subject = subject,
        room = "425",
        teacher = "Иванов И.И.",
        type = LessonType.LECTURE
    )

    private val fullWeek = listOf(
        day("Пн", "Понедельник - 01.09.2026", "01.09.2026"),
        day("Вт", "Вторник - 02.09.2026", "02.09.2026"),
        day("Ср", "Среда - 03.09.2026", "03.09.2026"),
        day("Чт", "Четверг - 04.09.2026", "04.09.2026"),
        day("Пт", "Пятница - 05.09.2026", "05.09.2026"),
        day("Сб", "Суббота - 06.09.2026", "06.09.2026"),
        day("Вс", "Воскресенье - 07.09.2026", "07.09.2026")
    )

    private fun dayCells(cells: List<DayRowCell>): List<DayRowCell.Day> =
        cells.filterIsInstance<DayRowCell.Day>()

    @Test
    fun fullWeekReplacesSundayCellWithActionInTheSameSlot() {
        val cells = DayRowCells.build(fullWeek)

        assertEquals("Ряд остаётся семиячеечным", 7, cells.size)
        assertEquals(
            "Пн..Сб сохраняют исходные индексы 0..5",
            (0..5).toList(),
            dayCells(cells).map { it.originalIndex }
        )
        assertTrue("Воскресенье заменено действием напоминаний", cells[6] is DayRowCell.RemindersAction)
        assertEquals("Порядок дней не изменился", "Пн", dayCells(cells)[0].day.dayName)
    }

    @Test
    fun sixDayWeekAppendsActionCell() {
        val sixDays = fullWeek.take(6)
        val cells = DayRowCells.build(sixDays)

        assertEquals("Шесть дней + ячейка действия", 7, cells.size)
        assertEquals((0..5).toList(), dayCells(cells).map { it.originalIndex })
        assertTrue("Действие добавлено в конец ряда", cells.last() is DayRowCell.RemindersAction)
    }

    @Test
    fun listWithoutSundayAppendsActionCell() {
        val cells = DayRowCells.build(fullWeek.take(3))

        assertEquals(4, cells.size)
        assertEquals((0..2).toList(), dayCells(cells).map { it.originalIndex })
        assertTrue(cells.last() is DayRowCell.RemindersAction)
    }

    @Test
    fun sundayIsDetectedByShortRussianNameRegardlessOfCase() {
        val cells = DayRowCells.build(listOf(day("Пн"), day("вс")))

        assertEquals(2, cells.size)
        assertTrue(cells[1] is DayRowCell.RemindersAction)
    }

    @Test
    fun sundayIsDetectedByFullRussianTitle() {
        val cells = DayRowCells.build(
            listOf(day("Пн", "Понедельник - 01.09.2026"), day("?", "воскресенье - 07.09.2026"))
        )

        assertEquals(2, cells.size)
        assertTrue(cells[1] is DayRowCell.RemindersAction)
    }

    @Test
    fun sundayIsDetectedByEnglishNameAndTitle() {
        val byShortName = DayRowCells.build(listOf(day("MON"), day("SUN")))
        assertTrue(byShortName.last() is DayRowCell.RemindersAction)

        val byTitle = DayRowCells.build(listOf(day("MON"), day("?", "Sunday - 07.09.2026")))
        assertTrue(byTitle.last() is DayRowCell.RemindersAction)
    }

    @Test
    fun sundayInTheMiddleIsReplacedInPlaceAndIndexesStayIntact() {
        // Портал теоретически может отдать дни в произвольном порядке.
        val cells = DayRowCells.build(
            listOf(day("Сб", "Суббота - 06.09.2026"), day("Вс", "Воскресенье - 07.09.2026"), day("Пн", "Понедельник - 08.09.2026"))
        )

        assertEquals(3, cells.size)
        assertTrue(cells[1] is DayRowCell.RemindersAction)
        assertEquals(0, dayCells(cells)[0].originalIndex)
        assertEquals("Индекс дня после воскресенья не сдвигается", 2, dayCells(cells)[1].originalIndex)
    }

    @Test
    fun singleSundayDayProducesOnlyActionCell() {
        val cells = DayRowCells.build(listOf(day("Вс", "Воскресенье - 07.09.2026")))

        assertEquals(1, cells.size)
        assertTrue(cells.single() is DayRowCell.RemindersAction)
    }

    @Test
    fun singleNonSundayDayProducesDayAndAction() {
        val monday = day("Пн", "Понедельник - 01.09.2026")
        val cells = DayRowCells.build(listOf(monday))

        assertEquals(2, cells.size)
        assertEquals(0, dayCells(cells).single().originalIndex)
        assertSame("Данные дня передаются без копирования", monday, dayCells(cells).single().day)
        assertTrue(cells.last() is DayRowCell.RemindersAction)
    }

    @Test
    fun emptyListProducesNoCells() {
        assertTrue(DayRowCells.build(emptyList()).isEmpty())
    }

    @Test
    fun actionCellAppearsExactlyOnce() {
        val cells = DayRowCells.build(fullWeek)
        assertEquals(1, cells.count { it is DayRowCell.RemindersAction })
    }

    @Test
    fun dayPayloadAndLessonsArePreserved() {
        val busyThursday = day(
            "Чт",
            "Четверг - 04.09.2026",
            "04.09.2026",
            lessons = listOf(lesson("Физика"), lesson("Химия"))
        )
        val cells = DayRowCells.build(listOf(busyThursday))
        val cell = dayCells(cells).single()

        assertEquals("Чт", cell.day.dayName)
        assertEquals(2, cell.day.lessons.size)
        assertEquals("Физика", cell.day.lessons.first().subject)
    }
}
