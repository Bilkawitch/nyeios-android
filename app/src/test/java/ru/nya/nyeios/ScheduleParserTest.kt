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

    @Test
    fun testServerErrorDoesNotReturnEmptySchedule() {
        val serverErrorHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Расписание</title></head>
            <body>
                <div id="workarea">
                    <div id="workarea-content">
                        <div class="workarea-content-paddings">
                            <p><font class="errortext">Отсутствует соединение с сервером. Повторите попытку позднее</font></p>
                        </div>
                    </div>
                </div>
                <div class="menu">
                    <a href="/eios/contacts/timetable/">Расписание</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        val errMsg = ScheduleParser.extractErrorMessage(serverErrorHtml)
        assertEquals("Отсутствует соединение с сервером. Повторите попытку позднее", errMsg)

        // Must NOT return empty week schedule! Must return null on server error.
        val schedule = ScheduleParser.parse(serverErrorHtml, offsetWeeks = 0, "21.09.2026", "27.09.2026")
        org.junit.Assert.assertNull(schedule)
    }

    @Test
    fun testGenericBitrixPageReturnsNull() {
        val genericBitrixHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Страница</title></head>
            <body>
                <div class="workarea-content">
                    <h2>Информация</h2>
                    <p>Какой-то контент</p>
                </div>
                <a href="/eios/contacts/timetable/">Ссылка на расписание</a>
            </body>
            </html>
        """.trimIndent()

        val schedule = ScheduleParser.parse(genericBitrixHtml, offsetWeeks = 0, "21.09.2026", "27.09.2026")
        org.junit.Assert.assertNull(schedule)
    }

    @Test
    fun testTableWithoutTheadParsesCorrectly() {
        val tableHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <table class="schedule-table">
                    <tr>
                        <th>Время</th>
                        <th>Понедельник 21.09</th>
                    </tr>
                    <tr>
                        <td>1 пара 08:30 - 10:00</td>
                        <td>
                            <table class="schedule-cell">
                                <tr><td>Философия</td><td>301</td></tr>
                                <tr><td>Кузнецов К.К.</td><td>Лекция</td></tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val schedule = ScheduleParser.parse(tableHtml, offsetWeeks = 0, "21.09.2026", "27.09.2026")
        assertNotNull(schedule)
        assertEquals(1, schedule!!.days.size)
        assertEquals(1, schedule.days[0].lessons.size)
        assertEquals("Философия", schedule.days[0].lessons[0].subject)
        assertEquals("301", schedule.days[0].lessons[0].room)
    }

    @Test
    fun testBitrixScriptOfflineMessageDoesNotBlockParsing() {
        val htmlWithBitrixScripts = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Расписание занятий</title>
                <script>
                    BX.message({
                        "BITRIX24_CS_OFFLINE" : "Отсутствует соединение с сервером",
                        "IM_CS_OFFLINE" : "Отсутствует соединение с сервером"
                    });
                </script>
            </head>
            <body>
                <div id="workarea">
                    <div class="schedule-body">
                        <h2>14.09.2026 — 20.09.2026</h2>
                        <select id="group-select"><option selected>23АН-о-41</option></select>
                        <table class="schedule-table">
                            <thead>
                                <tr><th>Время</th><th>Понедельник 14.09</th></tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td>1 пара 08:30 - 10:00</td>
                                    <td>
                                        <table class="schedule-cell">
                                            <tr><td>Информатика</td><td>417</td></tr>
                                            <tr><td>Иванов И.И.</td><td>Практика</td></tr>
                                        </table>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val errMsg = ScheduleParser.extractErrorMessage(htmlWithBitrixScripts)
        org.junit.Assert.assertNull(errMsg)

        val schedule = ScheduleParser.parse(htmlWithBitrixScripts, offsetWeeks = 0, "14.09.2026", "20.09.2026")
        assertNotNull(schedule)
        assertEquals("23АН-о-41", schedule!!.group)
        assertEquals(1, schedule.days[0].lessons.size)
        assertEquals("Информатика", schedule.days[0].lessons[0].subject)
        assertEquals("417", schedule.days[0].lessons[0].room)
    }

    @Test
    fun testEmptyWeekScheduleWithNoticeErrorReturnsEmptyWeek() {
        val emptyWeekHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Расписание занятий</title>
                <script>
                    BX.message({ "BITRIX24_CS_OFFLINE" : "Отсутствует соединение с сервером" });
                </script>
            </head>
            <body>
                <div id="workarea">
                    <div class="schedule-body">
                        <div class="error-messages">
                            <div class="content-edit-form-notice-error">Расписание работает в режиме отладки.</div>
                        </div>
                        Нет данных
                        <h2>28.09.2026 — 04.10.2026</h2>
                        <select id="group-select"><option selected>23АН-о-41</option></select>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val errMsg = ScheduleParser.extractErrorMessage(emptyWeekHtml)
        org.junit.Assert.assertNull(errMsg)

        val schedule = ScheduleParser.parse(emptyWeekHtml, offsetWeeks = 0, "28.09.2026", "04.10.2026")
        assertNotNull(schedule)
        assertEquals("23АН-о-41", schedule!!.group)
        assertTrue(schedule.days.all { it.lessons.isEmpty() })
    }
}

