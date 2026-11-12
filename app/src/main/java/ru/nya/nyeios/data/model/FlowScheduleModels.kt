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
 * Состав потока: группы того же курса (первые две цифры номера группы), того же факультета
 * и той же формы обучения. Для 23АН-о-41 это 23АК-о-41, 23АФ-о-41 и 23ПП-о-41.
 */
object FlowGroupMatcher {

    /** Ограничение на число дополнительных запросов за неделю. */
    const val MAX_SIBLINGS = 8

    fun yearPrefix(groupName: String): String? {
        val prefix = groupName.trim().take(2)
        return prefix.takeIf { it.length == 2 && it.all(Char::isDigit) }
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
