package ru.nya.nyeios.data.autoupdate

import ru.nya.nyeios.data.AppLocale
import ru.nya.nyeios.data.changes.CurriculumChanges
import ru.nya.nyeios.data.changes.LessonChangeKind
import ru.nya.nyeios.data.changes.LessonRef
import ru.nya.nyeios.data.changes.ScheduleChanges
import ru.nya.nyeios.data.changes.ScoreChange
import java.util.Locale

/**
 * Чистое форматирование текстов уведомлений автообновления (JVM-тестируемое).
 *
 * Data-слой не имеет доступа к локализованному Activity-контексту, поэтому тексты
 * подбираются через [AppLocale.pick] — формулировки совпадают с ключами `notif_*`
 * из `values/strings.xml` и `values-en/strings.xml`.
 */
object AutoUpdateNotificationText {

    /** Максимальная длина названия пары/предмета в строке уведомления. */
    const val MAX_LINE_LENGTH = 64

    /** Обрезка с хвостовым многоточием: `truncate("abcdef", 4) == "abc…"`. */
    fun truncate(text: String, max: Int = MAX_LINE_LENGTH): String {
        val limit = max.coerceAtLeast(0)
        if (text.length <= limit) return text
        if (limit == 0) return ""
        if (limit == 1) return "…"
        return text.take(limit - 1).trimEnd() + "…"
    }

    /** Строка изменения расписания: `26.07 ЧТ добавлена: Лексика`. */
    fun scheduleLine(change: LessonRef, kind: LessonChangeKind): String {
        val day = change.dayName.uppercase(Locale.getDefault())
        val subject = truncate(change.subject)
        val date = change.dateString
        return when (kind) {
            LessonChangeKind.ADDED ->
                AppLocale.pick("$date $day добавлена: $subject", "$date $day added: $subject")
            LessonChangeKind.REMOVED ->
                AppLocale.pick("$date $day удален(а): $subject", "$date $day removed: $subject")
            LessonChangeKind.MODIFIED ->
                AppLocale.pick("$date $day изменена: $subject", "$date $day changed: $subject")
        }
    }

    /** Строка изменения баллов БРС: `Математика: 5 → 8`. */
    fun scoreLine(change: ScoreChange): String {
        val subject = truncate(change.subject)
        val oldValue = change.oldValue.ifBlank { "—" }
        val newValue = change.newValue.ifBlank { "—" }
        return "$subject: $oldValue → $newValue"
    }

    /**
     * Строки для `NotificationCompat.InboxStyle` (всего не больше [maxLines] строк).
     *
     * Порядок: сначала секция расписания (заголовок + строки по неделям), затем секция БРС.
     * Если изменения не помещаются, последняя строка — `+N ещё`. Пустые секции не показываются.
     */
    fun summaryLines(
        schedule: ScheduleChanges,
        curriculum: CurriculumChanges,
        maxLines: Int = 6
    ): List<String> {
        if (maxLines <= 0) return emptyList()

        val scheduleRows = scheduleRows(schedule)
        val scoreRows = scoreRows(curriculum)
        if (scheduleRows.isEmpty() && scoreRows.isEmpty()) return emptyList()

        val budget = maxLines.coerceAtLeast(1)
        val sections = mutableListOf<Pair<String, List<String>>>()
        if (scheduleRows.isNotEmpty()) sections += sectionScheduleHeader() to scheduleRows
        if (scoreRows.isNotEmpty()) sections += sectionScoresHeader() to scoreRows

        // Совсем мало места: показываем один самый важный факт без заголовка секции.
        if (budget == 1) return listOf(sections.first().second.first())

        // Каждой секции нужно минимум заголовок + одна строка.
        var visible: List<Pair<String, List<String>>> = sections
        while (visible.size > 1 && budget < visible.size * 2) {
            visible = visible.dropLast(1)
        }

        val sectionCount = visible.size
        val rowsBudget = budget - sectionCount
        val totalRows = visible.sumOf { it.second.size }

        if (totalRows <= rowsBudget) {
            return visible.flatMap { (header, rows) -> listOf(header) + rows }
        }

        // Есть переполнение. Если остаётся лишняя строка — отдаём её под «+N ещё».
        val withOverflow = rowsBudget > sectionCount
        val rowsAllowance = if (withOverflow) rowsBudget - 1 else rowsBudget
        val allocation = allocate(visible.map { it.second.size }, rowsAllowance)

        val result = mutableListOf<String>()
        var shown = 0
        visible.forEachIndexed { index, (header, rows) ->
            result += header
            val part = rows.take(allocation[index])
            result += part
            shown += part.size
        }
        val hidden = totalRows - shown
        if (withOverflow && hidden > 0) result += moreLine(hidden)
        return result
    }

    fun sectionScheduleHeader(): String =
        AppLocale.pick("Изменения расписания:", "Schedule changes:")

    fun sectionScoresHeader(): String =
        AppLocale.pick("Изменения БРС:", "Grade changes:")

    fun resultTitle(hasChanges: Boolean): String = if (hasChanges) {
        AppLocale.pick("NyEIOS: есть изменения", "NyEIOS: new changes")
    } else {
        AppLocale.pick("NyEIOS обновлён", "NyEIOS updated")
    }

    /** Проценты прогресс-нотификации для каждой стадии цикла. */
    fun progressPercentFor(stage: AutoUpdateStage): Int = when (stage) {
        AutoUpdateStage.START -> 5
        AutoUpdateStage.SCHEDULE -> 45
        AutoUpdateStage.NEXT_WEEK -> 65
        AutoUpdateStage.CURRICULUM -> 85
        AutoUpdateStage.COMPARE -> 100
    }

    private fun moreLine(count: Int): String =
        AppLocale.pick("+$count ещё", "+$count more")

    private fun scheduleRows(schedule: ScheduleChanges): List<String> {
        val result = mutableListOf<String>()
        // Сортировка по ключу недели: порядок строк детерминирован и проверяем в тестах.
        schedule.weeks.toSortedMap().values.forEach { week ->
            week.added.forEach { result += scheduleLine(it, LessonChangeKind.ADDED) }
            week.modified.forEach { result += scheduleLine(it, LessonChangeKind.MODIFIED) }
            week.removed.forEach { result += scheduleLine(it, LessonChangeKind.REMOVED) }
        }
        return result
    }

    private fun scoreRows(curriculum: CurriculumChanges): List<String> {
        val result = mutableListOf<String>()
        curriculum.terms.toSortedMap().values.forEach { term ->
            term.scoreChanges.forEach { result += scoreLine(it) }
        }
        return result
    }

    /**
     * Делит [budget] строк между секциями максимально ровно, не превышая [capacities].
     * Сначала каждой непустой секции гарантируется строка, остаток раздаётся по кругу.
     */
    private fun allocate(capacities: List<Int>, budget: Int): IntArray {
        val allocation = IntArray(capacities.size)
        var remaining = budget.coerceIn(0, capacities.sum())
        for (index in capacities.indices) {
            if (remaining <= 0) break
            if (capacities[index] > 0) {
                allocation[index] = 1
                remaining--
            }
        }
        while (remaining > 0) {
            var progressed = false
            for (index in capacities.indices) {
                if (remaining <= 0) break
                if (allocation[index] < capacities[index]) {
                    allocation[index]++
                    remaining--
                    progressed = true
                }
            }
            if (!progressed) break
        }
        return allocation
    }
}
