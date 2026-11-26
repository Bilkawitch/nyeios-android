package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.changes.ChangesDiff
import ru.nya.nyeios.data.changes.CurriculumChanges
import ru.nya.nyeios.data.changes.ScoreField
import ru.nya.nyeios.data.changes.TermChanges
import ru.nya.nyeios.data.model.CurriculumSubject
import ru.nya.nyeios.data.model.CurriculumTerm

/**
 * JVM-проверки diff-движка БРС ([ChangesDiff]): кумулятивная дельта из baseline,
 * пустые значения, новые предметы, семестры и сопоставление по controlType.
 */
class CurriculumDiffTest {

    private fun subject(
        subject: String = "Математика",
        controlType: String = "Экзамен",
        currentScore: String = "5",
        currentRating: String = "",
        termRating: String = "",
        examScore: String = "",
        finalRating: String = "",
        grade: String = ""
    ) = CurriculumSubject(
        subject = subject,
        controlType = controlType,
        currentScore = currentScore,
        currentRating = currentRating,
        termRating = termRating,
        examScore = examScore,
        finalRating = finalRating,
        grade = grade
    )

    private fun term(termNum: Int = 1, subjects: List<CurriculumSubject> = listOf(subject())) =
        CurriculumTerm(
            termNum = termNum,
            termTitle = "Семестр $termNum",
            subjects = subjects
        )

    private fun snapshotOf(terms: List<CurriculumTerm>) = ChangesDiff.curriculumSnapshot(terms)

    private fun diff(
        baseline: List<CurriculumTerm>?,
        latest: List<CurriculumTerm>
    ): Map<Int, TermChanges> = ChangesDiff.curriculumChanges(
        baseline?.let { ChangesDiff.curriculumSnapshot(it) },
        ChangesDiff.curriculumSnapshot(latest)
    )

    // --- snapshot conversion -------------------------------------------------

    @Test
    fun snapshotContainsAllScoreFields() {
        val snapshot = snapshotOf(listOf(term()))

        val subject = snapshot.subjects.single()
        assertEquals(1, subject.termNum)
        assertEquals("Математика", subject.subject)
        assertEquals("Экзамен", subject.controlType)
        assertEquals(
            ScoreField.entries.map { it.rawName }.toSet(),
            subject.scores.keys
        )
        assertEquals("5", subject.scores[ScoreField.CURRENT_SCORE.rawName])
        assertEquals("", subject.scores[ScoreField.GRADE.rawName])
    }

    @Test
    fun snapshotFlattensAllTermsAndSubjects() {
        val snapshot = snapshotOf(
            listOf(
                term(1, listOf(subject("Математика"), subject("Физика"))),
                term(2, listOf(subject("История")))
            )
        )

        assertEquals(3, snapshot.subjects.size)
        assertEquals(setOf(1, 2), snapshot.subjects.map { it.termNum }.toSet())
    }

    // --- diff basics ---------------------------------------------------------

    @Test
    fun firstLoadWithoutBaselineProducesNoChanges() {
        assertTrue(ChangesDiff.curriculumChanges(null, snapshotOf(listOf(term()))).isEmpty())
    }

    @Test
    fun identicalTermsProduceNoChanges() {
        val terms = listOf(term())
        assertTrue(diff(terms, terms).isEmpty())
    }

    @Test
    fun currentScoreFiveToEightIsOneChange() {
        val changes = diff(
            listOf(term(subjects = listOf(subject(currentScore = "5")))),
            listOf(term(subjects = listOf(subject(currentScore = "8"))))
        )

        val termChanges = changes.getValue(1)
        assertEquals(1, termChanges.scoreChanges.size)
        val change = termChanges.scoreChanges.single()
        assertEquals(1, change.termNum)
        assertEquals("Математика", change.subject)
        assertEquals("Экзамен", change.controlType)
        assertEquals(ScoreField.CURRENT_SCORE, change.field)
        assertEquals("5", change.oldValue)
        assertEquals("8", change.newValue)
        assertTrue(termChanges.hasChanges)
    }

    @Test
    fun emptyOldValueCountsAsChange() {
        val changes = diff(
            listOf(term(subjects = listOf(subject(currentScore = "")))),
            listOf(term(subjects = listOf(subject(currentScore = "12"))))
        )

        val change = changes.getValue(1).scoreChanges.single()
        assertEquals(ScoreField.CURRENT_SCORE, change.field)
        assertEquals("", change.oldValue)
        assertEquals("12", change.newValue)
    }

