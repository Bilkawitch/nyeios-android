package ru.nya.nyeios

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.nya.nyeios.data.changes.ScoreField
import ru.nya.nyeios.data.history.HistoryImportResult
import ru.nya.nyeios.data.history.InMemoryScoreHistoryDao
import ru.nya.nyeios.data.history.ScoreHistoryCodec
import ru.nya.nyeios.data.history.ScoreHistoryPoint
import ru.nya.nyeios.data.history.ScoreHistoryRepository
import ru.nya.nyeios.data.history.ScoreSeriesKey
import ru.nya.nyeios.data.model.CurriculumSubject
import ru.nya.nyeios.data.model.CurriculumTerm
import java.time.Instant
import java.util.TimeZone

/**
 * JVM-проверки фасада [ScoreHistoryRepository] на [InMemoryScoreHistoryDao] и фиксированных
 * часах: правила записи (изменение снимка, ежедневная точка, пустые снимки, группа),
 * revision, экспорт/импорт с дедупликацией и потокобезопасность.
 */
class ScoreHistoryRepositoryTest {

    private lateinit var dao: InMemoryScoreHistoryDao
    private lateinit var repository: ScoreHistoryRepository
    private var now: Long = 0L
    private lateinit var originalTimeZone: TimeZone

    private val dayMs = 24L * 60L * 60L * 1000L

    private val subjectName = "Математика"
    private val controlType = "Экзамен"
    private val group = "23АН-о-41"

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        now = Instant.parse("2026-01-15T12:00:00Z").toEpochMilli()
        dao = InMemoryScoreHistoryDao()
        repository = ScoreHistoryRepository.forTesting(dao) { now }
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    // --- builders ------------------------------------------------------------

    private fun subject(
        subject: String = subjectName,
        controlType: String = this.controlType,
        currentScore: String = "",
        currentRating: String = "",
        termRating: String = "",
        finalRating: String = "",
        examScore: String = "",
        grade: String = ""
    ) = CurriculumSubject(
        subject = subject,
        controlType = controlType,
        currentScore = currentScore,
        currentRating = currentRating,
        termRating = termRating,
        finalRating = finalRating,
        examScore = examScore,
        grade = grade
    )

    private fun term(termNum: Int = 1, vararg subjects: CurriculumSubject) = CurriculumTerm(
        termNum = termNum,
        termTitle = "Семестр $termNum",
        subjects = subjects.toList()
    )

    private fun fetch(terms: List<CurriculumTerm>, group: String? = this.group) {
        repository.onCurriculumFetched(terms, group)
    }

    private fun singleTerm(vararg subjects: CurriculumSubject) = listOf(term(1, *subjects))

    private fun key(
        group: String = this.group,
        termNum: Int = 1,
        subject: String = subjectName,
        controlType: String = this.controlType
    ) = ScoreSeriesKey(group, termNum, subject, controlType)

    // --- первая запись и дедупликация ---------------------------------------

    @Test
    fun firstFetchWritesCompletePoint() {
        fetch(singleTerm(subject(currentScore = "5")))

        assertEquals(1, dao.count())
        val point = dao.queryAll().single()
        assertEquals(now, point.recordedAtMs)
        assertEquals(group, point.group)
        assertEquals(1, point.termNum)
        assertEquals(subjectName, point.subject)
        assertEquals(controlType, point.controlType)
        assertEquals("5", point.scores[ScoreField.CURRENT_SCORE.rawName])
        assertEquals(ScoreField.entries.size, point.scores.size)
        assertEquals(1, repository.revision.value)
    }

    @Test
    fun sameSnapshotSameDayDoesNotWriteAgain() {
        fetch(singleTerm(subject(currentScore = "5")))
        fetch(singleTerm(subject(currentScore = "5")))

        now += 60L * 60L * 1000L // +1 час, те же календарные сутки
        fetch(singleTerm(subject(currentScore = "5")))

        assertEquals(1, dao.count())
        assertEquals(1, repository.revision.value)
    }

    @Test
    fun changedScoreWritesNewPointSameDay() {
        fetch(singleTerm(subject(currentScore = "5")))
        now += 60L * 60L * 1000L
        fetch(singleTerm(subject(currentScore = "8")))

        assertEquals(2, dao.count())
        val points = dao.querySeries(key())
        assertEquals("5", points[0].scores[ScoreField.CURRENT_SCORE.rawName])
        assertEquals("8", points[1].scores[ScoreField.CURRENT_SCORE.rawName])
        assertEquals(2, repository.revision.value)
    }

