package ru.nya.nyeios.data.changes

import ru.nya.nyeios.data.model.CurriculumSubject
import ru.nya.nyeios.data.model.CurriculumTerm
import ru.nya.nyeios.data.model.LessonItem
import ru.nya.nyeios.data.model.WeekSchedule
import ru.nya.nyeios.data.parser.ScheduleParser

/**
 * Чистый (без Android API) diff-движок подсистемы «что нового».
 *
 * Сравнивает baseline (то, что пользователь уже видел) с latest (последним снимком сервера).
 * Движок не хранит состояние и не накапливает дельты: каждый вызов — полное сравнение
 * baseline -> latest, поэтому серия обновлений 5 -> 6 -> 7 -> 8 всегда даёт diff 5 -> 8.
 */
object ChangesDiff {

    /** Пара в снимке недели. */
    data class LessonSnapshot(
        val weekKey: String,
        val dateString: String,
        val dayName: String,
        val lessonNumber: String,
        val subgroup: String,
        val time: String,
        val subject: String,
        val room: String,
        val teacher: String,
        val type: String
    )

    data class ScheduleSnapshot(val weeks: Map<String, List<LessonSnapshot>> = emptyMap())

    /** Предмет с баллами БРС в снимке семестра. */
    data class SubjectSnapshot(
        val termNum: Int,
        val subject: String,
        val controlType: String,
        val scores: Map<String, String>   // rawName ScoreField -> значение
    )

    data class CurriculumSnapshot(val subjects: List<SubjectSnapshot> = emptyList())

    /**
     * Diff расписания. Возвращает только недели, где реально есть изменения.
     * Если baseline отсутствует — изменений нет (первая загрузка молчаливая).
     * Неделя, которой нет в baseline, тоже не даёт изменений: её тихо зафиксирует репозиторий.
     */
    fun scheduleChanges(
        baseline: ScheduleSnapshot?,
        latest: ScheduleSnapshot
    ): Map<String, WeekChanges> {
        if (baseline == null) return emptyMap()

        val result = LinkedHashMap<String, WeekChanges>()
        for ((weekKey, latestLessons) in latest.weeks) {
            val baselineLessons = baseline.weeks[weekKey] ?: continue
            val weekChanges = diffWeek(weekKey, baselineLessons, latestLessons)
            if (weekChanges.hasChanges) result[weekKey] = weekChanges
        }
        return result
    }

    /**
     * Diff БРС. Сравниваются только предметы, существующие в baseline (subject + controlType);
     * предмет, пришедший только в latest, не считается изменением баллов.
     * Возвращает только семестры с непустыми изменениями.
     */
    fun curriculumChanges(
        baseline: CurriculumSnapshot?,
        latest: CurriculumSnapshot
    ): Map<Int, TermChanges> {
        if (baseline == null) return emptyMap()

        val baselineByKey = baseline.subjects.associateBy {
            subjectKey(it.termNum, it.subject, it.controlType)
        }
        val baselineTermNums = baseline.subjects.mapTo(HashSet()) { it.termNum }
        val byTerm = LinkedHashMap<Int, MutableList<ScoreChange>>()

        for (latestSubject in latest.subjects) {
            if (latestSubject.termNum !in baselineTermNums) continue
            val baselineSubject = baselineByKey[
                subjectKey(latestSubject.termNum, latestSubject.subject, latestSubject.controlType)
            ] ?: continue

            for (field in ScoreField.entries) {
                val oldValue = baselineSubject.scores[field.rawName].orEmpty()
                val newValue = latestSubject.scores[field.rawName].orEmpty()
                if (oldValue == newValue) continue
                byTerm.getOrPut(latestSubject.termNum) { mutableListOf() } += ScoreChange(
                    termNum = latestSubject.termNum,
                    subject = latestSubject.subject,
                    controlType = latestSubject.controlType,
                    field = field,
                    oldValue = oldValue,
                    newValue = newValue
                )
            }
        }

        val result = LinkedHashMap<Int, TermChanges>()
        for ((termNum, changes) in byTerm) {
            if (changes.isNotEmpty()) result[termNum] = TermChanges(termNum, changes)
        }
        return result
    }

