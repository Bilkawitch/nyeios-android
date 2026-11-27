package ru.nya.nyeios.data.history

import ru.nya.nyeios.data.changes.ScoreField

/** Одна точка истории: снимок баллов предмета на момент времени. */
data class ScoreHistoryPoint(
    val recordedAtMs: Long,
    val group: String,          // учебная группа, напр. "23АН-о-41" (может быть "")
    val termNum: Int,
    val subject: String,
    val controlType: String,
    val scores: Map<String, String>   // ScoreField.rawName -> сырое значение с портала
)

/** Идентификатор серии (предмет в семестре). */
data class ScoreSeriesKey(
    val group: String,
    val termNum: Int,
    val subject: String,
    val controlType: String
)

data class HistoryImportResult(
    val added: Int,
    val duplicates: Int,
    val invalid: Int
)

data class HistoryStats(
    val totalPoints: Int,
    val subjectsCount: Int,
    val firstAtMs: Long?,
    val lastAtMs: Long?
)

/**
 * Перевод сырых баллов в число для графиков.
 *
 * Приоритет полей зеркалит уже отображаемый в UI балл
 * (`NycCurriculumScreen` / `CurriculumScreen`): finalRating -> currentScore ->
 * currentRating -> termRating. `examScore` и `grade` — оценки, а не «баллы»,
 * поэтому в графики они не попадают (но хранятся в снимке `scores`).
 */
object ScoreHistoryValues {

    /** Поля в порядке приоритета для «баллов» на графике. */
    val PRIMARY_FIELDS: List<String> = listOf(
        ScoreField.FINAL_RATING.rawName,
        ScoreField.CURRENT_SCORE.rawName,
        ScoreField.CURRENT_RATING.rawName,
        ScoreField.TERM_RATING.rawName
    )

    /**
     * null — если ни одно приоритетное поле не парсится как число.
     *
     * Значения `""` и `"0"` считаются отсутствующими (как в UI): поле пропускается.
     */
    fun primaryValue(scores: Map<String, String>): Double? {
        for (field in PRIMARY_FIELDS) {
            val raw = scores[field] ?: continue
            if (isAbsent(raw)) continue
            parseNumber(raw)?.let { return it }
        }
        return null
    }

    /** "12,5" / "12.5" / " 8 " -> 12.5 / 12.5 / 8.0; иначе null. "0" -> 0.0. */
    fun parseNumber(raw: String): Double? {
        val normalized = raw
            .replace('\u00A0', ' ')
            .replace(',', '.')
            .replace("%", "")
            .trim()
        if (normalized.isEmpty()) return null
        return normalized.toDoubleOrNull()
    }

    /** Значение считается отсутствующим, если оно пустое или ровно "0" (как в UI). */
    fun isAbsent(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.isEmpty() || trimmed == "0"
    }
}
