package ru.nya.nyeios.ui.schedule

import ru.nya.nyeios.data.model.DaySchedule
import java.util.Locale

/** Ячейка ряда дней недели на экране расписания. */
sealed interface DayRowCell {

    /** Обычный день недели; [originalIndex] — индекс в исходном списке для onSelectDay. */
    data class Day(val day: DaySchedule, val originalIndex: Int) : DayRowCell

    /** Действие «напоминания» на месте воскресенья (воскресенье не содержит пар). */
    data object RemindersAction : DayRowCell
}

/**
 * Чистая модель ряда дней: воскресенье не имеет занятий, поэтому его ячейка
 * заменяется действием напоминаний в той же позиции. Индексы остальных дней
 * сохраняются, чтобы onSelectDay продолжал получать исходные индексы.
 *
 * Список дней приходит из парсера портала: обычно 7 дней (Пн..Вс), но бывает
 * и 6 (Пн..Сб). Поэтому воскресенье ищется по имени/заголовку, а не по позиции:
 * если его нет — ячейка действия добавляется в конец ряда.
 */
object DayRowCells {

    private val sundayShortNames = setOf("вс", "sun")
    private val sundayFullNames = setOf("воскресенье", "sunday")
    private val tokenSplit = Regex("""[\s\-,.;:()\[\]]+""")

    fun build(days: List<DaySchedule>): List<DayRowCell> {
        if (days.isEmpty()) return emptyList()

        val cells = ArrayList<DayRowCell>(days.size + 1)
        var actionPlaced = false

        days.forEachIndexed { index, day ->
            if (isSunday(day)) {
                // Воскресенье не отображается как день: на его месте (первое найденное)
                // появляется действие напоминаний. Повторное воскресенье просто пропускаем,
                // чтобы ячейка действия не дублировалась.
                if (!actionPlaced) {
                    cells += DayRowCell.RemindersAction
                    actionPlaced = true
                }
            } else {
                cells += DayRowCell.Day(day = day, originalIndex = index)
            }
        }

        if (!actionPlaced) {
            cells += DayRowCell.RemindersAction
        }

        return cells
    }

    /** Воскресенье по короткому имени («Вс»/«Sun») или по полному названию в заголовке. */
    fun isSunday(day: DaySchedule): Boolean =
        isSundayLabel(day.dayName) || isSundayLabel(day.dayTitle)

    private fun isSundayLabel(raw: String): Boolean {
        val normalized = raw.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return false
        if (normalized in sundayShortNames || normalized in sundayFullNames) return true

        // «Воскресенье - 07.09.2026» / «Sunday 07.09»: проверяем первое слово заголовка.
        val firstToken = tokenSplit.split(normalized).firstOrNull { it.isNotBlank() } ?: return false
        return firstToken in sundayShortNames ||
            firstToken in sundayFullNames ||
            firstToken.startsWith("воскресенье") ||
            firstToken.startsWith("sunday")
    }
}