    @Test
    fun nextCalendarDayWritesDailyClosingPoint() {
        fetch(singleTerm(subject(currentScore = "5")))

        now += dayMs
        fetch(singleTerm(subject(currentScore = "5")))

        assertEquals(2, dao.count())
        assertEquals(now, dao.latestPoint(key())!!.recordedAtMs)
        assertEquals(2, repository.revision.value)
    }

    @Test
    fun emptySnapshotIsNeverWrittenAsFirstPoint() {
        fetch(singleTerm(subject()))

        assertEquals(0, dao.count())
        assertEquals(0, repository.revision.value)
    }

    @Test
    fun zeroOnlyScoreIsNotWrittenAsFirstPoint() {
        fetch(singleTerm(subject(currentScore = "0")))

        assertEquals(0, dao.count())
    }

    @Test
    fun emptySnapshotDoesNotDrownExistingSeries() {
        fetch(singleTerm(subject(currentScore = "5")))

        now += 60L * 60L * 1000L
        fetch(singleTerm(subject()))

        now += dayMs
        fetch(singleTerm(subject()))

        assertEquals(1, dao.count())
        assertEquals(1, repository.revision.value)
    }

    @Test
    fun briefBecomesEmptyPrimaryDoesNotWritePoint() {
        // Балл был, стал "—": точка не нужна, график не должен проваливаться.
        fetch(singleTerm(subject(currentScore = "5")))

        now += 60L * 60L * 1000L
        fetch(singleTerm(subject(currentScore = "—")))

        assertEquals(1, dao.count())
    }

    // --- группа --------------------------------------------------------------

    @Test
    fun groupArgumentOverridesRememberedGroup() {
        repository.rememberGroup("REMEMBERED")
        fetch(singleTerm(subject(currentScore = "5")), group = "EXPLICIT")

        assertEquals("EXPLICIT", dao.queryAll().single().group)
    }

    @Test
    fun rememberedGroupIsUsedWhenArgumentMissing() {
        repository.rememberGroup("REMEMBERED")
        fetch(singleTerm(subject(currentScore = "5")), group = null)

        assertEquals("REMEMBERED", dao.queryAll().single().group)
    }

    @Test
    fun blankGroupIsUsedAsIsWhenNothingRemembered() {
        fetch(singleTerm(subject(currentScore = "5")), group = null)

        assertEquals("", dao.queryAll().single().group)
    }

    @Test
    fun differentGroupsProduceDifferentSeries() {
        fetch(singleTerm(subject(currentScore = "5")), group = "G1")
        now += 60L * 60L * 1000L
        fetch(singleTerm(subject(currentScore = "5")), group = "G2")

        assertEquals(2, dao.count())
        assertEquals(
            setOf("G1", "G2"),
            dao.distinctSeriesKeys().map { it.group }.toSet()
        )
    }

    // --- revision ------------------------------------------------------------

    @Test
    fun revisionIncrementsOncePerFetchWithInsertions() {
        fetch(
            listOf(
                term(
                    1,
                    subject(subject = "Математика", currentScore = "5"),
                    subject(subject = "Физика", currentScore = "4")
                )
            )
        )

        assertEquals(2, dao.count())
        assertEquals(1, repository.revision.value)
    }

    @Test
    fun revisionDoesNotGrowWithoutInsertions() {
        fetch(singleTerm(subject(currentScore = "5")))
        fetch(singleTerm(subject(currentScore = "5")))

        assertEquals(1, repository.revision.value)
    }

    // --- чтение --------------------------------------------------------------

    @Test
    fun seriesKeysAreSortedAndFilteredByRememberedGroup() {
        fetch(singleTerm(subject(subject = "Б", currentScore = "5")), group = "G2")
        now += 60L * 60L * 1000L
        fetch(singleTerm(subject(subject = "А", currentScore = "5")), group = "G1")
        now += 60L * 60L * 1000L
        fetch(
            listOf(
                term(2, subject(subject = "Б", currentScore = "5"))
            ),
            group = "G1"
        )

        assertEquals(
            listOf(
                ScoreSeriesKey("G1", 1, "А", controlType),
                ScoreSeriesKey("G1", 2, "Б", controlType),
                ScoreSeriesKey("G2", 1, "Б", controlType)
            ),
            repository.seriesKeys()
        )

        repository.rememberGroup("G1")
        assertEquals(
            listOf(
                ScoreSeriesKey("G1", 1, "А", controlType),
                ScoreSeriesKey("G1", 2, "Б", controlType)
            ),
            repository.seriesKeys(allGroups = false)
        )
    }

