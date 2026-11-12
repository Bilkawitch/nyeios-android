package ru.nya.nyeios.data.parser

import org.jsoup.Jsoup
import ru.nya.nyeios.data.model.PublicGroupInfo
import ru.nya.nyeios.data.model.PublicLesson
import java.util.Locale

/**
 * Парсер публичного расписания вуза `gukolomna.ru/studentu/schedule`.
 *
 * Из HTML вытаскиваются только данные, нужные для тихой сверки потока:
 * список групп (id + факультет/форма обучения) и пары выбранной группы.
 */
object PublicScheduleParser {

    private val AR_ELEMENTS = Regex(
        """arElements\(\s*(\{.*?\})\s*\);""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    private val GROUP_ENTRY = Regex(
        """'AcademicGroupCompoundKey':'(?<compound>[^']*)','AcademicGroupName':'(?<name>[^']*)'.*?""" +
            """'Faculty':\{'ReferenceName':'(?<faculty>[^']*)'.*?'ReferenceUID':'(?<facultyUid>[^']*)'.*?""" +
            """'EduForm':\{'ReferenceName':'(?<eduForm>[^']*)'.*?'ReferenceUID':'(?<eduFormUid>[^']*)'""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    private val DAY_INDEX = mapOf(
        "ПН" to 0, "ВТ" to 1, "СР" to 2, "ЧТ" to 3, "ПТ" to 4, "СБ" to 5, "ВС" to 6
    )

    fun parseGroups(html: String): List<PublicGroupInfo> {
        val payload = AR_ELEMENTS.find(html)?.groupValues?.get(1) ?: return emptyList()
        return GROUP_ENTRY.findAll(payload).map { match ->
            PublicGroupInfo(
                name = match.groups["name"]?.value?.trim().orEmpty(),
                compoundKey = match.groups["compound"]?.value?.trim().orEmpty(),
                facultyName = match.groups["faculty"]?.value?.trim().orEmpty(),
                facultyUid = match.groups["facultyUid"]?.value?.trim().orEmpty(),
                eduFormName = match.groups["eduForm"]?.value?.trim().orEmpty(),
                eduFormUid = match.groups["eduFormUid"]?.value?.trim().orEmpty()
            )
        }.filter { it.name.isNotEmpty() && it.compoundKey.isNotEmpty() }.toList()
    }

    /**
     * @return список пар или null, если на странице нет таблицы расписания
     *   (например, период не входит в опубликованный список недель).
     */
    fun parseWeek(html: String): List<PublicLesson>? {
        val doc = Jsoup.parse(html)
        val table = doc.selectFirst("table.schedule-component") ?: return null
        val lessons = mutableListOf<PublicLesson>()
        var currentDay = -1

        for (row in table.select("tr")) {
            val cells = row.children().filter { it.tagName() == "td" }
            if (cells.size < 3) continue

            val dayText = cells[0].text().trim().uppercase(Locale.ROOT)
            if (dayText.isNotEmpty()) {
                currentDay = DAY_INDEX[dayText.take(2)] ?: -1
            }
            if (currentDay < 0) continue

            val time = cells[1].text().trim()
            if (time.isEmpty()) continue
            val cell = cells[2]
            val subject = cell.select("span.schedule-component-accent")
                .asSequence()
                .map { it.text().trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("(") }
                .orEmpty()
            val room = cell.selectFirst("b")?.text()?.trim().orEmpty()

            if (subject.isEmpty() && room.isEmpty()) continue
            lessons += PublicLesson(
                dayIndex = currentDay,
                time = time,
                subject = subject,
                room = room
            )
        }
        return lessons
    }
}
