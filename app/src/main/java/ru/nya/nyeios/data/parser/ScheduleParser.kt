package ru.nya.nyeios.data.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ru.nya.nyeios.data.model.DaySchedule
import ru.nya.nyeios.data.model.LessonItem
import ru.nya.nyeios.data.model.LessonType
import ru.nya.nyeios.data.model.WeekSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object ScheduleParser {

    fun isAuthRequired(html: String): Boolean {
        return html.contains("name=\"AUTH_FORM\"") ||
                html.contains("form_auth") ||
                html.contains("<title>Авторизация</title>") ||
                (html.contains("USER_LOGIN") && html.contains("USER_PASSWORD"))
    }

    fun extractErrorMessage(doc: Document): String? {
        val errorEl = doc.selectFirst(".errortext")
            ?: doc.selectFirst("font.errortext")
            ?: doc.selectFirst(".error")
        val text = errorEl?.text()?.trim()
        if (!text.isNullOrEmpty()) return text

        val workareaText = doc.selectFirst("#workarea")?.text() ?: doc.body()?.text() ?: ""
        if (workareaText.contains("Отсутствует соединение с сервером", ignoreCase = true)) {
            return "Отсутствует соединение с сервером. Повторите попытку позднее"
        }
        return null
    }

    fun extractErrorMessage(html: String): String? {
        return extractErrorMessage(Jsoup.parse(html))
    }

    fun parse(html: String, offsetWeeks: Int = 0, startDateStr: String = "", endDateStr: String = ""): WeekSchedule? {
        if (isAuthRequired(html)) return null

        val doc = Jsoup.parse(html)

        // If the page contains an explicit server error, it is not a valid empty schedule
        if (extractErrorMessage(doc) != null) {
            return null
        }

        val table = doc.selectFirst("table.schedule-table")

        if (table == null) {
            val hasTimetableContext = doc.selectFirst("div.schedule-body") != null ||
                    doc.selectFirst("select#group-select") != null

            if (hasTimetableContext) {
                return buildEmptyWeekSchedule(doc, offsetWeeks, startDateStr, endDateStr)
            }
            return null
        }

        val thElements = table.select("thead th").let { theadThs ->
            if (theadThs.isNotEmpty()) theadThs
            else table.select("tr").firstOrNull()?.select("th")?.let { trThs ->
                if (trThs.isNotEmpty()) trThs
                else table.select("tr").firstOrNull()?.select("td") ?: org.jsoup.select.Elements()
            } ?: org.jsoup.select.Elements()
        }
        if (thElements.size < 2) {
            return buildEmptyWeekSchedule(doc, offsetWeeks, startDateStr, endDateStr)
        }

        // thElements[0] is header for time column, rest are days
        val rawDays = thElements.drop(1).map { it.text().trim() }
        val numDays = rawDays.size

        val h2Raw = doc.selectFirst("h2")?.text()?.trim() ?: "Расписание занятий"
        val h2Text = h2Raw.replace("&mdash;", "—")

        val groupSelect = doc.selectFirst("select#group-select")
        val groupName = groupSelect?.selectFirst("option[selected]")?.text()?.trim()
            ?: groupSelect?.selectFirst("option")?.text()?.trim()

        val today = LocalDate.now()
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault())
        val todayStr = today.format(dateFormatter)

        val dayLessonsMap = rawDays.associateWith { mutableListOf<LessonItem>() }

        val tbody = table.selectFirst("tbody") ?: table
        val rows = tbody.select("tr")

        var currentTime = ""
        var currentLessonNumber = ""

        for (tr in rows) {
            val tds = tr.children().filter { it.tagName() == "td" }
            val dayTds: List<org.jsoup.nodes.Element>

            if (tds.size >= numDays + 1) {
                val timeRaw = tds[0].text().trim()
                if (timeRaw.any { it.isDigit() }) {
                    currentTime = timeRaw.replace(Regex("""\s*[-—–]\s*"""), " - ")
                    currentLessonNumber = inferLessonNumber(timeRaw)
                }
                dayTds = tds.drop(1)
            } else if (tds.size == numDays) {
                dayTds = tds
            } else {
                continue
            }

            for ((idx, td) in dayTds.withIndex()) {
                if (idx >= rawDays.size) break
                val dayKey = rawDays[idx]

                val cells = td.select("table.schedule-cell")
                for (cell in cells) {
                    val subgroupEl = cell.selectFirst("tr.schedule-cell-subgroup")
                    val subgroup = subgroupEl?.text()?.trim().orEmpty()

                    var subject = ""
                    var room = ""
                    var teacher = ""
                    var typeRaw = ""

                    val cellRows = cell.select("tr")
                    for (cRow in cellRows) {
                        if (cRow == subgroupEl) continue
                        val tdList = cRow.select("td")

                        if (tdList.size == 2) {
                            val left = tdList[0].text().trim()
                            val right = tdList[1].text().trim()

                            val hasStrong = tdList[1].selectFirst("strong") != null
                            val hasI = tdList[1].selectFirst("i") != null

                            val rightUpper = right.uppercase(Locale.ROOT)
                            val isKnownType = isLessonType(right)
                            val isKnownRoom = right.any { it.isDigit() } ||
                                    rightUpper in listOf("ДО", "ДОТ", "ЭОР", "ДИСТ", "КАБ", "АУД", "СТАДИОН", "СПОРТЗАЛ", "СПОРТ", "БАССЕЙН", "МАНЕЖ", "ТИР", "ЗАЛ", "ПОЛЕ", "ПЛОЩАДКА") ||
                                    rightUpper.startsWith("СПОРТ") ||
                                    rightUpper.startsWith("СТАДИОН") ||
                                    rightUpper.startsWith("КАБ") ||
                                    rightUpper.startsWith("АУД")

                            val isRoom = when {
                                hasStrong && !hasI -> true
                                hasI && !hasStrong -> false
                                isKnownType -> false
                                isKnownRoom -> true
                                subject.isEmpty() -> true
                                else -> false
                            }

                            if (isRoom) {
                                subject = left
                                room = right
                            } else {
                                teacher = left
                                typeRaw = right
                            }
                        } else if (tdList.size == 1) {
                            if (subject.isEmpty()) {
                                subject = tdList[0].text().trim()
                            }
                        }
                    }

                    if (subject.isNotEmpty() || room.isNotEmpty()) {
                        val lessonType = when {
                            typeRaw.contains("лек", ignoreCase = true) -> LessonType.LECTURE
                            typeRaw.contains("сем", ignoreCase = true) -> LessonType.SEMINAR
                            typeRaw.contains("прак", ignoreCase = true) -> LessonType.PRACTICE
                            typeRaw.contains("лаб", ignoreCase = true) -> LessonType.LAB
                            typeRaw.contains("экз", ignoreCase = true) ||
                                    typeRaw.contains("зач", ignoreCase = true) ||
                                    typeRaw.contains("контр", ignoreCase = true) -> LessonType.EXAM
                            else -> LessonType.OTHER
                        }

                        dayLessonsMap[dayKey]?.add(
                            LessonItem(
                                time = currentTime,
                                lessonNumber = currentLessonNumber,
                                subgroup = subgroup,
                                subject = subject,
                                room = room,
                                teacher = teacher,
                                type = lessonType
                            )
                        )
                    }
                }
            }
        }

        val todayDayMonth = today.format(DateTimeFormatter.ofPattern("dd.MM", Locale.getDefault()))

        val parsedDays = rawDays.map { dayTitle ->
            val parts = dayTitle.split(Regex("""[\s\-,]+""")).filter { it.isNotBlank() }
            val dayName = when (parts.firstOrNull()?.lowercase(Locale.ROOT)) {
                "понедельник" -> "Пн"
                "вторник" -> "Вт"
                "среда" -> "Ср"
                "четверг" -> "Чт"
                "пятница" -> "Пт"
                "суббота" -> "Сб"
                "воскресенье" -> "Вс"
                else -> parts.firstOrNull()?.take(2)?.replaceFirstChar { it.uppercase() } ?: ""
            }

            val dateMatch = Regex("""(\d{1,2}\.\d{1,2})""").find(dayTitle)
            val cleanDate = dateMatch?.value.orEmpty()
            val isToday = cleanDate.isNotEmpty() && cleanDate == todayDayMonth

            DaySchedule(
                dayTitle = dayTitle,
                dayName = dayName,
                dateString = cleanDate,
                isToday = isToday,
                lessons = dayLessonsMap[dayTitle] ?: emptyList()
            )
        }

        return WeekSchedule(
            weekTitle = h2Text,
            group = groupName,
            startDate = startDateStr,
            endDate = endDateStr,
            offsetWeeks = offsetWeeks,
            days = parsedDays,
            isCached = false
        )
    }

    private fun buildEmptyWeekSchedule(
        doc: org.jsoup.nodes.Document,
        offsetWeeks: Int,
        startDateStr: String,
        endDateStr: String
    ): WeekSchedule {
        val h2Raw = doc.selectFirst("h2")?.text()?.trim().orEmpty()
        val h2Clean = h2Raw.replace("&mdash;", "—").ifEmpty {
            if (startDateStr.isNotEmpty() && endDateStr.isNotEmpty()) "$startDateStr — $endDateStr" else "Расписание занятий"
        }

        val groupSelect = doc.selectFirst("select#group-select")
        val groupName = groupSelect?.selectFirst("option[selected]")?.text()?.trim()
            ?: groupSelect?.selectFirst("option")?.text()?.trim()

        val today = LocalDate.now()
        val dFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault())
        val todayDayMonth = today.format(DateTimeFormatter.ofPattern("dd.MM", Locale.getDefault()))

        val parsedStart = try {
            if (startDateStr.isNotEmpty()) LocalDate.parse(startDateStr, dFmt) else null
        } catch (_: Exception) {
            null
        } ?: run {
            val match = Regex("""(\d{2}\.\d{2}\.\d{4})""").find(h2Clean)
            match?.value?.let {
                try { LocalDate.parse(it, dFmt) } catch (_: Exception) { null }
            }
        } ?: today.plusWeeks(offsetWeeks.toLong()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val dayNames = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
        val shortNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

        val days = (0..6).map { i ->
            val dayDate = parsedStart.plusDays(i.toLong())
            val cleanDate = dayDate.format(DateTimeFormatter.ofPattern("dd.MM", Locale.getDefault()))
            val fullDate = dayDate.format(dFmt)
            val isToday = (offsetWeeks == 0 && cleanDate == todayDayMonth)

            DaySchedule(
                dayTitle = "${dayNames[i]} - $fullDate",
                dayName = shortNames[i],
                dateString = cleanDate,
                isToday = isToday,
                lessons = emptyList()
            )
        }

        val effStartDate = if (startDateStr.isNotEmpty()) startDateStr else parsedStart.format(dFmt)
        val effEndDate = if (endDateStr.isNotEmpty()) endDateStr else parsedStart.plusDays(6).format(dFmt)

        return WeekSchedule(
            weekTitle = h2Clean,
            group = groupName,
            startDate = effStartDate,
            endDate = effEndDate,
            offsetWeeks = offsetWeeks,
            days = days,
            isCached = false
        )
    }

    private fun isLessonType(text: String): Boolean {
        val clean = text.trim().lowercase(Locale.ROOT)
        if (clean.isEmpty()) return false
        return clean.startsWith("лек") ||
                clean.startsWith("прак") ||
                clean.startsWith("сем") ||
                clean.startsWith("лаб") ||
                clean.startsWith("экз") ||
                clean.startsWith("зач") ||
                clean.startsWith("контр") ||
                clean.startsWith("конс") ||
                clean.startsWith("коллок") ||
                clean.startsWith("тест") ||
                clean == "кср" ||
                clean.startsWith("диф") ||
                clean.startsWith("олимп") ||
                clean.startsWith("аттест")
    }

    private fun inferLessonNumber(timeRaw: String): String {
        val explicitMatch = Regex("""(\d+)\s*(?:пара|п\b)""", RegexOption.IGNORE_CASE).find(timeRaw)
        if (explicitMatch != null) return explicitMatch.groupValues[1]

        // If it starts with clock time format "HH:mm", infer from time
        val timeMatch = Regex("""^(\d{1,2}):(\d{2})""").find(timeRaw)
        if (timeMatch != null) {
            val h = timeMatch.groupValues[1].toIntOrNull() ?: return ""
            val m = timeMatch.groupValues[2].toIntOrNull() ?: return ""
            val totalMinutes = h * 60 + m
            return when (totalMinutes) {
                in 500..570 -> "1"   // ~ 08:30
                in 590..660 -> "2"   // ~ 10:10 / 10:15
                in 710..780 -> "3"   // ~ 12:10 / 12:15
                in 810..890 -> "4"   // ~ 13:50 / 14:00
                in 910..980 -> "5"   // ~ 15:30 / 15:45
                in 1010..1080 -> "6"  // ~ 17:10 / 17:30
                in 1100..1170 -> "7"  // ~ 18:50
                else -> ""
            }
        }

        // If it starts with a pair number like "1 08:30" or "1. 08:30" or "1"
        val leadingDigit = Regex("""^(\d+)\b""").find(timeRaw)
        if (leadingDigit != null) return leadingDigit.groupValues[1]

        return ""
    }
}

