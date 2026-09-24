package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.model.LessonType
import ru.nya.nyeios.data.parser.ScheduleParser

class ScheduleParserTest {

    @Test
    fun testScheduleParser() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Расписание</title></head>
            <body>
                <h2>Расписание занятий с 15.09.2026 по 21.09.2026</h2>
                <select id="group-select">
                    <option selected>23ан-о-41</option>
                </select>
                <table class="schedule-table">
                    <thead>
                        <tr>
                            <th>Время</th>
                            <th>Понедельник 15.09</th>
                            <th>Вторник 16.09</th>
                            <th>Среда 17.09</th>
                            <th>Четверг 18.09</th>
                            <th>Пятница 19.09</th>
                            <th>Суббота 20.09</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>1 пара 08:30 - 10:00</td>
                            <td>
                                <table class="schedule-cell">
                                    <tr><td>Иностранный язык</td><td>204</td></tr>
                                    <tr><td>Иванова И.И.</td><td>Практ.</td></tr>
                                </table>
                            </td>
                            <td>
                                <table class="schedule-cell">
                                    <tr class="schedule-cell-subgroup"><td>1 п/г</td></tr>
                                    <tr><td>Информатика</td><td>ДО</td></tr>
                                    <tr><td>Петров П.П.</td><td>Лаб.</td></tr>
                                </table>
                            </td>
                            <td></td>
                            <td></td>
                            <td></td>
                            <td></td>
                        </tr>
                        <tr>
                            <td>2 пара 10:10 - 11:40</td>
                            <td>
                                <table class="schedule-cell">
                                    <tr><td>История России</td><td>101</td></tr>
                                    <tr><td>Сидоров С.С.</td><td>Лекция</td></tr>
                                </table>
                            </td>
                            <td></td>
                            <td></td>
                            <td></td>
                            <td></td>
                            <td></td>
                        </tr>
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()

        val schedule = ScheduleParser.parse(sampleHtml, offsetWeeks = 0, "15.09.2026", "21.09.2026")
        assertNotNull(schedule)
        assertEquals("23ан-о-41", schedule!!.group)
        assertEquals(6, schedule.days.size)

        // Monday
        val monday = schedule.days[0]
        assertEquals("Пн", monday.dayName)
        assertEquals("15.09", monday.dateString)
        assertEquals(2, monday.lessons.size)

        val lesson1 = monday.lessons[0]
        assertEquals("Иностранный язык", lesson1.subject)
        assertEquals("204", lesson1.room)
        assertEquals("Иванова И.И.", lesson1.teacher)
        assertEquals(LessonType.PRACTICE, lesson1.type)

        val lesson2 = monday.lessons[1]
        assertEquals("История России", lesson2.subject)
        assertEquals("101", lesson2.room)
        assertEquals("Сидоров С.С.", lesson2.teacher)
        assertEquals(LessonType.LECTURE, lesson2.type)

        // Tuesday
        val tuesday = schedule.days[1]
        assertEquals("Вт", tuesday.dayName)
        assertEquals(1, tuesday.lessons.size)
        val tuesdayLesson = tuesday.lessons[0]
        assertEquals("1 п/г", tuesdayLesson.subgroup)
        assertEquals("Информатика", tuesdayLesson.subject)
        assertEquals("ДО", tuesdayLesson.room)
        assertEquals(LessonType.LAB, tuesdayLesson.type)
    }

    @Test
    fun testEmptyWeekScheduleParser() {
        val emptyWeekHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Расписание</title></head>
            <body>
                <div class="schedule-body">
                    <div class="error-messages">
                        <div class="content-edit-form-notice-error">
                            <span class="content-edit-form-notice-text">
                                Расписание работает в режиме отладки, просим уточнять расписание в деканате или на стенде университета
                            </span>
                        </div>
                    </div>
                    <h2>28.09.2026 &mdash; 04.10.2026</h2>
                    <select id="group-select">
                        <option selected>23АН-о-41</option>
                    </select>
                    <p><strong>Бакалавриат, 44.03.05, 23АН-о-41</strong></p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val schedule = ScheduleParser.parse(emptyWeekHtml, offsetWeeks = 1, "28.09.2026", "04.10.2026")
        assertNotNull(schedule)
        assertEquals("23АН-о-41", schedule!!.group)
        assertEquals("28.09.2026 — 04.10.2026", schedule.weekTitle)
        assertEquals(7, schedule.days.size)
        assertTrue(schedule.days.all { it.lessons.isEmpty() })

        assertEquals("Пн", schedule.days[0].dayName)
        assertEquals("28.09", schedule.days[0].dateString)
        assertEquals("Вт", schedule.days[1].dayName)
        assertEquals("29.09", schedule.days[1].dateString)
        assertEquals("Вс", schedule.days[6].dayName)
        assertEquals("04.10", schedule.days[6].dateString)
    }

    @Test
    fun testAuthRequiredCheck() {
        val loginHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Авторизация</title></head>
            <body>
                <form name="form_auth" method="post">
                    <input type="hidden" name="AUTH_FORM" value="Y" />
                    <input type="text" name="USER_LOGIN" />
                    <input type="password" name="USER_PASSWORD" />
                </form>
            </body>
            </html>
        """.trimIndent()

        assertTrue(ScheduleParser.isAuthRequired(loginHtml))
        val schedule = ScheduleParser.parse(loginHtml, offsetWeeks = 0, "21.09.2026", "27.09.2026")
        org.junit.Assert.assertNull(schedule)
    }
}
