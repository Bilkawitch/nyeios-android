package ru.nya.nyeios.data.model

import java.util.Locale

/**
 * Пара из публичного расписания вуза (gukolomna.ru/studentu/schedule).
 * Нужна только для сверки: если такая же пара есть у соседней группы, значит она потоковая.
 */
data class PublicLesson(
    val dayIndex: Int = 0,
    val time: String = "",
    val subject: String = "",
    val room: String = ""
)

/** Учебная группа из публичного расписания: id, факультет и форма обучения для фильтрации потока. */
data class PublicGroupInfo(
    val name: String = "",
    val compoundKey: String = "",
    val facultyName: String = "",
    val facultyUid: String = "",
    val eduFormName: String = "",
    val eduFormUid: String = ""
)

/**
 * Состояние сверки одной недели с расписанием соседних групп.
 *
 * @param streamLessonKeys ключи пар, которые нашлись хотя бы у одной соседней группы.
 * @param isComplete true, когда опрошены все соседние группы; только тогда отсутствие ключа
 *   означает, что пара идёт только для своей группы.
 */
data class FlowWeekState(
    val weekKey: String = "",
    val streamLessonKeys: Set<String> = emptySet(),
    val isComplete: Boolean = false,
    val fetchedGroups: Int = 0,
    val expectedGroups: Int = 0
)

/**
 * Устойчивый ключ пары для сверки расписаний из разных источников.
 * Матчим по дню и времени, а из предмета/аудитории берём то, что надёжно есть в обоих
 * источниках: аудиторию, иначе название дисциплины.
 */
object PublicLessonKey {

    fun of(dayIndex: Int, time: String, subject: String, room: String): String {
        val day = dayIndex.toString()
        val normalizedTime = canonical(time)
        val normalizedRoom = canonicalRoom(room)
        return if (normalizedRoom.isNotEmpty()) {
            "$day|$normalizedTime|$normalizedRoom"
        } else {
            "$day|$normalizedTime|${canonical(subject)}"
        }
    }

    fun canonical(value: String): String =
        value.trim()
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace(Regex("""\s+"""), " ")

    fun canonicalRoom(room: String): String =
        canonical(room).removePrefix("ауд.").trim()
}

/**
 * Кандидаты в поток: группы того же курса (год — две первые цифры номера группы), того же
 * факультета и той же формы обучения.
 *
 * Какие из кандидатов реально идут одним потоком, решается не по названию группы, а по
 * фактическому совпадению пар: совпавшая пара (день + время + аудитория) означает, что две
 * группы сидят в одной аудитории в одно время, то есть учатся вместе. Поэтому 23ПП-о-41
 * остаётся кандидатом для 23АН-о-41, но потоковой для него не станет, пока у групп нет общих
 * пар — и точно так же приложение работает для студента из 23ПП.
 */
object FlowGroupMatcher {

    /** Ограничение на число дополнительных запросов за неделю. */
    const val MAX_SIBLINGS = 8

    fun yearPrefix(groupName: String): String? {
        val canonical = groupName.trim().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        if (canonical.length < 2) return null
        if (!canonical[0].isDigit() || !canonical[1].isDigit()) return null
        return canonical.take(2)
    }

    fun siblings(own: PublicGroupInfo, all: List<PublicGroupInfo>): List<PublicGroupInfo> {
        val year = yearPrefix(own.name) ?: return emptyList()
        return all.asSequence()
            .filter { it.name != own.name }
            .filter { yearPrefix(it.name) == year }
            .filter { it.facultyUid == own.facultyUid && it.eduFormUid == own.eduFormUid }
            .filter { it.compoundKey.isNotEmpty() }
            .sortedBy { it.name }
            .take(MAX_SIBLINGS)
            .toList()
    }
}
