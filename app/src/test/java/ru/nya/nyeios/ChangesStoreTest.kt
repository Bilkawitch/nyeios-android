package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.nya.nyeios.data.changes.ChangePrefs
import ru.nya.nyeios.data.changes.ChangesDiff
import ru.nya.nyeios.data.changes.ChangesStore
import ru.nya.nyeios.data.changes.CurriculumChanges
import ru.nya.nyeios.data.changes.LessonRef
import ru.nya.nyeios.data.changes.ScheduleChanges
import ru.nya.nyeios.data.changes.ScoreChange
import ru.nya.nyeios.data.changes.ScoreField
import ru.nya.nyeios.data.changes.TermChanges
import ru.nya.nyeios.data.changes.WeekChanges

/**
 * JVM-проверки персистентности [ChangesStore] на фейковом [ChangePrefs]:
 * round-trip baseline/latest/changes, битые данные и очистка.
 */
class ChangesStoreTest {

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

    @Before
    fun setUp() {
        prefs = FakePrefs()
        store = ChangesStore(prefs)
    }

    private fun lessonSnapshot(
        weekKey: String = WEEK_KEY,
        dateString: String = "15.09",
        dayName: String = "Пн",
        lessonNumber: String = "1",
        subgroup: String = "",
        time: String = "08:30 - 10:00",
        subject: String = "Математика",
        room: String = "101",
        teacher: String = "Иванов И.И.",
        type: String = "LECTURE"
    ) = ChangesDiff.LessonSnapshot(
        weekKey = weekKey,
        dateString = dateString,
        dayName = dayName,
        lessonNumber = lessonNumber,
        subgroup = subgroup,
        time = time,
        subject = subject,
        room = room,
        teacher = teacher,
        type = type
    )

    private fun scheduleSnapshot(lessons: List<ChangesDiff.LessonSnapshot> = listOf(lessonSnapshot())) =
        ChangesDiff.ScheduleSnapshot(mapOf(WEEK_KEY to lessons))

    private fun lessonRef(lessonNumber: String = "1", subject: String = "Математика") = LessonRef(
        weekKey = WEEK_KEY,
        dateString = "15.09",
        dayName = "Пн",
        lessonNumber = lessonNumber,
        subgroup = "",
        time = "08:30 - 10:00",
        subject = subject,
        room = "101",
        teacher = "Иванов И.И.",
        type = "LECTURE"
    )

    private fun subjectSnapshot(
        termNum: Int = 1,
        subject: String = "Математика",
        controlType: String = "Экзамен",
        scores: Map<String, String> = mutableMapOf(
            ScoreField.CURRENT_SCORE.rawName to "5",
            ScoreField.CURRENT_RATING.rawName to "",
            ScoreField.TERM_RATING.rawName to "",
            ScoreField.EXAM_SCORE.rawName to "",
            ScoreField.FINAL_RATING.rawName to "",
            ScoreField.GRADE.rawName to ""
        )
    ) = ChangesDiff.SubjectSnapshot(
        termNum = termNum,
        subject = subject,
        controlType = controlType,
        scores = scores
    )

    private fun curriculumSnapshot(subjects: List<ChangesDiff.SubjectSnapshot> = listOf(subjectSnapshot())) =
        ChangesDiff.CurriculumSnapshot(subjects)

    private fun sampleScheduleChanges(): ScheduleChanges = ScheduleChanges(
        mapOf(
            WEEK_KEY to WeekChanges(
                weekKey = WEEK_KEY,
                added = listOf(lessonRef("2", "Физика")),
                modified = listOf(lessonRef("1", "Математика")),
                removed = listOf(lessonRef("3", "История"))
            )
        )
    )

    private fun sampleCurriculumChanges(): CurriculumChanges = CurriculumChanges(
        mapOf(
            1 to TermChanges(
                termNum = 1,
                scoreChanges = listOf(
                    ScoreChange(1, "Математика", "Экзамен", ScoreField.CURRENT_SCORE, "5", "8")
                )
            )
        )
    )

    private fun corruptAllValues() {
        prefs.values.keys.toList().forEach { key -> prefs.values[key] = "{ this is not valid json ]" }
    }

    // --- empty / missing -----------------------------------------------------

    @Test
    fun missingDataLoadsEmptyValues() {
        assertNull(store.loadScheduleBaseline())
        assertNull(store.loadScheduleLatest())
        assertTrue(store.loadScheduleChanges().weeks.isEmpty())
        assertNull(store.loadCurriculumBaseline())
        assertNull(store.loadCurriculumLatest())
        assertTrue(store.loadCurriculumChanges().terms.isEmpty())
    }

    @Test
    fun blankDataLoadsEmptyValues() {
        store.saveScheduleBaseline(scheduleSnapshot())
        store.saveCurriculumBaseline(curriculumSnapshot())
        prefs.values.keys.toList().forEach { key -> prefs.values[key] = "   " }

        assertNull(store.loadScheduleBaseline())
        assertNull(store.loadCurriculumBaseline())
    }

