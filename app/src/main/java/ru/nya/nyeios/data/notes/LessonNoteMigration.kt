package ru.nya.nyeios.data.notes

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Результат миграции загруженных заметок.
 *
 * @property notes итоговый список (ключ каждой заметки выведен из её собственных полей)
 * @property removedDuplicates заметки, признанные дублями, созданными багом размножения
 * @property rekeyedCount сколько заметок получили исправленный ключ
 */
data class LessonNoteMigrationResult(
    val notes: List<LessonNote>,
    val removedDuplicates: List<LessonNote>,
    val rekeyedCount: Int
) {
    val changed: Boolean
        get() = rekeyedCount > 0 || removedDuplicates.isNotEmpty()
}

/**
 * Защитная миграция заметок при загрузке с диска.
 *
 * 1. Ключ каждой заметки пересчитывается из её собственных полей ([LessonNoteKey]).
 *    Если сохранённый ключ не совпадает с каноническим (старая нормализация, ключ,
 *    записанный под чужую пару), заметка переносится под правильный ключ — данные
 *    не удаляются.
 * 2. Одинаковые по содержимому заметки, размноженные старым багом «Презентация по
 *    чуме», схлопываются: остаётся самая ранняя по `updatedAt`. Правило намеренно
 *    узкое и не может удалить уникальный контент:
 *    - `rawText` непустой и полностью совпадает, `plainSnippet` совпадает;
 *    - очищенный `plainSnippet` не короче [MIN_DEDUPE_SNIPPET_LENGTH] (короткие пометки
 *      вроде «нет» или «см. лекцию» пользователь ставит на разные пары осознанно);
 *    - у заметок нет фотографий (фото-вложения не трогаем никогда);
 *    - заметки относятся к одной календарной неделе (дата dd.MM);
 *    - ключи пары различаются (то есть это реально разные пары, а не одна запись).
 *    Удалённые дубли не уничтожаются: репозиторий складывает их в
 *    `files/lesson_notes_duplicates.json`.
 */
object LessonNoteMigration {

    /**
     * Минимальная длина очищенного текста, начиная с которой одинаковые записи считаются
     * размноженными багом, а не осознанно повторёнными пользователем. Порог 16 символов
     * выбран с запасом над короткими служебными пометками, которые пользователи ставят
     * на разные пары намеренно: «нет» (3), «нет дз» (6), «см. лекцию» (10). Такие записи
     * не схлопываются никогда, а реальное размноженное ДЗ из отчёта («Презентация по
     * чуме», 18 символов) — схлопывается. Риск потерять настоящую связь заметки с парой
     * выше, чем польза от автоочистки коротких пометок.
     */
    private const val MIN_DEDUPE_SNIPPET_LENGTH = 16

    private const val WEEK_REFERENCE_YEAR = 2000
    private val DATE_PARTS = Regex("""(\d{1,2})\.(\d{1,2})""")

    fun migrate(notes: List<LessonNote>): LessonNoteMigrationResult {
        var rekeyed = 0
        val byKey = LinkedHashMap<String, LessonNote>()

        for (raw in notes) {
            val canonicalKey = LessonNoteKey.buildKey(raw.date, raw.time, raw.lessonNumber, raw.subject)
            if (raw.key != canonicalKey) rekeyed++
            val repaired = raw.copy(key = canonicalKey)
            val existing = byKey[canonicalKey]
            byKey[canonicalKey] = when {
                existing == null -> repaired
                repaired.updatedAt >= existing.updatedAt -> repaired
                else -> existing
            }
        }

        val grouped = LinkedHashMap<ContentSignature, MutableList<LessonNote>>()
        for (note in byKey.values) {
            val signature = ContentSignature.of(note) ?: continue
            grouped.getOrPut(signature) { mutableListOf() }.add(note)
        }

        val removed = mutableListOf<LessonNote>()
        val keptKeys = byKey.keys.toMutableSet()
        for ((_, group) in grouped) {
            if (group.size <= 1) continue
            val ordered = group.sortedWith(compareBy({ it.updatedAt }, { it.key }))
            for (duplicate in ordered.drop(1)) {
                removed.add(duplicate)
                keptKeys.remove(duplicate.key)
            }
        }

        val kept = byKey.values.filter { it.key in keptKeys }
        return LessonNoteMigrationResult(
            notes = kept,
            removedDuplicates = removed,
            rekeyedCount = rekeyed
        )
    }

    /** Подпись одинакового содержимого для безопасного схлопывания дублей. */
    private data class ContentSignature(
        val weekStart: String,
        val rawText: String,
        val plainSnippet: String
    ) {
        companion object {
            fun of(note: LessonNote): ContentSignature? {
                if (note.rawText.isBlank()) return null
                if (note.plainSnippet.trim().length < MIN_DEDUPE_SNIPPET_LENGTH) return null
                if (note.photoAttachments.isNotEmpty()) return null
                val weekStart = weekStartOf(note.date) ?: return null
                return ContentSignature(
                    weekStart = weekStart,
                    rawText = note.rawText,
                    plainSnippet = note.plainSnippet
                )
            }

            private fun weekStartOf(date: String): String? {
                val canonical = LessonNoteKey.canonicalDate(date)
                val match = DATE_PARTS.find(canonical) ?: return null
                val day = match.groupValues[1].toIntOrNull() ?: return null
                val month = match.groupValues[2].toIntOrNull() ?: return null
                if (month !in 1..12 || day !in 1..31) return null
                val parsed = try {
                    LocalDate.of(WEEK_REFERENCE_YEAR, month, day)
                } catch (_: Exception) {
                    return null
                }
                return parsed.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
            }
        }
    }
}
