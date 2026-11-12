package ru.nya.nyeios.data.model

import java.util.Locale

/**
 * Кому принадлежит пара в расписании.
 *
 * - [SUBGROUP] — пара только для одной подгруппы (группы): портал пишет «Подгруппа N».
 * - [GROUP] — пара для всей своей группы (всех её подгрупп), но не для потока.
 * - [STREAM] — пара для всего потока (всех групп года), подтверждается расписанием соседних групп.
 *
 * В разметке ЭИОС потоковые и групповые пары выглядят одинаково (пустая строка подгруппы),
 * поэтому GROUP/STREAM различаются только сверкой с публичным расписанием соседних групп.
 */
enum class LessonScope {
    SUBGROUP,
    GROUP,
    STREAM
}

object LessonScopeParser {

    private val DIGITS = Regex("""\d{1,2}""")

    /** Маркеры потока на случай, если портал начнёт писать их в строку подгруппы. */
    private val STREAM_MARKERS = listOf("поток", "все подгруппы", "все группы", "весь курс")

    /**
     * Номер подгруппы из текста портала: «Подгруппа 1», «1 подгруппа», «1 п/г».
     * Возвращает null, если номера нет или он не похож на номер подгруппы (1..9).
     */
    fun subgroupNumber(subgroup: String): Int? =
        DIGITS.find(subgroup)?.value?.toIntOrNull()?.takeIf { it in 1..9 }

    fun isStreamMarker(subgroup: String): Boolean {
        val clean = subgroup.trim().lowercase(Locale.ROOT)
        return clean.isNotEmpty() && STREAM_MARKERS.any { clean.contains(it) }
    }
}

/**
 * Определение уровня пары. Для пар с отметкой подгруппы достаточно текста портала,
 * для пар без отметки нужен результат сверки с соседними группами.
 */
object LessonScopeResolver {

    /**
     * @param dayIndex индекс дня недели в расписании (0 — понедельник).
     * @param flow состояние сверки недели; null — сверка ещё не дала данных.
     * @return null, если уровень пока неизвестен (ждём тихую досверку).
     */
    fun scopeOf(lesson: LessonItem, dayIndex: Int, flow: FlowWeekState?): LessonScope? {
        val subgroup = lesson.subgroup.trim()
        if (subgroup.isNotEmpty()) {
            return if (LessonScopeParser.isStreamMarker(subgroup)) {
                LessonScope.STREAM
            } else {
                LessonScope.SUBGROUP
            }
        }
        if (flow == null) return null
        val key = PublicLessonKey.of(dayIndex, lesson.time, lesson.subject, lesson.room)
        return when {
            key in flow.streamLessonKeys -> LessonScope.STREAM
            flow.isComplete -> LessonScope.GROUP
            else -> null
        }
    }
}

/**
 * Короткий код группы для подписи подгруппы: «23АН-о-41» → «АН».
 * Нужен, чтобы подгруппы разных групп потока не путались: «АФ подгруппа 1».
 */
object GroupShortName {

    private val TWO_LETTERS = Regex("""[А-Яа-яЁёA-Za-z]{2}""")

    fun of(group: String?): String =
        TWO_LETTERS.find(group.orEmpty())?.value?.uppercase(Locale.ROOT).orEmpty()
}
