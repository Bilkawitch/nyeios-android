package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.nya.nyeios.data.model.GroupShortName
import ru.nya.nyeios.data.model.LessonScope
import ru.nya.nyeios.data.model.LessonScopeParser

/**
 * Разбор принадлежности пары: подгруппа (одна группа/подгруппа) или поток (все группы
 * и подгруппы). Портал помечает подгрупповые пары строкой «Подгруппа N», у потоковых
 * лекций эта строка пустая; подпись подгруппы дополняется кодом группы: «АФ подгруппа 1».
 */
class LessonScopeTest {

    @Test
    fun emptySubgroupMarkMeansStream() {
        assertEquals(LessonScope.STREAM, LessonScopeParser.scopeOf(""))
        assertEquals(LessonScope.STREAM, LessonScopeParser.scopeOf("   "))
        // Обе лекции 10.11 в расписании группы 23АН-о-41 — потоковые.
        assertEquals(LessonScope.STREAM, LessonScopeParser.scopeOf("Поток"))
        assertEquals(LessonScope.STREAM, LessonScopeParser.scopeOf("все подгруппы"))
    }

    @Test
    fun subgroupMarkMeansSubgroup() {
        assertEquals(LessonScope.SUBGROUP, LessonScopeParser.scopeOf("Подгруппа 1"))
        assertEquals(LessonScope.SUBGROUP, LessonScopeParser.scopeOf("1 п/г"))
        assertEquals(LessonScope.SUBGROUP, LessonScopeParser.scopeOf("2 подгруппа"))
    }

    @Test
    fun subgroupNumberParsesPortalVariants() {
        assertEquals(1, LessonScopeParser.subgroupNumber("Подгруппа 1"))
        assertEquals(2, LessonScopeParser.subgroupNumber("Подгруппа 2"))
        assertEquals(1, LessonScopeParser.subgroupNumber("1 подгруппа"))
        assertEquals(3, LessonScopeParser.subgroupNumber("3 п/г"))
        assertNull("Нет номера — нет подписи номера", LessonScopeParser.subgroupNumber("Подгруппа"))
        assertNull("Номер группы не должен становиться номером подгруппы", LessonScopeParser.subgroupNumber("23АН-о-41"))
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
}
