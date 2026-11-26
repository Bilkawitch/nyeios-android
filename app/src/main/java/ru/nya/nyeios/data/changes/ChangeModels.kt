package ru.nya.nyeios.data.changes

/** Вид изменения пары в расписании. */
enum class LessonChangeKind { ADDED, MODIFIED, REMOVED }

/** Ссылка на пару в неделе — самодостаточный снимок для UI и уведомлений. */
data class LessonRef(
    val weekKey: String,
    val dateString: String,      // "15.09" из DaySchedule.dateString
    val dayName: String,         // "Пн"
    val lessonNumber: String,    // LessonItem.lessonNumber (нормализованный)
    val subgroup: String,        // LessonItem.subgroup
    val time: String,            // LessonItem.time
    val subject: String,
    val room: String,
    val teacher: String,
    val type: String             // LessonType.name
)

/**
 * Ключ пары внутри недели: дата + номер пары + подгруппа.
 *
 * Вынесен в публичный объект, чтобы UI и уведомления собирали ключ ровно так же,
 * как diff-движок (никаких расхождений в формате).
 */
object LessonKeys {
    fun of(dateString: String, lessonNumber: String, subgroup: String): String =
        "$dateString|$lessonNumber|$subgroup"
}

/** Изменения одной недели расписания. */
data class WeekChanges(
    val weekKey: String,
    val added: List<LessonRef> = emptyList(),
    val modified: List<LessonRef> = emptyList(),
    val removed: List<LessonRef> = emptyList()
) {
    /** Ключи пар для зелёной точки на карточке: добавленные и изменённые. */
    val markedLessonKeys: Set<String>
        get() = (added + modified).mapTo(LinkedHashSet()) {
            LessonKeys.of(it.dateString, it.lessonNumber, it.subgroup)
        }

    /** Даты (dateString) дней, где есть любые изменения (для точки на дне недели). */
    val changedDayDates: Set<String>
        get() = (added + modified + removed).mapTo(LinkedHashSet()) { it.dateString }

    val hasChanges: Boolean
        get() = added.isNotEmpty() || modified.isNotEmpty() || removed.isNotEmpty()

    fun isLessonMarked(dateString: String, lessonNumber: String, subgroup: String): Boolean =
        LessonKeys.of(dateString, lessonNumber, subgroup) in markedLessonKeys

    fun isDayMarked(dateString: String): Boolean = dateString in changedDayDates
}

/** Все непросмотренные изменения расписания (по неделям). */
data class ScheduleChanges(
    val weeks: Map<String, WeekChanges> = emptyMap()
) {
    fun forWeek(startDate: String, endDate: String): WeekChanges? = weeks[weekKey(startDate, endDate)]

    /** Для бейджа на кнопке «Расписание». */
    fun isWeekMarked(startDate: String, endDate: String): Boolean =
        forWeek(startDate, endDate)?.hasChanges == true

    val totalChanges: Int
        get() = weeks.values.sumOf { it.added.size + it.modified.size + it.removed.size }

    val hasChanges: Boolean
        get() = totalChanges > 0

    companion object {
        /** Единый формат ключа недели: "<startDate>_<endDate>" в формате dd.MM.yyyy. */
        fun weekKey(startDate: String, endDate: String): String = "${startDate}_${endDate}"
    }
}

/** Одно изменившееся числовое поле БРС. */
data class ScoreChange(
    val termNum: Int,
    val subject: String,
    val controlType: String,     // CurriculumSubject.controlType
    val field: ScoreField,
    val oldValue: String,        // может быть пустым ("")
    val newValue: String
)

enum class ScoreField(val rawName: String) {
    CURRENT_SCORE("currentScore"),
    CURRENT_RATING("currentRating"),
    TERM_RATING("termRating"),
    EXAM_SCORE("examScore"),
    FINAL_RATING("finalRating"),
    GRADE("grade");
}

/** Изменения баллов одного семестра. */
data class TermChanges(
    val termNum: Int,
    val scoreChanges: List<ScoreChange> = emptyList()
) {
    val hasChanges: Boolean
        get() = scoreChanges.isNotEmpty()

    fun forSubject(subject: String, controlType: String): List<ScoreChange> =
        scoreChanges.filter { it.subject == subject && it.controlType == controlType }
}

/** Все непросмотренные изменения БРС (по семестрам). */
data class CurriculumChanges(
    val terms: Map<Int, TermChanges> = emptyMap()
) {
    fun forTerm(termNum: Int): TermChanges? = terms[termNum]

    val totalChanges: Int
        get() = terms.values.sumOf { it.scoreChanges.size }

    val hasChanges: Boolean
        get() = totalChanges > 0
}