    /** Снимок одной недели расписания (merge нескольких недель делает репозиторий). */
    fun scheduleSnapshot(schedule: WeekSchedule): ScheduleSnapshot {
        val weekKey = ScheduleChanges.weekKey(schedule.startDate, schedule.endDate)
        val lessons = ArrayList<LessonSnapshot>()
        for (day in schedule.days) {
            for (lesson in day.lessons) {
                lessons += LessonSnapshot(
                    weekKey = weekKey,
                    dateString = day.dateString,
                    dayName = day.dayName,
                    lessonNumber = normalizeLessonNumber(lesson),
                    subgroup = lesson.subgroup,
                    time = lesson.time,
                    subject = lesson.subject,
                    room = lesson.room,
                    teacher = lesson.teacher,
                    type = lesson.type.name
                )
            }
        }
        return ScheduleSnapshot(mapOf(weekKey to lessons))
    }

    /** Снимок БРС по всем переданным семестрам. */
    fun curriculumSnapshot(terms: List<CurriculumTerm>): CurriculumSnapshot {
        val subjects = ArrayList<SubjectSnapshot>()
        for (term in terms) {
            for (subject in term.subjects) {
                val scores = LinkedHashMap<String, String>()
                for (field in ScoreField.entries) {
                    scores[field.rawName] = subject.scoreValue(field)
                }
                subjects += SubjectSnapshot(
                    termNum = term.termNum,
                    subject = subject.subject,
                    controlType = subject.controlType,
                    scores = scores
                )
            }
        }
        return CurriculumSnapshot(subjects)
    }

    // --- internal ------------------------------------------------------------

    private fun diffWeek(
        weekKey: String,
        baselineLessons: List<LessonSnapshot>,
        latestLessons: List<LessonSnapshot>
    ): WeekChanges {
        val baselineByKey = baselineLessons.associateBy { lessonKey(it) }
        val latestByKey = latestLessons.associateBy { lessonKey(it) }

        val added = ArrayList<LessonRef>()
        val modified = ArrayList<LessonRef>()
        val removed = ArrayList<LessonRef>()

        for ((key, latestLesson) in latestByKey) {
            val baselineLesson = baselineByKey[key]
            if (baselineLesson == null) {
                added += latestLesson.toLessonRef(weekKey)
            } else if (isModified(baselineLesson, latestLesson)) {
                modified += latestLesson.toLessonRef(weekKey)
            }
        }
        for ((key, baselineLesson) in baselineByKey) {
            if (key !in latestByKey) removed += baselineLesson.toLessonRef(weekKey)
        }

        return WeekChanges(
            weekKey = weekKey,
            added = added,
            modified = modified,
            removed = removed
        )
    }

    private fun lessonKey(lesson: LessonSnapshot): String =
        LessonKeys.of(lesson.dateString, lesson.lessonNumber, lesson.subgroup)

    private fun subjectKey(termNum: Int, subject: String, controlType: String): String =
        "$termNum|$subject|$controlType"

    private fun isModified(baseline: LessonSnapshot, latest: LessonSnapshot): Boolean =
        baseline.subject != latest.subject ||
            baseline.room != latest.room ||
            baseline.teacher != latest.teacher ||
            baseline.type != latest.type ||
            baseline.time != latest.time

    private fun LessonSnapshot.toLessonRef(weekKey: String): LessonRef = LessonRef(
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

    /**
     * Стабильная нормализация номера пары — ровно та же, что делает кэш
     * (`EiosRepository.sanitizeCachedSchedule`): валидный номер 1..8 сохраняется, иначе
     * он выводится из времени. Без этого diff-ключи кэша и сети расходились бы.
     */
    private fun normalizeLessonNumber(lesson: LessonItem): String =
        normalizedLessonNumber(lesson.lessonNumber, lesson.time)

    /**
     * Нормализация номера пары, общая для diff-движка, кэша и UI.
     * UI обязан собирать ключ через неё, иначе зелёная точка не совпадёт с записью diff.
     */
    fun normalizedLessonNumber(lessonNumber: String, time: String): String {
        val number = lessonNumber.toIntOrNull()
        return if (number != null && number in 1..8 && !lessonNumber.startsWith("0")) {
            lessonNumber
        } else {
            ScheduleParser.inferLessonNumber(time)
        }
    }

    private fun CurriculumSubject.scoreValue(field: ScoreField): String = when (field) {
        ScoreField.CURRENT_SCORE -> currentScore
        ScoreField.CURRENT_RATING -> currentRating
        ScoreField.TERM_RATING -> termRating
        ScoreField.EXAM_SCORE -> examScore
        ScoreField.FINAL_RATING -> finalRating
        ScoreField.GRADE -> grade
    }
}
