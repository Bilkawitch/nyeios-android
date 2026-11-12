package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.parser.PublicScheduleParser

/**
 * Парсер публичного расписания вуза: список групп и пары выбранной группы нужны для тихой
 * сверки потока (сайт отдаёт HTML без JSON-API).
 */
class PublicScheduleParserTest {

    private val groupsHtml = """
        <html><body>
        <script>
        arElements({'data':[
        {'AcademicGroupCompoundKey':'c2cdb96b-6972-11ed-8115-00155d040503:8ea6cb63','AcademicGroupName':'23АН-о-41','Faculty':{'ReferenceName':'Факультет иностранных языков','ReferenceId':'000000007','ReferenceUID':'88d34ec5-8fec-11e8-80e6-00155d3a0358','ReferenceClassName':'Справочник.СтруктураУниверситета'},'EduForm':{'ReferenceName':'Очная','ReferenceId':'000000001','ReferenceUID':'ed52fe1e-8383-11e8-80e5-00155d3a0358','ReferenceClassName':'Справочник.ФормаОбучения'}},
        {'AcademicGroupCompoundKey':'f47ddd47-6974-11ed-8115-00155d040503:8ea6cb65','AcademicGroupName':'23АК-о-41','Faculty':{'ReferenceName':'Факультет иностранных языков','ReferenceId':'000000007','ReferenceUID':'88d34ec5-8fec-11e8-80e6-00155d3a0358','ReferenceClassName':'Справочник.СтруктураУниверситета'},'EduForm':{'ReferenceName':'Очная','ReferenceId':'000000001','ReferenceUID':'ed52fe1e-8383-11e8-80e5-00155d3a0358','ReferenceClassName':'Справочник.ФормаОбучения'}},
        {'AcademicGroupCompoundKey':'6ce0db4f-39e2-11ef-8127-00155d040503:ad3ebdc3','AcademicGroupName':'23ИНФ-о-41','Faculty':{'ReferenceName':'Факультет математики, физики, химии, информатики','ReferenceId':'000000009','ReferenceUID':'88d34ec7-8fec-11e8-80e6-00155d3a0358','ReferenceClassName':'Справочник.СтруктураУниверситета'},'EduForm':{'ReferenceName':'Очная','ReferenceId':'000000001','ReferenceUID':'ed52fe1e-8383-11e8-80e5-00155d3a0358','ReferenceClassName':'Справочник.ФормаОбучения'}}
        ]});
        </script>
        </body></html>
    """.trimIndent()

    private val weekHtml = """
        <html><body>
        <table class="table schedule-component">
          <tbody>
            <tr class="schedule-component-row-first">
              <td><span class="schedule-component-accent">ПН</span></td>
              <td class="schedule-component-time">08:30 - 10:00</td>
              <td><span class="schedule-component-accent">Теория и методика обучения английскому языку</span>, Лекц<br>Онищенко Юлия Юрьевна<br><span class="schedule-component-accent"></span> ауд. <b>432</b></td>
            </tr>
            <tr>
              <td></td>
              <td class="schedule-component-time">10:10 - 11:40</td>
              <td><div class="schedule-component-accent">(подгруппа 1)</div><span class="schedule-component-accent">Практика устной и письменной речи английского языка</span>, Прак<br>Чернякова Юлия Сергеевна<br><span class="schedule-component-accent"></span> ауд. <b>431</b></td>
            </tr>
            <tr>
              <td></td>
              <td class="schedule-component-time">12:10 - 13:40</td>
              <td></td>
            </tr>
            <tr class="schedule-component-row-first">
              <td><span class="schedule-component-accent">ВТ</span></td>
              <td class="schedule-component-time">08:30 - 10:00</td>
              <td><span class="schedule-component-accent">Стилистика английского языка</span>, Лекц<br>Чернякова Юлия Сергеевна<br><span class="schedule-component-accent"></span> ауд. <b>432</b></td>
            </tr>
          </tbody>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun parsesGroupListWithFacultyAndEduForm() {
        val groups = PublicScheduleParser.parseGroups(groupsHtml)
        assertEquals(3, groups.size)

        val an = groups.first { it.name == "23АН-о-41" }
        assertEquals("c2cdb96b-6972-11ed-8115-00155d040503:8ea6cb63", an.compoundKey)
        assertEquals("Факультет иностранных языков", an.facultyName)
        assertEquals("88d34ec5-8fec-11e8-80e6-00155d3a0358", an.facultyUid)
        assertEquals("Очная", an.eduFormName)
        assertEquals("ed52fe1e-8383-11e8-80e5-00155d3a0358", an.eduFormUid)
    }

    @Test
    fun parsesWeekLessonsWithDayIndexTimeSubjectAndRoom() {
        val lessons = PublicScheduleParser.parseWeek(weekHtml)
        assertNotNull(lessons)
        assertEquals(3, lessons!!.size)

        val first = lessons[0]
        assertEquals(0, first.dayIndex)
        assertEquals("08:30 - 10:00", first.time)
        assertEquals("Теория и методика обучения английскому языку", first.subject)
        assertEquals("432", first.room)

        val second = lessons[1]
        assertEquals(0, second.dayIndex)
        assertEquals("10:10 - 11:40", second.time)
        assertEquals("Практика устной и письменной речи английского языка", second.subject)
        assertEquals("431", second.room)

        val third = lessons[2]
        assertEquals(1, third.dayIndex)
        assertEquals("Стилистика английского языка", third.subject)
        assertTrue(lessons.none { it.subject.isEmpty() && it.room.isEmpty() })
    }

    @Test
    fun returnsNullWhenScheduleTableIsMissing() {
        // Период не входит в опубликованный список недель — таблицы нет, это не пустая неделя.
        assertNull(PublicScheduleParser.parseWeek("<html><body>Форма без расписания</body></html>"))
        assertEquals(emptyList<Any>(), PublicScheduleParser.parseGroups("<html><body>нет данных</body></html>"))
    }
}
