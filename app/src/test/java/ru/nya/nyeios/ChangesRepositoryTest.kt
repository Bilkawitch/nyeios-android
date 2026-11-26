package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.nya.nyeios.data.changes.ChangePrefs
import ru.nya.nyeios.data.changes.ChangesRepository
import ru.nya.nyeios.data.changes.ChangesStore
import ru.nya.nyeios.data.model.CurriculumSubject
import ru.nya.nyeios.data.model.CurriculumTerm
import ru.nya.nyeios.data.model.DaySchedule
import ru.nya.nyeios.data.model.LessonItem
import ru.nya.nyeios.data.model.LessonType
import ru.nya.nyeios.data.model.WeekSchedule

/**
 * JVM-проверки фасада [ChangesRepository] на инъецированном [ChangesStore]:
 * кумулятивная дельта от baseline, первая молчаливая загрузка, независимость недель/семестров,
 * markSeen-операции, reset и потокобезопасность.
 */
class ChangesRepositoryTest {

    private class FakePrefs : ChangePrefs {
        val values = LinkedHashMap<String, String>()
        override fun getString(key: String): String? = values[key]
        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }
    }

    private lateinit var prefs: FakePrefs
    private lateinit var store: ChangesStore

    private val week1Start = "15.09.2026"
    private val week1End = "21.09.2026"
    private val week2Start = "22.09.2026"
    private val week2End = "28.09.2026"

    @Before
    fun setUp() {
        prefs = FakePrefs()
        store = ChangesStore(prefs)
    }

    private fun repo() = ChangesRepository.forTesting(store)

    // --- builders ------------------------------------------------------------

    private fun lesson(
        lessonNumber: String = "1",
        subject: String = "Математика",
        time: String = "08:30 - 10:00",
        room: String = "101"
    ) = LessonItem(
        time = time,
        lessonNumber = lessonNumber,
        subgroup = "",
        subject = subject,
        room = room,
        teacher = "Иванов И.И.",
        type = LessonType.LECTURE
    )

    private fun week(
        start: String = week1Start,
        end: String = week1End,
        lessons: List<LessonItem>
    ) = WeekSchedule(
        weekTitle = "Расписание",
        group = "23ан-о-41",
        startDate = start,
        endDate = end,
        offsetWeeks = 0,
        days = listOf(
            DaySchedule(
                dayTitle = "Пн",
                dayName = "Пн",
                dateString = "15.09",
                isToday = false,
                lessons = lessons
            )
        )
    )

    private fun week1(vararg lessons: LessonItem) = week(week1Start, week1End, lessons.toList())

    private fun week2(vararg lessons: LessonItem) = week(week2Start, week2End, lessons.toList())

    private fun subject(
        subject: String = "Математика",
        controlType: String = "Экзамен",
        currentScore: String = "5",
        termRating: String = ""
    ) = CurriculumSubject(
        subject = subject,
        controlType = controlType,
        currentScore = currentScore,
        termRating = termRating
    )

    private fun term(termNum: Int = 1, vararg subjects: CurriculumSubject) = CurriculumTerm(
        termNum = termNum,
        termTitle = "Семестр $termNum",
        subjects = subjects.toList()
    )

    // --- schedule ------------------------------------------------------------

    @Test
    fun firstScheduleFetchIsSilentAndSavesBaseline() {
        val repository = repo()

        repository.onScheduleFetched(week1(lesson()))

        assertFalse(repository.scheduleChanges.value.hasChanges)
        assertNotNull(store.loadScheduleBaseline())
        assertNotNull(store.loadScheduleLatest())
        assertEquals(1, store.loadScheduleBaseline()!!.weeks.getValue("${week1Start}_$week1End").size)
    }

    @Test
    fun addedLessonAppearsAfterSecondFetch() {
        val repository = repo()
        repository.onScheduleFetched(week1(lesson()))

        repository.onScheduleFetched(
            week1(lesson(), lesson(lessonNumber = "2", subject = "Физика", time = "10:15 - 11:45", room = "202"))
        )

        val weekChanges = repository.scheduleChanges.value.forWeek(week1Start, week1End)
        assertNotNull(weekChanges)
        assertEquals(1, weekChanges!!.added.size)
        assertEquals("Физика", weekChanges.added.single().subject)
        assertTrue(repository.scheduleChanges.value.isWeekMarked(week1Start, week1End))
    }

    @Test
    fun scheduleDiffIsCumulativeFromBaseline() {
        val repository = repo()
        val baselineLessons = listOf(lesson())
        repository.onScheduleFetched(week1(*baselineLessons.toTypedArray()))
        assertFalse(repository.scheduleChanges.value.hasChanges)

        repository.onScheduleFetched(
            week1(*baselineLessons.toTypedArray(), lesson(lessonNumber = "2", subject = "Физика", time = "10:15 - 11:45"))
        )
        assertEquals(
            1,
            repository.scheduleChanges.value.forWeek(week1Start, week1End)!!.added.size
        )

        // Третья загрузка: diff всё ещё считается от исходного baseline, а не от прошлого latest.
        repository.onScheduleFetched(
            week1(
                *baselineLessons.toTypedArray(),
                lesson(lessonNumber = "2", subject = "Физика", time = "10:15 - 11:45"),
                lesson(lessonNumber = "3", subject = "История", time = "12:15 - 13:45")
            )
        )

        val weekChanges = repository.scheduleChanges.value.forWeek(week1Start, week1End)!!
        assertEquals(2, weekChanges.added.size)
        assertEquals(setOf("2", "3"), weekChanges.added.map { it.lessonNumber }.toSet())
        assertTrue(weekChanges.modified.isEmpty())
        assertTrue(weekChanges.removed.isEmpty())
    }

    @Test
    fun differentWeeksDoNotOverwriteChanges() {
        val repository = repo()
        repository.onScheduleFetched(week1(lesson()))
        repository.onScheduleFetched(
            week1(lesson(), lesson(lessonNumber = "2", subject = "Физика", time = "10:15 - 11:45"))
        )
        assertTrue(repository.scheduleChanges.value.isWeekMarked(week1Start, week1End))

        // Загрузка другой недели не должна затирать изменения первой.
        repository.onScheduleFetched(week2(lesson(lessonNumber = "1", subject = "Право")))

        assertEquals(1, repository.scheduleChanges.value.forWeek(week1Start, week1End)!!.added.size)
        assertFalse(repository.scheduleChanges.value.isWeekMarked(week2Start, week2End))
        assertNotNull(store.loadScheduleLatest()!!.weeks["${week2Start}_$week2End"])
    }

    @Test
    fun weekMissingFromBaselineIsSilentlyAdded() {
        val repository = repo()
        repository.onScheduleFetched(week1(lesson()))

        repository.onScheduleFetched(week2(lesson(lessonNumber = "1", subject = "Право")))

        assertFalse(repository.scheduleChanges.value.hasChanges)
        assertNotNull(store.loadScheduleBaseline()!!.weeks["${week2Start}_$week2End"])

        // После тихой записи в baseline вторая загрузка этой недели уже даёт нормальный diff.
        repository.onScheduleFetched(
            week2(
                lesson(lessonNumber = "1", subject = "Право"),
                lesson(lessonNumber = "2", subject = "История", time = "10:15 - 11:45")
            )
        )
        val weekChanges = repository.scheduleChanges.value.forWeek(week2Start, week2End)
        assertNotNull(weekChanges)
        assertEquals(1, weekChanges!!.added.size)
    }

    @Test
    fun markWeekSeenClearsOnlyItsWeek() {
        val repository = repo()
        repository.onScheduleFetched(week1(lesson()))
        repository.onScheduleFetched(week2(lesson(lessonNumber = "1", subject = "Право")))
        repository.onScheduleFetched(
            week1(lesson(), lesson(lessonNumber = "2", subject = "Физика", time = "10:15 - 11:45"))
        )
        repository.onScheduleFetched(
            week2(
                lesson(lessonNumber = "1", subject = "Право"),
                lesson(lessonNumber = "2", subject = "История", time = "10:15 - 11:45")
            )
        )
        assertTrue(repository.scheduleChanges.value.isWeekMarked(week1Start, week1End))
        assertTrue(repository.scheduleChanges.value.isWeekMarked(week2Start, week2End))

        repository.markWeekSeen(week1Start, week1End)

        assertFalse(repository.scheduleChanges.value.isWeekMarked(week1Start, week1End))
        assertNull(repository.scheduleChanges.value.forWeek(week1Start, week1End))
        assertTrue(repository.scheduleChanges.value.isWeekMarked(week2Start, week2End))

        // baseline первой недели обновился до latest: повторная загрузка не даёт изменений.
        assertEquals(
            2,
            store.loadScheduleBaseline()!!.weeks.getValue("${week1Start}_$week1End").size
        )
        repository.onScheduleFetched(
            week1(lesson(), lesson(lessonNumber = "2", subject = "Физика", time = "10:15 - 11:45"))
        )
        assertFalse(repository.scheduleChanges.value.isWeekMarked(week1Start, week1End))
    }

    @Test
    fun markWeekSeenKeepsBaselineOfOtherWeeks() {
        val repository = repo()
        repository.onScheduleFetched(week1(lesson()))
        repository.onScheduleFetched(week2(lesson(lessonNumber = "1", subject = "Право")))
        assertEquals(
            1,
            store.loadScheduleBaseline()!!.weeks.getValue("${week1Start}_$week1End").size
        )

        repository.markWeekSeen(week2Start, week2End)

        assertEquals(
            1,
            store.loadScheduleBaseline()!!.weeks.getValue("${week1Start}_$week1End").size
        )
    }

    @Test
    fun markAllScheduleSeenClearsEverything() {
        val repository = repo()
        repository.onScheduleFetched(week1(lesson()))
        repository.onScheduleFetched(
            week1(lesson(), lesson(lessonNumber = "2", subject = "Физика", time = "10:15 - 11:45"))
        )
        repository.onScheduleFetched(week2(lesson(lessonNumber = "1", subject = "Право")))
        repository.onScheduleFetched(
            week2(
                lesson(lessonNumber = "1", subject = "Право"),
                lesson(lessonNumber = "2", subject = "История", time = "10:15 - 11:45")
            )
        )

        repository.markAllScheduleSeen()

        assertFalse(repository.scheduleChanges.value.hasChanges)
        assertTrue(store.loadScheduleChanges().weeks.isEmpty())
    }

    @Test
    fun resetClearsScheduleAndCurriculum() {
        val repository = repo()
        repository.onScheduleFetched(week1(lesson()))
        repository.onScheduleFetched(
            week1(lesson(), lesson(lessonNumber = "2", subject = "Физика", time = "10:15 - 11:45"))
        )
        repository.onCurriculumFetched(listOf(term(1, subject(currentScore = "5"))))
        repository.onCurriculumFetched(listOf(term(1, subject(currentScore = "8"))))
        assertTrue(repository.scheduleChanges.value.hasChanges)
        assertTrue(repository.curriculumChanges.value.hasChanges)

        repository.reset()

        assertFalse(repository.scheduleChanges.value.hasChanges)
        assertFalse(repository.curriculumChanges.value.hasChanges)
        assertNull(store.loadScheduleBaseline())
        assertNull(store.loadCurriculumBaseline())
        assertTrue(prefs.values.isEmpty())
    }

    @Test
    fun persistedChangesAreReloadedByNewRepository() {
        val first = repo()
        first.onScheduleFetched(week1(lesson()))
        first.onScheduleFetched(
            week1(lesson(), lesson(lessonNumber = "2", subject = "Физика", time = "10:15 - 11:45"))
        )
        val expected = first.scheduleChanges.value

        val second = ChangesRepository.forTesting(ChangesStore(prefs))

        assertEquals(expected, second.scheduleChanges.value)
        assertTrue(second.scheduleChanges.value.isWeekMarked(week1Start, week1End))
    }

    @Test
    fun concurrentFetchesDoNotCorruptState() {
        val repository = repo()
        repository.onScheduleFetched(
            week1(lesson(lessonNumber = "1", subject = "S1", time = "01:00 - 01:45"))
        )

        val workers = (1..4).map {
            Thread {
                for (i in 1..20) {
                    val count = (i % 5) + 1
                    val lessons = (1..count).map { number ->
                        lesson(
                            lessonNumber = number.toString(),
                            subject = "S$number",
                            time = "0$number:00 - 0$number:45"
                        )
                    }
                    repository.onScheduleFetched(week1(*lessons.toTypedArray()))
                }
            }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }

        val weekChanges = repository.scheduleChanges.value.forWeek(week1Start, week1End)
        weekChanges?.let {
            assertTrue(it.modified.isEmpty())
            assertTrue(it.removed.isEmpty())
            assertTrue(it.added.all { ref -> ref.lessonNumber != "1" })
        }
        assertNotNull(store.loadScheduleLatest())
        assertNotNull(store.loadScheduleBaseline()!!.weeks["${week1Start}_$week1End"])
    }

    // --- curriculum ----------------------------------------------------------

    @Test
    fun firstCurriculumFetchIsSilent() {
        val repository = repo()

        repository.onCurriculumFetched(listOf(term(1, subject(currentScore = "5"))))

        assertFalse(repository.curriculumChanges.value.hasChanges)
        assertNotNull(store.loadCurriculumBaseline())
    }

    @Test
    fun curriculumScoreDiffIsCumulativeFromBaseline() {
        val repository = repo()
        repository.onCurriculumFetched(listOf(term(1, subject(currentScore = "5"))))

        repository.onCurriculumFetched(listOf(term(1, subject(currentScore = "6"))))
        val firstChange = repository.curriculumChanges.value.forTerm(1)!!.scoreChanges.single()
        assertEquals("5", firstChange.oldValue)
        assertEquals("6", firstChange.newValue)

        // 8 приходит после 7: diff всё ещё от baseline (5), одна запись 5 -> 8.
        repository.onCurriculumFetched(listOf(term(1, subject(currentScore = "7"))))
        repository.onCurriculumFetched(listOf(term(1, subject(currentScore = "8"))))

        val changes = repository.curriculumChanges.value.forTerm(1)!!.scoreChanges
        assertEquals(1, changes.size)
        assertEquals("5", changes.single().oldValue)
        assertEquals("8", changes.single().newValue)
        assertEquals(
            "5",
            store.loadCurriculumBaseline()!!.subjects.single().scores["currentScore"]
        )
    }

    @Test
    fun newSubjectInLatestIsNotReportedByRepository() {
        val repository = repo()
        repository.onCurriculumFetched(listOf(term(1, subject(currentScore = "5"))))

        repository.onCurriculumFetched(
            listOf(term(1, subject(currentScore = "5"), subject("Новый предмет", currentScore = "12")))
        )

        assertFalse(repository.curriculumChanges.value.hasChanges)
    }

    @Test
    fun newTermWithoutBaselineIsSilentlyAdded() {
        val repository = repo()
        repository.onCurriculumFetched(listOf(term(1, subject(currentScore = "5"))))

        repository.onCurriculumFetched(
            listOf(
                term(1, subject(currentScore = "5")),
                term(2, subject("История", currentScore = "9"))
            )
        )

        assertFalse(repository.curriculumChanges.value.hasChanges)
        assertTrue(store.loadCurriculumBaseline()!!.subjects.any { it.termNum == 2 })
    }

    @Test
    fun markTermSeenClearsOnlyItsTerm() {
        val repository = repo()
        repository.onCurriculumFetched(
            listOf(
                term(1, subject("Математика", currentScore = "5")),
                term(2, subject("История", currentScore = "9"))
            )
        )
        repository.onCurriculumFetched(
            listOf(
                term(1, subject("Математика", currentScore = "8")),
                term(2, subject("История", currentScore = "10"))
            )
        )
        assertTrue(repository.curriculumChanges.value.forTerm(1)!!.hasChanges)
        assertTrue(repository.curriculumChanges.value.forTerm(2)!!.hasChanges)

        repository.markTermSeen(1)

        assertNull(repository.curriculumChanges.value.forTerm(1))
        assertTrue(repository.curriculumChanges.value.forTerm(2)!!.hasChanges)
        assertEquals(
            "8",
            store.loadCurriculumBaseline()!!.subjects.first { it.termNum == 1 }.scores["currentScore"]
        )
    }

    @Test
    fun markAllCurriculumSeenClearsEverything() {
        val repository = repo()
        repository.onCurriculumFetched(listOf(term(1, subject(currentScore = "5"))))
        repository.onCurriculumFetched(listOf(term(1, subject(currentScore = "8"))))
        assertTrue(repository.curriculumChanges.value.hasChanges)

        repository.markAllCurriculumSeen()

        assertFalse(repository.curriculumChanges.value.hasChanges)
        assertTrue(store.loadCurriculumChanges().terms.isEmpty())
    }
}
