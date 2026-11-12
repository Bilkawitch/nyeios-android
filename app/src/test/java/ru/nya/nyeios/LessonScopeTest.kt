package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.model.FlowGroupMatcher
import ru.nya.nyeios.data.model.FlowWeekState
import ru.nya.nyeios.data.model.GroupShortName
import ru.nya.nyeios.data.model.LessonItem
import ru.nya.nyeios.data.model.LessonScope
import ru.nya.nyeios.data.model.LessonScopeParser
import ru.nya.nyeios.data.model.LessonScopeResolver
import ru.nya.nyeios.data.model.LessonType
import ru.nya.nyeios.data.model.PublicGroupInfo
import ru.nya.nyeios.data.model.PublicLessonKey

/**
 * Определение уровня пары: подгруппа (по отметке портала), поток и группа (по тихой сверке
 * с расписанием соседних групп). В разметке ЭИОС поток и группа не различаются.
 */
class LessonScopeTest {

    private fun lesson(
        subgroup: String = "",
        time: String = "08:30 - 10:00",
        subject: String = "Теория и методика обучения немецкому языку",
        room: String = "433"
    ) = LessonItem(
        time = time,
        lessonNumber = "1",
        subgroup = subgroup,
        subject = subject,
        room = room,
        teacher = "Серебрякова Екатерина Владимировна",
        type = LessonType.LECTURE
    )

    private fun flowWith(vararg lessons: Pair<Int, LessonItem>, complete: Boolean = true): FlowWeekState {
        val keys = lessons.map { (day, l) ->
            PublicLessonKey.of(day, l.time, l.subject, l.room)
        }.toSet()
        return FlowWeekState(weekKey = "09.11.2026_15.11.2026", streamLessonKeys = keys, isComplete = complete)
    }

    @Test
    fun subgroupMarkMeansSubgroup() {
        assertEquals(
            LessonScope.SUBGROUP,
            LessonScopeResolver.scopeOf(lesson(subgroup = "Подгруппа 1"), dayIndex = 2, flow = null)
        )
        assertEquals(
            LessonScope.SUBGROUP,
            LessonScopeResolver.scopeOf(lesson(subgroup = "1 п/г"), dayIndex = 2, flow = null)
        )
    }

    @Test
    fun streamMarkerIsStreamEvenWithoutSiblingData() {
        assertEquals(
            LessonScope.STREAM,
            LessonScopeResolver.scopeOf(lesson(subgroup = "Поток"), dayIndex = 2, flow = null)
        )
    }

    @Test
    fun emptySubgroupWithoutSiblingDataIsUnknown() {
        // Пока тихая сверка не дала данных, уровень не выдумываем — метки нет.
        assertNull(LessonScopeResolver.scopeOf(lesson(), dayIndex = 3, flow = null))
    }

    @Test
    fun lessonFoundInSiblingScheduleIsStream() {
        val lecture = lesson()
        val flow = flowWith(3 to lecture)
        assertEquals(
            LessonScope.STREAM,
            LessonScopeResolver.scopeOf(lecture, dayIndex = 3, flow = flow)
        )
    }

    @Test
    fun unsharedLessonAfterCompleteCheckIsGroup() {
        // ЧТ 08:30 «Теория и методика обучения немецкому языку» есть только у 23АН.
        val lecture = lesson()
        val flow = FlowWeekState(weekKey = "week", streamLessonKeys = emptySet(), isComplete = true)
        assertEquals(
            LessonScope.GROUP,
            LessonScopeResolver.scopeOf(lecture, dayIndex = 3, flow = flow)
        )
    }

    @Test
    fun unsharedLessonWhileCheckIsIncompleteStaysUnknown() {
        val lecture = lesson()
        val partial = FlowWeekState(weekKey = "week", isComplete = false, fetchedGroups = 1, expectedGroups = 3)
        assertNull(LessonScopeResolver.scopeOf(lecture, dayIndex = 3, flow = partial))
    }

    @Test
    fun matchingIgnoresBracketedSubjectFormatting() {
        // Сверка идёт по дню, времени и аудитории, поэтому различия в названии дисциплины не мешают.
        val own = lesson(time = "08:30 - 10:00", room = "432")
        val siblingKey = PublicLessonKey.of(3, "08:30 - 10:00", "другой вариант названия", "432")
        val flow = FlowWeekState(streamLessonKeys = setOf(siblingKey), isComplete = true)
        assertEquals(LessonScope.STREAM, LessonScopeResolver.scopeOf(own, 3, flow))
    }

    @Test
    fun subgroupNumberParsesPortalVariants() {
        assertEquals(1, LessonScopeParser.subgroupNumber("Подгруппа 1"))
        assertEquals(2, LessonScopeParser.subgroupNumber("Подгруппа 2"))
        assertEquals(1, LessonScopeParser.subgroupNumber("1 подгруппа"))
        assertEquals(3, LessonScopeParser.subgroupNumber("3 п/г"))
        assertNull(LessonScopeParser.subgroupNumber("Подгруппа"))
        assertNull(LessonScopeParser.subgroupNumber("23АН-о-41"))
    }

    @Test
    fun groupShortNameTakesFirstTwoLetters() {
        assertEquals("АН", GroupShortName.of("23АН-о-41"))
        assertEquals("АФ", GroupShortName.of("23АФ-о-41-1"))
        assertEquals("ФК", GroupShortName.of("ФК-21"))
        assertEquals("АН", GroupShortName.of("23ан-о-41"))
        assertEquals("", GroupShortName.of(null))
        assertEquals("", GroupShortName.of(""))
    }

    @Test
    fun flowSiblingsShareYearFacultyAndEduForm() {
        val own = PublicGroupInfo(
            name = "23АН-о-41", compoundKey = "k0",
            facultyName = "Факультет иностранных языков", facultyUid = "f1",
            eduFormName = "Очная", eduFormUid = "e1"
        )
        val all = listOf(
            own,
            own.copy(name = "23АК-о-41", compoundKey = "k1"),
            own.copy(name = "23АФ-о-41", compoundKey = "k2"),
            own.copy(name = "23ПП-о-41", compoundKey = "k3"),
            own.copy(name = "24АН-о-31", compoundKey = "k4"),
            own.copy(name = "23ИНФ-о-41", compoundKey = "k5", facultyUid = "f2"),
            own.copy(name = "23ЗПК-ДО-о-41", compoundKey = "k6", eduFormUid = "e2")
        )

        val siblings = FlowGroupMatcher.siblings(own, all).map { it.name }
        assertEquals(listOf("23АК-о-41", "23АФ-о-41", "23ПП-о-41"), siblings)
        assertTrue(siblings.none { it == "23АН-о-41" })
    }

    @Test
    fun flowSiblingsRequireNumericYear() {
        val own = PublicGroupInfo(name = "АН-41", compoundKey = "k0", facultyUid = "f1", eduFormUid = "e1")
        assertEquals(emptyList<String>(), FlowGroupMatcher.siblings(own, listOf(own)).map { it.name })
    }
}
