package ru.nya.nyeios.data.model

import java.util.Locale

/**
 * Кому принадлежит пара в расписании группы.
 *
 * Портал помечает подгрупповые пары строкой вида «Подгруппа 1»; у лекций для всего
 * потока (всех групп и подгрупп) эта строка пустая. Отдельной сущности «поток» в
 * разметке нет, поэтому пустая отметка трактуется как потоковая пара.
 */
enum class LessonScope {
    /** Пара только для одной подгруппы (группы): в разметке есть номер подгруппы. */
    SUBGROUP,

    /** Пара для всего потока: подгруппа в разметке не указана. */
    STREAM
}

object LessonScopeParser {

    /** Маркеры потока на случай, если портал начнёт писать их в строку подгруппы. */
    private val STREAM_MARKERS = listOf("поток", "все подгруппы", "все группы", "весь курс")

    private val DIGITS = Regex("""\d{1,2}""")

    fun scopeOf(subgroup: String): LessonScope {
        val clean = subgroup.trim().lowercase(Locale.ROOT)
        return when {
            clean.isEmpty() -> LessonScope.STREAM
            STREAM_MARKERS.any { clean.contains(it) } -> LessonScope.STREAM
            else -> LessonScope.SUBGROUP
        }
    }

    /**
     * Номер подгруппы из текста портала: «Подгруппа 1», «1 подгруппа», «1 п/г».
     * Возвращает null, если номера нет или он не похож на номер подгруппы (1..9).
     */
    fun subgroupNumber(subgroup: String): Int? =
        DIGITS.find(subgroup)?.value?.toIntOrNull()?.takeIf { it in 1..9 }
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
