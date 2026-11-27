package ru.nya.nyeios.data.history

import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Генератор синтетической истории баллов для проверки графиков на эмуляторе/устройстве.
 *
 * Используется только debug-хуком `nyeios_debug_seed_history` из MainActivity: реальные
 * данные пишутся автоматически при каждой загрузке БРС. Точки вставляются через штатный
 * импорт ([ScoreHistoryRepository.importJson]), поэтому ни одна продуктовая ветка записи
 * не обходится.
 */
object DebugHistorySeeder {

    private const val TAG = "NyEIOS-History"

    private data class SeededSubject(
        val subject: String,
        val controlType: String,
        val startScore: Double,
        val dailyDelta: Double,
        val wobble: Double
    )

    /**
     * Наполняет историю за [days] календарных дней по два снимка в день (утро/вечер),
     * чтобы у свечей были тела (open/high/low/close отличаются).
     *
     * @return число добавленных точек (0 — история уже непустая или запись не удалась).
     */
    fun seedIfEmpty(context: Context, days: Int = 30): Int {
        val repository = ScoreHistoryRepository.getInstance(context)
        if (repository.stats().totalPoints > 0) {
            Log.i(TAG, "debug seed skipped: history already has points")
            return 0
        }

        val group = repository.exportPayload().group.ifBlank { "23АН-о-41" }
        val now = System.currentTimeMillis()
        val dayMs = TimeUnit.DAYS.toMillis(1)
        val subjects = listOf(
            SeededSubject(
                subject = "Практика устной и письменной речи английского языка",
                controlType = "Зачет с оценкой",
                startScore = 58.0,
                dailyDelta = 1.05,
                wobble = 2.4
            ),
            SeededSubject(
                subject = "Литература немецкоязычных стран",
                controlType = "Экзамен",
                startScore = 88.0,
                dailyDelta = -0.85,
                wobble = 2.0
            ),
            SeededSubject(
                subject = "Стилистика английского языка",
                controlType = "Зачет",
                startScore = 70.0,
                dailyDelta = 0.06,
                wobble = 3.2
            )
        )

        val points = ArrayList<ScoreHistoryPoint>(days * 2 * subjects.size)
        for (dayOffset in 0 until days) {
            val dayIndex = (days - 1) - dayOffset
            val morning = now - dayIndex * dayMs - TimeUnit.HOURS.toMillis(8)
            val evening = now - dayIndex * dayMs - TimeUnit.HOURS.toMillis(2)
            subjects.forEachIndexed { subjectIndex, seeded ->
                for ((slotIndex, atMs) in longArrayOf(morning, evening).withIndex()) {
                    val base = seeded.startScore + seeded.dailyDelta * (days - dayIndex)
                    val wobble = seeded.wobble * kotlin.math.sin(
                        (dayIndex * 1.7 + subjectIndex * 2.3 + slotIndex * 1.1).toDouble()
                    )
                    val value = (base + wobble).coerceIn(0.0, 100.0)
                    points += ScoreHistoryPoint(
                        recordedAtMs = atMs,
                        group = group,
                        termNum = 7,
                        subject = seeded.subject,
                        controlType = seeded.controlType,
                        scores = mapOf(
                            "finalRating" to formatValue(value),
                            "currentScore" to "",
                            "currentRating" to "",
                            "termRating" to "",
                            "examScore" to "",
                            "grade" to ""
                        )
                    )
                }
            }
        }

        val payload = ScoreHistoryCodec.ExportPayload(
            schema = ScoreHistoryCodec.SCHEMA_VERSION,
            app = ScoreHistoryCodec.APP_NAME,
            exportedAtMs = now,
            group = group,
            points = points
        )
        val imported = runCatching { repository.importJson(ScoreHistoryCodec.encode(payload)) }
            .getOrElse { error ->
                Log.i(TAG, "debug seed failed: ${error.message}")
                return 0
            }
        Log.i(TAG, "debug seed added=${imported.added} duplicates=${imported.duplicates}")
        return imported.added
    }

    private fun formatValue(value: Double): String =
        String.format(java.util.Locale.ROOT, "%.1f", value).trimEnd('0').trimEnd('.')
}
