package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.changes.ChangesDiff
import ru.nya.nyeios.data.changes.LessonKeys
import ru.nya.nyeios.data.changes.ScheduleChanges
import ru.nya.nyeios.data.changes.WeekChanges
import ru.nya.nyeios.data.model.DaySchedule
import ru.nya.nyeios.data.model.LessonItem
import ru.nya.nyeios.data.model.LessonType
import ru.nya.nyeios.data.model.WeekSchedule

/**
 * JVM-проверки diff-движка расписания ([ChangesDiff]): добавление/изменение/удаление пар,
 * нормализация номера пары, ключи с подгруппой, изоляция недель и дневные пометки.
 */
class ScheduleDiffTest {

    private val weekStart = "15.09.2026"
    private val weekEnd = "21.09.2026"
    private val weekKey = "${weekStart}_$weekEnd"

    private fun lesson(
        time: String = "08:30 - 10:00",
        lessonNumber: String = "1",
        subgroup: String = "",
        subject: String = "Математика",
        room: String = "101",
        teacher: String = "Иванов И.И.",
        type: LessonType = LessonType.LECTURE
    ) = LessonItem(
        time = time,
        lessonNumber = lessonNumber,
        subgroup = subgroup,
        subject = subject,
        room = room,
        teacher = teacher,
        type = type
    )

    private fun day(
        date: String = "15.09",
        name: String = "Пн",
        lessons: List<LessonItem> = listOf(lesson())
    ) = DaySchedule(
        dayTitle = "$name $date",
        dayName = name,
        dateString = date,
        isToday = false,
        lessons = lessons
    )

    private fun week(
        days: List<DaySchedule> = listOf(day()),
        start: String = weekStart,
        end: String = weekEnd
    ) = WeekSchedule(
        weekTitle = "Расписание",
        group = "23ан-о-41",
        startDate = start,
        endDate = end,
        offsetWeeks = 0,
        days = days
    )

    private fun snapshotOf(days: List<DaySchedule> = listOf(day())) =
        ChangesDiff.scheduleSnapshot(week(days))

    private fun diff(
        baselineDays: List<DaySchedule>,
        latestDays: List<DaySchedule>
    ): Map<String, WeekChanges> = ChangesDiff.scheduleChanges(
        ChangesDiff.scheduleSnapshot(week(baselineDays)),
        ChangesDiff.scheduleSnapshot(week(latestDays))
    )

    private fun snapshotOfDays(days: List<DaySchedule>) = ChangesDiff.scheduleSnapshot(week(days))

    // --- snapshot conversion -------------------------------------------------

    @Test
    fun snapshotKeepsWeekAndDayFields() {
        val snapshot = snapshotOf()
        val lessons = snapshot.weeks.getValue(weekKey)

        assertEquals(1, lessons.size)
        val item = lessons.single()
        assertEquals(weekKey, item.weekKey)
        assertEquals("15.09", item.dateString)
        assertEquals("Пн", item.dayName)
        assertEquals("1", item.lessonNumber)
        assertEquals("08:30 - 10:00", item.time)
        assertEquals("Математика", item.subject)
        assertEquals("101", item.room)
        assertEquals("Иванов И.И.", item.teacher)
        assertEquals(LessonType.LECTURE.name, item.type)
    }

    @Test
    fun snapshotNormalizesEmptyLessonNumberFromTime() {
        val snapshot = snapshotOf(listOf(day(lessons = listOf(lesson(time = "10:15 - 11:45", lessonNumber = "")))))

        assertEquals("2", snapshot.weeks.getValue(weekKey).single().lessonNumber)
    }

    @Test
    fun snapshotKeepsAlreadyValidLessonNumber() {
        val snapshot = snapshotOf(listOf(day(lessons = listOf(lesson(time = "08:30 - 10:00", lessonNumber = "4")))))

        assertEquals("4", snapshot.weeks.getValue(weekKey).single().lessonNumber)
    }

    @Test
    fun emptyLessonNumbersInSameDayDoNotCollapseDifferentLessons() {
        val lessons = listOf(
            lesson(time = "08:30 - 10:00", lessonNumber = "", subject = "Математика"),
            lesson(time = "10:15 - 11:45", lessonNumber = "", subject = "Физика", room = "202")
        )
        val normalized = snapshotOf(listOf(day(lessons = lessons))).weeks.getValue(weekKey)

        assertEquals(2, normalized.size)
        assertEquals(setOf("1", "2"), normalized.map { it.lessonNumber }.toSet())
        assertEquals(
            2,
            normalized.map { LessonKeys.of(it.dateString, it.lessonNumber, it.subgroup) }.toSet().size
        )
    }

