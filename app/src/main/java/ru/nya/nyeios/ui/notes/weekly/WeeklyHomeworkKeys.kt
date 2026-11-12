package ru.nya.nyeios.ui.notes.weekly

import ru.nya.nyeios.data.model.DaySchedule
import ru.nya.nyeios.data.model.LessonItem
import ru.nya.nyeios.data.model.WeekSchedule

/**
 * Ключи элементов LazyColumn еженедельной ведомости ДЗ.
 *
 * Compose требует уникальности ключей в пределах списка: повторный ключ валит процесс
 * с `IllegalArgumentException: Key "..." was already used`. Кортеж
 * «дата + время + номер пары + дисциплина» уникальным не является: портал может отдать
 * одну и ту же дисциплину в одной паре дважды — например, две подгруппы иностранного
 * языка с одинаковым названием предмета, различающиеся только преподавателем,
 * аудиторией и подписью подгруппы. Поэтому ключ пары дополнительно содержит её
 * порядковый индекс внутри дня.
 */
internal object WeeklyHomeworkKeys {

    fun week(week: WeekSchedule): String = "week_${week.startDate}"

    fun day(week: WeekSchedule, day: DaySchedule): String =
        "day_${week.startDate}_${day.dayTitle}"

    fun lesson(
        week: WeekSchedule,
        day: DaySchedule,
        lesson: LessonItem,
        index: Int
    ): String =
        "lesson_${week.startDate}_${day.dateString}_${lesson.time}_${lesson.subject}_${lesson.lessonNumber}_$index"
}