    @Test
    fun pointsForReturnsSeriesOrderedByTime() {
        fetch(singleTerm(subject(currentScore = "5")))
        now += dayMs
        fetch(singleTerm(subject(currentScore = "8")))

        val points = repository.pointsFor(key())

        assertEquals(2, points.size)
        assertEquals("5", points[0].scores[ScoreField.CURRENT_SCORE.rawName])
        assertEquals("8", points[1].scores[ScoreField.CURRENT_SCORE.rawName])
        assertTrue(points[0].recordedAtMs < points[1].recordedAtMs)
    }

    @Test
    fun statsReflectStoredPoints() {
        fetch(
            listOf(
                term(1, subject(subject = "Математика", currentScore = "5")),
                term(1, subject(subject = "Физика", currentScore = "4"))
            )
        )
        now += dayMs
        fetch(singleTerm(subject(subject = "Математика", currentScore = "8")))

        val stats = repository.stats()

        assertEquals(3, stats.totalPoints)
        assertEquals(2, stats.subjectsCount)
        assertEquals(now - dayMs, stats.firstAtMs)
        assertEquals(now, stats.lastAtMs)
    }

    // --- экспорт / импорт ----------------------------------------------------

    @Test
    fun exportImportRoundTripIntoEmptyDatabaseGivesSamePoints() {
        fetch(singleTerm(subject(currentScore = "5")))
        now += dayMs
        fetch(singleTerm(subject(currentScore = "8")))
        val exported = repository.exportJson()
        val originalPoints = dao.queryAll()

        val freshDao = InMemoryScoreHistoryDao()
        val freshRepository = ScoreHistoryRepository.forTesting(freshDao) { now }
        val result = freshRepository.importJson(exported)

        assertEquals(HistoryImportResult(added = 2, duplicates = 0, invalid = 0), result)
        assertEquals(originalPoints, freshDao.queryAll())
        assertEquals(1, freshRepository.revision.value)
    }

    @Test
    fun exportPayloadUsesRememberedGroupAndClock() {
        repository.rememberGroup("GRP")
        fetch(singleTerm(subject(currentScore = "5")), group = "GRP")

        val payload = repository.exportPayload()

        assertEquals(ScoreHistoryCodec.SCHEMA_VERSION, payload.schema)
        assertEquals(ScoreHistoryCodec.APP_NAME, payload.app)
        assertEquals("GRP", payload.group)
        assertEquals(now, payload.exportedAtMs)
        assertEquals(1, payload.points.size)
    }

    @Test
    fun exportPayloadOfEmptyHistoryIsEmpty() {
        val payload = repository.exportPayload()

        assertEquals("", payload.group)
        assertTrue(payload.points.isEmpty())
    }

    @Test
    fun importInvalidJsonReportsInvalid() {
        val result = repository.importJson("definitely not json")

        assertEquals(HistoryImportResult(added = 0, duplicates = 0, invalid = 1), result)
        assertEquals(0, dao.count())
        assertEquals(0, repository.revision.value)
    }

    @Test
    fun importDeduplicatesExistingPoints() {
        fetch(singleTerm(subject(currentScore = "5")))
        now += dayMs
        fetch(singleTerm(subject(currentScore = "8")))
        val json = repository.exportJson()

        val result = repository.importJson(json)

        assertEquals(HistoryImportResult(added = 0, duplicates = 2, invalid = 0), result)
        assertEquals(2, dao.count())
        assertEquals(2, repository.revision.value)
    }

