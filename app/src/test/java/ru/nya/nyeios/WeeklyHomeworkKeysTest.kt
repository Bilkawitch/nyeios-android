package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ru.nya.nyeios.data.model.DaySchedule
import ru.nya.nyeios.data.model.LessonItem
import ru.nya.nyeios.data.model.LessonType
import ru.nya.nyeios.data.model.WeekSchedule
import ru.nya.nyeios.ui.notes.weekly.WeeklyHomeworkKeys

/**
 * Регрессия краша недельной ведомости ДЗ: `IllegalArgumentException: Key "..." was already used`.
 *
 * Портал отдаёт одну и ту же дисциплину в одной паре дважды (две подгруппы иностранного
 * языка с одинаковым названием предмета), а ключ элемента LazyColumn строился только из
 * даты, времени, номера пары и дисциплины — без преподавателя, аудитории и подгруппы.
 * При проматывании списка обе карточки попадали в композицию, и Compose валил процесс.
 */
class WeeklyHomeworkKeysTest {

    private fun lesson(
        subject: String = "Практический курс второго иностранного языка",
        subgroup: String = "",
        room: String = "425",
        teacher: String = "Чернякова Юлия Сергеевна",
        time: String = "08:30 - 10:00",
        lessonNumber: String = "1"
    ) = LessonItem(
        time = time,
        lessonNumber = lessonNumber,
        subgroup = subgroup,
        subject = subject,
        room = room,
        teacher = teacher,
        type = LessonType.PRACTICE
    )

    private fun day(
        dateString: String,
        dayTitle: String,
        lessons: List<LessonItem>
    ) = DaySchedule(
        dayTitle = dayTitle,
        dayName = "Чт",
        dateString = dateString,
        lessons = lessons
    )

    private fun week(days: List<DaySchedule>): WeekSchedule = WeekSchedule(
        weekTitle = "09.11.2026 — 15.11.2026",
        group = "23АН-о-41",
        startDate = "09.11.2026",
        endDate = "15.11.2026",
        offsetWeeks = 0,
        days = days
    )

    /** Повторяет порядок ключей, который LazyColumn ведомости ДЗ строит в рантайме. */
    private fun lazyColumnKeys(week: WeekSchedule): List<String> = buildList {
        add(WeeklyHomeworkKeys.week(week))
        week.days.forEach { day ->
            if (day.lessons.isEmpty()) return@forEach
            add(WeeklyHomeworkKeys.day(week, day))
            day.lessons.forEachIndexed { index, lesson ->
                add(WeeklyHomeworkKeys.lesson(week, day, lesson, index))
            }
        }
    }

    @Test
    fun lessonKeysAreUniqueWhenPortalReturnsSameSubjectTwiceInOnePair() {
        // Одна пара, две подгруппы, одинаковые дата/время/номер пары/дисциплина.
        // Именно такой набор данных пришёл в краше пользователя.
        val schedule = week(
            listOf(
                day(
                    dateString = "12.11",
                    dayTitle = "Четверг - 12.11.2026",
                    lessons = listOf(
                        lesson(subgroup = "Подгруппа 1", room = "425", teacher = "Чернякова Юлия Сергеевна"),
                        lesson(subgroup = "Подгруппа 2", room = "436", teacher = "Серебрякова Екатерина Владимировна")
                    )
                )
            )
        )

        val keys = lazyColumnKeys(schedule)
        val lessonKeys = schedule.days.flatMap { day ->
            day.lessons.mapIndexed { index, lesson ->
                WeeklyHomeworkKeys.lesson(schedule, day, lesson, index)
            }
        }

        assertEquals("Обе подгруппы должны отображаться отдельными карточками", 2, lessonKeys.size)
        assertEquals(
            "Ключи элементов LazyColumn обязаны быть уникальны, иначе Compose валит процесс",
            lessonKeys.size,
            lessonKeys.distinct().size
        )
        assertEquals(
            "Ключи всего списка ведомости обязаны быть уникальны",
            keys.size,
            keys.distinct().size
        )
    }

    @Test
    fun keysOfDifferentDaysAndWeeksStayDistinct() {
        val schedule = week(
            listOf(
                day("12.11", "Четверг - 12.11.2026", listOf(lesson())),
                day("13.11", "Пятница - 13.11.2026", listOf(lesson()))
            )
        )
        val dayKeys = schedule.days.map { WeeklyHomeworkKeys.day(schedule, it) }
        val lessonKeys = schedule.days.flatMap { day ->
            day.lessons.mapIndexed { index, lesson ->
                WeeklyHomeworkKeys.lesson(schedule, day, lesson, index)
            }
        }

        assertEquals("Ключи дней одной недели не должны совпадать", 2, dayKeys.distinct().size)
        assertEquals(
            "Одинаковые пары в разные дни должны иметь разные ключи",
            2,
            lessonKeys.distinct().size
        )

        val otherWeek = schedule.copy(startDate = "16.11.2026", endDate = "22.11.2026")
        val otherDay = schedule.days.first()
        assertNotEquals(
            "Одинаковая пара в разные недели должна иметь разные ключи",
            WeeklyHomeworkKeys.lesson(schedule, otherDay, otherDay.lessons.first(), 0),
            WeeklyHomeworkKeys.lesson(otherWeek, otherDay, otherDay.lessons.first(), 0)
        )
    }

    @Test
    fun everyLessonOfAWeekGetsAUniqueKey() {
        val schedule = week(
            listOf(
                day(
                    "09.11",
                    "Понедельник - 09.11.2026",
                    listOf(lesson(subject = "Лекция", lessonNumber = "1"), lesson(subject = "Лекция", lessonNumber = "2"))
                ),
                day(
                    "12.11",
                    "Четверг - 12.11.2026",
                    listOf(
                        lesson(subgroup = "Подгруппа 1"),
                        lesson(subgroup = "Подгруппа 2", teacher = "Серебрякова Екатерина Владимировна")
                    )
                )
            )
        )

        val keys = lazyColumnKeys(schedule)
        assertEquals("Ключ на каждый день и каждую пару", 1 + 3 + 3, keys.size)
        assertEquals(keys.size, keys.distinct().size)
    }
}