    // --- diff basics ---------------------------------------------------------

    @Test
    fun firstLoadWithoutBaselineProducesNoChanges() {
        assertTrue(ChangesDiff.scheduleChanges(null, snapshotOf()).isEmpty())
    }

    @Test
    fun identicalSnapshotsProduceNoChanges() {
        val snapshot = snapshotOf()
        assertTrue(ChangesDiff.scheduleChanges(snapshot, snapshot).isEmpty())
    }

    @Test
    fun addedLessonIsReported() {
        val existing = lesson()
        val added = lesson(time = "10:15 - 11:45", lessonNumber = "2", subject = "Физика", room = "202")
        val changes = diff(listOf(day(lessons = listOf(existing))), listOf(day(lessons = listOf(existing, added))))

        val week = changes.getValue(weekKey)
        assertEquals(1, week.added.size)
        assertEquals("Физика", week.added.single().subject)
        assertEquals(0, week.modified.size)
        assertEquals(0, week.removed.size)
        assertTrue(week.hasChanges)
        assertTrue(week.isLessonMarked("15.09", "2", ""))
    }

    @Test
    fun modifiedLessonIsReportedWithLatestValues() {
        val before = lesson()
        val after = before.copy(room = "305")
        val changes = diff(listOf(day(lessons = listOf(before))), listOf(day(lessons = listOf(after))))

        val week = changes.getValue(weekKey)
        assertEquals(0, week.added.size)
        assertEquals(1, week.modified.size)
        assertEquals("305", week.modified.single().room)
        assertEquals(0, week.removed.size)
        assertTrue(week.isLessonMarked("15.09", "1", ""))
    }

    @Test
    fun anyComparableFieldChangeMarksLessonModified() {
        val base = lesson()
        val variants = listOf(
            base.copy(time = "08:30 - 09:55"),
            base.copy(subject = "Алгебра"),
            base.copy(room = "202"),
            base.copy(teacher = "Петров П.П."),
            base.copy(type = LessonType.PRACTICE)
        )

        variants.forEach { latest ->
            val changes = diff(listOf(day(lessons = listOf(base))), listOf(day(lessons = listOf(latest))))
            val week = changes.getValue(weekKey)
            assertEquals("variant must be MODIFIED: $latest", 1, week.modified.size)
            assertEquals(0, week.added.size)
            assertEquals(0, week.removed.size)
        }
    }

    @Test
    fun removedLessonIsReportedFromBaselineValues() {
        val kept = lesson()
        val removed = lesson(time = "10:15 - 11:45", lessonNumber = "2", subject = "Физика")
        val changes = diff(
            listOf(day(lessons = listOf(kept, removed))),
            listOf(day(lessons = listOf(kept)))
        )

        val week = changes.getValue(weekKey)
        assertEquals(1, week.removed.size)
        assertEquals("Физика", week.removed.single().subject)
        assertEquals(0, week.modified.size)
        assertEquals(0, week.added.size)
        assertFalse(week.isLessonMarked("15.09", "2", ""))
        assertTrue(week.isDayMarked("15.09"))
    }

    @Test
    fun subgroupIsPartOfLessonKey() {
        val first = lesson(subgroup = "1 п/г")
        val second = lesson(subgroup = "2 п/г", subject = "Физика")
        val changes = diff(
            listOf(day(lessons = listOf(first))),
            listOf(day(lessons = listOf(first, second)))
        )

        val week = changes.getValue(weekKey)
        assertEquals(1, week.added.size)
        assertEquals("2 п/г", week.added.single().subgroup)
        assertTrue(week.isLessonMarked("15.09", "1", "2 п/г"))
        assertFalse(week.isLessonMarked("15.09", "1", "1 п/г"))
    }