    @Test
    fun severalFieldsOfOneSubjectAreReported() {
        val changes = diff(
            listOf(term(subjects = listOf(subject(currentScore = "5", termRating = "10")))),
            listOf(term(subjects = listOf(subject(currentScore = "8", termRating = "12"))))
        )

        val reported = changes.getValue(1).scoreChanges
        assertEquals(2, reported.size)
        assertEquals(
            setOf(ScoreField.CURRENT_SCORE, ScoreField.TERM_RATING),
            reported.map { it.field }.toSet()
        )
        assertEquals(
            mapOf(ScoreField.CURRENT_SCORE to ("5" to "8"), ScoreField.TERM_RATING to ("10" to "12")),
            reported.associate { it.field to (it.oldValue to it.newValue) }
        )
    }

    @Test
    fun severalSubjectsAndTermsAreReported() {
        val changes = diff(
            listOf(
                term(1, listOf(subject("Математика", currentScore = "5"), subject("Физика", currentScore = "7"))),
                term(2, listOf(subject("История", currentScore = "9")))
            ),
            listOf(
                term(1, listOf(subject("Математика", currentScore = "8"), subject("Физика", currentScore = "7"))),
                term(2, listOf(subject("История", currentScore = "10")))
            )
        )

        assertEquals(setOf(1, 2), changes.keys)
        assertEquals(1, changes.getValue(1).scoreChanges.size)
        assertEquals(1, changes.getValue(2).scoreChanges.size)
    }

    @Test
    fun newSubjectOnlyInLatestIsIgnored() {
        val changes = diff(
            listOf(term(subjects = listOf(subject("Математика", currentScore = "5")))),
            listOf(
                term(
                    subjects = listOf(
                        subject("Математика", currentScore = "5"),
                        subject("Новый предмет", currentScore = "12")
                    )
                )
            )
        )

        assertTrue("new subject must not look like a score change", changes.isEmpty())
    }

    @Test
    fun newTermWithoutBaselineIsIgnored() {
        val changes = diff(
            listOf(term(1)),
            listOf(term(1), term(2, listOf(subject("История", currentScore = "12"))))
        )

        assertTrue(changes.isEmpty())
    }

    @Test
    fun subjectIsMatchedBySubjectAndControlType() {
        val baseline = term(
            subjects = listOf(
                subject("Физика", controlType = "Экзамен", currentScore = "5"),
                subject("Физика", controlType = "Зачёт", currentScore = "7")
            )
        )
        val latest = term(
            subjects = listOf(
                subject("Физика", controlType = "Экзамен", currentScore = "5"),
                subject("Физика", controlType = "Зачёт", currentScore = "8")
            )
        )

        val reported = diff(listOf(baseline), listOf(latest)).getValue(1).scoreChanges
        assertEquals(1, reported.size)
        assertEquals("Зачёт", reported.single().controlType)
        assertEquals("7", reported.single().oldValue)
        assertEquals("8", reported.single().newValue)
    }

    @Test
    fun termsDoNotMixChanges() {
        val changes = diff(
            listOf(
                term(1, listOf(subject("Математика", currentScore = "5"))),
                term(2, listOf(subject("История", currentScore = "9")))
            ),
            listOf(
                term(1, listOf(subject("Математика", currentScore = "8"))),
                term(2, listOf(subject("История", currentScore = "9")))
            )
        )

        assertEquals(setOf(1), changes.keys)
        assertNull(changes[2])
    }

    // --- aggregate helpers ---------------------------------------------------

    @Test
    fun termChangesFiltersBySubjectAndControlType() {
        val changes = diff(
            listOf(
                term(
                    1,
                    listOf(
                        subject("Математика", controlType = "Экзамен", currentScore = "5"),
                        subject("Физика", controlType = "Зачёт", currentScore = "7")
                    )
                )
            ),
            listOf(
                term(
                    1,
                    listOf(
                        subject("Математика", controlType = "Экзамен", currentScore = "8"),
                        subject("Физика", controlType = "Зачёт", currentScore = "9")
                    )
                )
            )
        )

        val termChanges = changes.getValue(1)
        val math = termChanges.forSubject("Математика", "Экзамен")
        assertEquals(1, math.size)
        assertEquals(ScoreField.CURRENT_SCORE, math.single().field)
        assertTrue(termChanges.forSubject("История", "Экзамен").isEmpty())
    }

    @Test
    fun curriculumChangesAggregatesTermHelpers() {
        val changes = diff(
            listOf(term(1, listOf(subject("Математика", currentScore = "5")))),
            listOf(term(1, listOf(subject("Математика", currentScore = "8"))))
        )
        val aggregate = CurriculumChanges(changes)

        assertEquals(changes.getValue(1), aggregate.forTerm(1))
        assertNull(aggregate.forTerm(2))
        assertEquals(1, aggregate.totalChanges)
        assertTrue(aggregate.hasChanges)
        assertFalse(CurriculumChanges().hasChanges)
        assertEquals(0, CurriculumChanges().totalChanges)
    }

    @Test
    fun emptySubjectChangesAreNoChanges() {
        assertFalse(TermChanges(1).hasChanges)
        assertTrue(TermChanges(1).scoreChanges.isEmpty())
    }
}
