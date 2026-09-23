package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.model.CurriculumControlType
import ru.nya.nyeios.data.parser.CurriculumParser

class CurriculumParserTest {

    @Test
    fun testParseCurriculum() {
        val sampleHtml = """
            <div id="workarea">
                <div class="curriculum-terms-list">
                    <div>
                        <span class="curriculum-term menu-item-link-text">7 семестр</span>
                    </div>
                    <div class="curriculum-term-content" id="curriculum-term-7">
                        <table>
                            <thead>
                                <tr>
                                    <th>Дисциплина</th>
                                    <th>Форма контроля</th>
                                    <th>Всего часов</th>
                                    <th>ЗЕТ</th>
                                    <th>Текущий контроль</th>
                                    <th>Рейтинг по текущей</th>
                                    <th>Семестровый рейтинг</th>
                                    <th>Баллы на экзамене</th>
                                    <th>Итоговый рейтинг</th>
                                    <th>Оценка</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td>Архитектура информационных систем</td>
                                    <td>Экзамен</td>
                                    <td>144</td>
                                    <td>4</td>
                                    <td>42</td>
                                    <td>84%</td>
                                    <td>84%</td>
                                    <td>45</td>
                                    <td>87</td>
                                    <td>Отлично</td>
                                </tr>
                                <tr>
                                    <td>Тестирование ПО</td>
                                    <td>Зачет с оценкой</td>
                                    <td>108</td>
                                    <td>3</td>
                                    <td>38</td>
                                    <td>76%</td>
                                    <td>76%</td>
                                    <td>40</td>
                                    <td>78</td>
                                    <td>Хорошо</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>

                    <div>
                        <span class="curriculum-term menu-item-link-text opened">8 семестр</span>
                    </div>
                    <div class="curriculum-term-content" id="curriculum-term-8">
                        <table>
                            <thead>
                                <tr>
                                    <th>Дисциплина</th>
                                    <th>Форма контроля</th>
                                    <th>Всего часов</th>
                                    <th>ЗЕТ</th>
                                    <th>Текущий контроль</th>
                                    <th>Рейтинг по текущей</th>
                                    <th>Семестровый рейтинг</th>
                                    <th>Баллы на экзамене</th>
                                    <th>Итоговый рейтинг</th>
                                    <th>Оценка</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td>Преддипломная практика</td>
                                    <td>Диф. зачет</td>
                                    <td>216</td>
                                    <td>6</td>
                                    <td>45</td>
                                    <td>90%</td>
                                    <td>90%</td>
                                    <td>0</td>
                                    <td>45</td>
                                    <td></td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val terms = CurriculumParser.parse(sampleHtml)
        assertEquals(2, terms.size)

        // Terms are sorted descending (8 then 7)
        val term8 = terms[0]
        assertEquals(8, term8.termNum)
        assertEquals("8 семестр", term8.termTitle)
        assertTrue("8 semester has active non-empty score and no grade, so it should be current", term8.isCurrent)
        assertEquals(1, term8.subjects.size)

        val subject8 = term8.subjects[0]
        assertEquals("Преддипломная практика", subject8.subject)
        assertEquals("Диф. зачет", subject8.controlType)
        assertEquals(CurriculumControlType.GRADED_TEST, subject8.controlCategory)
        assertEquals("216", subject8.hours)
        assertEquals("6", subject8.zet)
        assertEquals("45", subject8.currentScore)
        assertEquals("", subject8.grade)

        val term7 = terms[1]
        assertEquals(7, term7.termNum)
        assertEquals("7 семестр", term7.termTitle)
        assertEquals(2, term7.subjects.size)

        val subject7 = term7.subjects[0]
        assertEquals("Архитектура информационных систем", subject7.subject)
        assertEquals(CurriculumControlType.EXAM, subject7.controlCategory)
        assertEquals("Отлично", subject7.grade)
    }
}