    @Test
    fun importMergesNewPointsAndSkipsDuplicates() {
        fetch(singleTerm(subject(currentScore = "5")))
        val existing = dao.queryAll().single()

        val newPoint = ScoreHistoryPoint(
            recordedAtMs = now + dayMs,
            group = group,
            termNum = 1,
            subject = subjectName,
            controlType = controlType,
            scores = mapOf(ScoreField.CURRENT_SCORE.rawName to "8")
        )
        val json = ScoreHistoryCodec.encode(
            ScoreHistoryCodec.ExportPayload(
                schema = ScoreHistoryCodec.SCHEMA_VERSION,
                app = ScoreHistoryCodec.APP_NAME,
                exportedAtMs = now,
                group = group,
                points = listOf(existing, newPoint)
            )
        )

        val result = repository.importJson(json)

        assertEquals(HistoryImportResult(added = 1, duplicates = 1, invalid = 0), result)
        assertEquals(2, dao.count())
        assertEquals(2, repository.revision.value)
    }

    @Test
    fun importFillsBlankPointGroupFromEnvelope() {
        val json = ScoreHistoryCodec.encode(
            ScoreHistoryCodec.ExportPayload(
                schema = ScoreHistoryCodec.SCHEMA_VERSION,
                app = ScoreHistoryCodec.APP_NAME,
                exportedAtMs = now,
                group = "ENV-GROUP",
                points = listOf(
                    ScoreHistoryPoint(
                        recordedAtMs = now - dayMs,
                        group = "",
                        termNum = 1,
                        subject = subjectName,
                        controlType = controlType,
                        scores = mapOf(ScoreField.CURRENT_SCORE.rawName to "5")
                    )
                )
            )
        )

        val result = repository.importJson(json)

        assertEquals(1, result.added)
        assertEquals("ENV-GROUP", dao.queryAll().single().group)
    }

    @Test
    fun importedOldPointAllowsDailyPointToBeWritten() {
        val json = ScoreHistoryCodec.encode(
            ScoreHistoryCodec.ExportPayload(
                schema = ScoreHistoryCodec.SCHEMA_VERSION,
                app = ScoreHistoryCodec.APP_NAME,
                exportedAtMs = now,
                group = group,
                points = listOf(
                    ScoreHistoryPoint(
                        recordedAtMs = now - 2 * dayMs,
                        group = group,
                        termNum = 1,
                        subject = subjectName,
                        controlType = controlType,
                        scores = mapOf(ScoreField.CURRENT_SCORE.rawName to "5")
                    )
                )
            )
        )
        repository.importJson(json)

        fetch(singleTerm(subject(currentScore = "5")))

        assertEquals(2, dao.count())
    }

    // --- clear ---------------------------------------------------------------

    @Test
    fun clearRemovesAllPointsAndBumpsRevision() {
        fetch(singleTerm(subject(currentScore = "5")))
        assertEquals(1, repository.revision.value)

        repository.clear()

        assertEquals(0, dao.count())
        assertEquals(0, repository.stats().totalPoints)
        assertNull(repository.stats().firstAtMs)
        assertEquals(2, repository.revision.value)
    }

    // --- потокобезопасность ---------------------------------------------------

    @Test
    fun concurrentFetchesDoNotDuplicatePoints() {
        val workers = (1..4).map {
            Thread {
                repeat(20) {
                    repository.onCurriculumFetched(
                        singleTerm(subject(currentScore = "5")),
                        group
                    )
                }
            }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }

        assertEquals(1, dao.count())
        assertEquals(1, repository.revision.value)
        assertNotNull(dao.latestPoint(key()))
    }

    // --- группа в экспортном конверте ---------------------------------------

    @Test
    fun exportPayloadFallsBackToSingleGroupFromPoints() {
        fetch(singleTerm(subject(currentScore = "5")))

        val payload = repository.exportPayload()

        assertEquals(group, payload.group)
        assertEquals(1, payload.points.size)
    }

    @Test
    fun exportPayloadPrefersRememberedGroup() {
        fetch(singleTerm(subject(currentScore = "5")))
        repository.rememberGroup("24АН-о-42")

        val payload = repository.exportPayload()

        assertEquals("24АН-о-42", payload.group)
    }

    @Test
    fun exportPayloadKeepsGroupBlankWhenPointsHaveSeveralGroups() {
        fetch(singleTerm(subject(currentScore = "5")), group = "23АН-о-41")
        fetch(singleTerm(subject(currentScore = "6")), group = "24АН-о-42")

        val payload = repository.exportPayload()

        assertEquals("", payload.group)
        assertEquals(2, payload.points.size)
    }
}