    @Test
    fun weeksDoNotMixChanges() {
        val otherStart = "22.09.2026"
        val otherEnd = "28.09.2026"
        val otherKey = "${otherStart}_$otherEnd"

        val baseline = snapshotOfDays(listOf(day()))
        val otherDay = day(date = "22.09", name = "Пн")
        val latest = ChangesDiff.ScheduleSnapshot(
            snapshotOfDays(
                listOf(
                    day(
                        lessons = listOf(
                            lesson(),
                            lesson(lessonNumber = "2", time = "10:15 - 11:45", subject = "Физика")
                        )
                    )
                )
            ).weeks + ChangesDiff
                .scheduleSnapshot(week(days = listOf(otherDay), start = otherStart, end = otherEnd))
                .weeks
        )

        val changes = ChangesDiff.scheduleChanges(baseline, latest)

        assertEquals(setOf(weekKey), changes.keys)
        assertNull(changes[otherKey])
    }

    @Test
    fun onlyDaysWithChangesAreMarked() {
        val mondayBefore = day("15.09", "Пн", listOf(lesson()))
        val tuesdayBefore = day("16.09", "Вт", listOf(lesson(time = "10:15 - 11:45", lessonNumber = "2", subject = "Физика")))
        val tuesdayAfter = tuesdayBefore.copy(lessons = listOf(lesson(time = "10:15 - 11:45", lessonNumber = "2", subject = "История")))

        val changes = diff(
            listOf(mondayBefore, tuesdayBefore),
            listOf(mondayBefore, tuesdayAfter)
        )

        val week = changes.getValue(weekKey)
        assertTrue(week.isDayMarked("16.09"))
        assertFalse(week.isDayMarked("15.09"))
        assertEquals(setOf("16.09"), week.changedDayDates)
    }

    @Test
    fun markedLessonKeysContainAddedAndModifiedButNotRemoved() {
        val kept = lesson()
        val modified = kept.copy(room = "305")
        val added = lesson(time = "10:15 - 11:45", lessonNumber = "2", subject = "Физика")
        val removed = lesson(time = "12:15 - 13:45", lessonNumber = "3", subject = "История")

        val changes = diff(
            listOf(day(lessons = listOf(kept, removed))),
            listOf(day(lessons = listOf(modified, added)))
        )

        val week = changes.getValue(weekKey)
        val expected = setOf(
            LessonKeys.of("15.09", "1", ""),
            LessonKeys.of("15.09", "2", "")
        )
        assertEquals(expected, week.markedLessonKeys)
        assertTrue(week.isLessonMarked("15.09", "1", ""))
        assertTrue(week.isLessonMarked("15.09", "2", ""))
        assertFalse(week.isLessonMarked("15.09", "3", ""))
    }

    // --- aggregate helpers ---------------------------------------------------

    @Test
    fun scheduleChangesAggregatesWeekHelpers() {
        val changes = diff(
            listOf(day(lessons = emptyList())),
            listOf(day(lessons = listOf(lesson())))
        )
        val scheduleChanges = ScheduleChanges(changes)

        val week = scheduleChanges.forWeek(weekStart, weekEnd)
        assertEquals(changes.getValue(weekKey), week)
        assertTrue(scheduleChanges.isWeekMarked(weekStart, weekEnd))
        assertEquals(1, scheduleChanges.totalChanges)
        assertTrue(scheduleChanges.hasChanges)
        assertNull(scheduleChanges.forWeek("01.01.2026", "07.01.2026"))
        assertFalse(scheduleChanges.isWeekMarked("01.01.2026", "07.01.2026"))
    }

    @Test
    fun emptyScheduleChangesHasNoChanges() {
        val changeless = ScheduleChanges()
        assertFalse(changeless.hasChanges)
        assertEquals(0, changeless.totalChanges)
        assertTrue(changeless.weeks.isEmpty())
    }

    @Test
    fun weekChangesCountsAndEmptyState() {
        val empty = WeekChanges(weekKey)
        assertFalse(empty.hasChanges)
        assertTrue(empty.markedLessonKeys.isEmpty())
        assertTrue(empty.changedDayDates.isEmpty())

        val changes = diff(
            listOf(day(lessons = listOf(lesson()))),
            listOf(day(lessons = listOf(lesson(room = "305"))))
        )
        val week = changes.getValue(weekKey)
        assertTrue(week.hasChanges)
        assertFalse(week.markedLessonKeys.isEmpty())
    }

    @Test
    fun lessonKeysFormatIsDateNumberSubgroup() {
        assertEquals("15.09|2|1 п/г", LessonKeys.of("15.09", "2", "1 п/г"))
    }
}