    @Test
    fun nullJsonLoadsEmptyValues() {
        store.saveScheduleChanges(sampleScheduleChanges())
        prefs.values.keys.toList().forEach { key -> prefs.values[key] = "null" }

        assertNull(store.loadScheduleBaseline())
        assertTrue(store.loadScheduleChanges().weeks.isEmpty())
    }

    @Test
    fun corruptedJsonLoadsEmptyValues() {
        store.saveScheduleBaseline(scheduleSnapshot())
        store.saveScheduleChanges(sampleScheduleChanges())
        store.saveCurriculumBaseline(curriculumSnapshot())
        store.saveCurriculumChanges(sampleCurriculumChanges())
        corruptAllValues()

        assertNull(store.loadScheduleBaseline())
        assertNull(store.loadScheduleLatest())
        assertTrue(store.loadScheduleChanges().weeks.isEmpty())
        assertNull(store.loadCurriculumBaseline())
        assertTrue(store.loadCurriculumChanges().terms.isEmpty())
    }

    // --- schedule round-trip -------------------------------------------------

    @Test
    fun scheduleBaselineRoundTrip() {
        val snapshot = scheduleSnapshot(
            listOf(
                lessonSnapshot(),
                lessonSnapshot(lessonNumber = "2", time = "10:15 - 11:45", subject = "Физика", room = "202")
            )
        )
        store.saveScheduleBaseline(snapshot)

        assertEquals(snapshot, store.loadScheduleBaseline())
        assertEquals(snapshot, store.loadScheduleBaseline())
    }

    @Test
    fun scheduleLatestRoundTrip() {
        val snapshot = scheduleSnapshot()
        store.saveScheduleLatest(snapshot)

        assertEquals(snapshot, store.loadScheduleLatest())
        assertNull(store.loadScheduleBaseline())
    }

    @Test
    fun scheduleChangesRoundTrip() {
        val changes = sampleScheduleChanges()
        store.saveScheduleChanges(changes)

        assertEquals(changes, store.loadScheduleChanges())
    }

    @Test
    fun emptySnapshotsRoundTrip() {
        val empty = ChangesDiff.ScheduleSnapshot()
        store.saveScheduleBaseline(empty)

        assertEquals(empty, store.loadScheduleBaseline())
        assertTrue(store.loadScheduleChanges().weeks.isEmpty())
    }

    // --- curriculum round-trip ----------------------------------------------

    @Test
    fun curriculumBaselineRoundTrip() {
        val snapshot = curriculumSnapshot(
            listOf(subjectSnapshot(), subjectSnapshot(subject = "Физика", controlType = "Зачёт"))
        )
        store.saveCurriculumBaseline(snapshot)

        assertEquals(snapshot, store.loadCurriculumBaseline())
    }

    @Test
    fun curriculumLatestRoundTrip() {
        val snapshot = curriculumSnapshot()
        store.saveCurriculumLatest(snapshot)

        assertEquals(snapshot, store.loadCurriculumLatest())
        assertNull(store.loadCurriculumBaseline())
    }

    @Test
    fun curriculumChangesRoundTrip() {
        val changes = sampleCurriculumChanges()
        store.saveCurriculumChanges(changes)

        assertEquals(changes, store.loadCurriculumChanges())
    }

    // --- lifecycle -----------------------------------------------------------

    @Test
    fun stateSurvivesNewStoreInstance() {
        store.saveScheduleBaseline(scheduleSnapshot())
        store.saveScheduleChanges(sampleScheduleChanges())
        store.saveCurriculumChanges(sampleCurriculumChanges())

        val reopened = ChangesStore(prefs)

        assertNotNull(reopened.loadScheduleBaseline())
        assertEquals(sampleScheduleChanges(), reopened.loadScheduleChanges())
        assertEquals(sampleCurriculumChanges(), reopened.loadCurriculumChanges())
    }

    @Test
    fun clearAllRemovesEverything() {
        store.saveScheduleBaseline(scheduleSnapshot())
        store.saveScheduleLatest(scheduleSnapshot())
        store.saveScheduleChanges(sampleScheduleChanges())
        store.saveCurriculumBaseline(curriculumSnapshot())
        store.saveCurriculumLatest(curriculumSnapshot())
        store.saveCurriculumChanges(sampleCurriculumChanges())
        assertTrue(prefs.values.isNotEmpty())

        store.clearAll()

        assertTrue("prefs must be empty after clearAll: ${prefs.values.keys}", prefs.values.isEmpty())
        assertNull(store.loadScheduleBaseline())
        assertNull(store.loadScheduleLatest())
        assertTrue(store.loadScheduleChanges().weeks.isEmpty())
        assertNull(store.loadCurriculumBaseline())
        assertNull(store.loadCurriculumLatest())
        assertTrue(store.loadCurriculumChanges().terms.isEmpty())
    }

    companion object {
        private const val WEEK_KEY = "15.09.2026_21.09.2026"
    }
}
