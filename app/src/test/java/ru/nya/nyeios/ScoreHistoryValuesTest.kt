package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.nya.nyeios.data.changes.ScoreField
import ru.nya.nyeios.data.history.ScoreHistoryValues

/**
 * JVM-проверки перевода сырых баллов БРС в число: парсинг чисел и приоритет полей
 * (finalRating -> currentScore -> currentRating -> termRating, как в UI).
 */
class ScoreHistoryValuesTest {

    // --- parseNumber ---------------------------------------------------------

    @Test
    fun parsesCommaDecimal() {
        assertEquals(12.5, ScoreHistoryValues.parseNumber("12,5")!!, 0.0001)
    }

    @Test
    fun parsesDotDecimal() {
        assertEquals(12.5, ScoreHistoryValues.parseNumber("12.5")!!, 0.0001)
    }

    @Test
    fun trimsSpaces() {
        assertEquals(8.0, ScoreHistoryValues.parseNumber(" 8 ")!!, 0.0001)
    }

    @Test
    fun parsesZero() {
        assertEquals(0.0, ScoreHistoryValues.parseNumber("0")!!, 0.0001)
    }

    @Test
    fun returnsNullForEmpty() {
        assertNull(ScoreHistoryValues.parseNumber(""))
        assertNull(ScoreHistoryValues.parseNumber("   "))
    }

    @Test
    fun returnsNullForDash() {
        assertNull(ScoreHistoryValues.parseNumber("—"))
        assertNull(ScoreHistoryValues.parseNumber("— "))
    }

    @Test
    fun returnsNullForNotApplicable() {
        assertNull(ScoreHistoryValues.parseNumber("н/а"))
    }

    @Test
    fun returnsNullForNonNumeric() {
        assertNull(ScoreHistoryValues.parseNumber("зачёт"))
        assertNull(ScoreHistoryValues.parseNumber("abc"))
    }

    // --- PRIMARY_FIELDS ------------------------------------------------------

    @Test
    fun primaryFieldsMirrorUiPriority() {
        assertEquals(
            listOf(
                ScoreField.FINAL_RATING.rawName,
                ScoreField.CURRENT_SCORE.rawName,
                ScoreField.CURRENT_RATING.rawName,
                ScoreField.TERM_RATING.rawName
            ),
            ScoreHistoryValues.PRIMARY_FIELDS
        )
    }

    @Test
    fun examScoreAndGradeAreNotPrimaryFields() {
        assertEquals(false, ScoreField.EXAM_SCORE.rawName in ScoreHistoryValues.PRIMARY_FIELDS)
        assertEquals(false, ScoreField.GRADE.rawName in ScoreHistoryValues.PRIMARY_FIELDS)
    }

    // --- primaryValue --------------------------------------------------------

    @Test
    fun finalRatingWinsOverCurrentScore() {
        val scores = mapOf(
            ScoreField.FINAL_RATING.rawName to "85",
            ScoreField.CURRENT_SCORE.rawName to "40"
        )
        assertEquals(85.0, ScoreHistoryValues.primaryValue(scores)!!, 0.0001)
    }

    @Test
    fun currentScoreWinsOverCurrentRating() {
        val scores = mapOf(
            ScoreField.CURRENT_SCORE.rawName to "40",
            ScoreField.CURRENT_RATING.rawName to "50"
        )
        assertEquals(40.0, ScoreHistoryValues.primaryValue(scores)!!, 0.0001)
    }

    @Test
    fun currentRatingWinsOverTermRating() {
        val scores = mapOf(
            ScoreField.CURRENT_RATING.rawName to "50",
            ScoreField.TERM_RATING.rawName to "70"
        )
        assertEquals(50.0, ScoreHistoryValues.primaryValue(scores)!!, 0.0001)
    }

    @Test
    fun termRatingIsUsedAsLastResort() {
        val scores = mapOf(ScoreField.TERM_RATING.rawName to "70")
        assertEquals(70.0, ScoreHistoryValues.primaryValue(scores)!!, 0.0001)
    }

    @Test
    fun blankAndZeroAreSkipped() {
        val scores = mapOf(
            ScoreField.FINAL_RATING.rawName to "",
            ScoreField.CURRENT_SCORE.rawName to "0",
            ScoreField.CURRENT_RATING.rawName to " 44 "
        )
        assertEquals(44.0, ScoreHistoryValues.primaryValue(scores)!!, 0.0001)
    }

    @Test
    fun nonNumericPrimaryFieldIsSkipped() {
        val scores = mapOf(
            ScoreField.FINAL_RATING.rawName to "зачёт",
            ScoreField.CURRENT_SCORE.rawName to "12,5"
        )
        assertEquals(12.5, ScoreHistoryValues.primaryValue(scores)!!, 0.0001)
    }

    @Test
    fun examScoreAndGradeDoNotProducePrimaryValue() {
        val scores = mapOf(
            ScoreField.EXAM_SCORE.rawName to "90",
            ScoreField.GRADE.rawName to "5"
        )
        assertNull(ScoreHistoryValues.primaryValue(scores))
    }

    @Test
    fun returnsNullWhenNothingParses() {
        assertNull(ScoreHistoryValues.primaryValue(emptyMap()))
        assertNull(
            ScoreHistoryValues.primaryValue(
                mapOf(
                    ScoreField.FINAL_RATING.rawName to "—",
                    ScoreField.CURRENT_SCORE.rawName to "",
                    ScoreField.CURRENT_RATING.rawName to "н/а",
                    ScoreField.TERM_RATING.rawName to "0"
                )
            )
        )
    }

    // --- isAbsent ------------------------------------------------------------

    @Test
    fun isAbsentTreatsBlankAndZeroOnly() {
        assertEquals(true, ScoreHistoryValues.isAbsent(""))
        assertEquals(true, ScoreHistoryValues.isAbsent("  "))
        assertEquals(true, ScoreHistoryValues.isAbsent("0"))
        assertEquals(false, ScoreHistoryValues.isAbsent("0.5"))
        assertEquals(false, ScoreHistoryValues.isAbsent("12"))
    }
}
