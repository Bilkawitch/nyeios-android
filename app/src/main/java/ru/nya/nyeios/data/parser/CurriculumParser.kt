package ru.nya.nyeios.data.parser

import org.jsoup.Jsoup
import ru.nya.nyeios.data.model.CurriculumControlType
import ru.nya.nyeios.data.model.CurriculumSubject
import ru.nya.nyeios.data.model.CurriculumTerm
import java.time.LocalDate
import java.util.Locale

object CurriculumParser {

    fun parse(html: String): List<CurriculumTerm> {
        if (html.isBlank()) return emptyList()

        val doc = Jsoup.parse(html)
        val workarea = doc.selectFirst("#workarea")
            ?: doc.selectFirst(".workarea-content-paddings")
            ?: doc.body()
            ?: doc

        val termSpans = workarea.select(".curriculum-term.menu-item-link-text, [class*='curriculum-term'][class*='menu-item-link-text']")
        val terms = mutableListOf<CurriculumTerm>()

        for (span in termSpans) {
            val spanText = span.text().trim()
            val termNumMatch = Regex("""(\d+)""").find(spanText)
            val termNum = termNumMatch?.value?.toIntOrNull() ?: 0

            // Find associated content container (usually next sibling or nearby with class curriculum-term-content)
            var contentDiv = span.parent()?.nextElementSibling()
            while (contentDiv != null && !contentDiv.hasClass("curriculum-term-content") && !contentDiv.classNames().any { it.contains("curriculum-term-content") }) {
                // If there's a nested table or content directly, break
                if (contentDiv.selectFirst("table") != null) break
                contentDiv = contentDiv.nextElementSibling()
            }

            // Fallback: search in whole workarea for term content matching termNum
            val targetContent = contentDiv
                ?: workarea.selectFirst(".curriculum-term-content[data-term='$termNum']")
                ?: workarea.selectFirst("#curriculum-term-$termNum")
                ?: span.closest("div")?.nextElementSibling()

            val table = targetContent?.selectFirst("table")
                ?: span.parents().select(".curriculum-term-content table").firstOrNull()

            val subjects = mutableListOf<CurriculumSubject>()
            if (table != null) {
                val rows = table.select("tr")
                for (tr in rows.drop(1)) {
                    val tds = tr.select("td, th").map { it.text().trim() }
                    if (tds.size >= 9) {
                        val cType = tds[1]
                        val category = when {
                            cType.contains("экзамен", ignoreCase = true) -> CurriculumControlType.EXAM
                            cType.contains("диф", ignoreCase = true) || cType.contains("зачет с оценкой", ignoreCase = true) -> CurriculumControlType.GRADED_TEST
                            cType.contains("зачет", ignoreCase = true) -> CurriculumControlType.TEST
                            cType.contains("курсов", ignoreCase = true) -> CurriculumControlType.COURSEWORK
                            else -> CurriculumControlType.OTHER
                        }

                        subjects.add(
                            CurriculumSubject(
                                subject = tds[0],
                                controlType = cType,
                                controlCategory = category,
                                hours = tds.getOrNull(2).orEmpty(),
                                zet = tds.getOrNull(3).orEmpty(),
                                currentScore = tds.getOrNull(4).orEmpty(),
                                currentRating = tds.getOrNull(5).orEmpty(),
                                termRating = tds.getOrNull(6).orEmpty(),
                                examScore = tds.getOrNull(7).orEmpty(),
                                finalRating = tds.getOrNull(8).orEmpty(),
                                grade = if (tds.size > 9) tds[9] else ""
                            )
                        )
                    }
                }
            }

            val isOpened = span.classNames().any { it.contains("opened", ignoreCase = true) } ||
                    span.parent()?.classNames()?.any { it.contains("opened", ignoreCase = true) } == true

            terms.add(
                CurriculumTerm(
                    termNum = termNum,
                    termTitle = "$termNum семестр",
                    isOpened = isOpened,
                    isCurrent = false,
                    subjects = subjects
                )
            )
        }

        // If no termSpans were found, try finding any table with curriculum headers directly
        if (terms.isEmpty()) {
            val directTables = workarea.select("table")
            for ((idx, table) in directTables.withIndex()) {
                val rows = table.select("tr")
                val subjects = mutableListOf<CurriculumSubject>()
                for (tr in rows.drop(1)) {
                    val tds = tr.select("td, th").map { it.text().trim() }
                    if (tds.size >= 9) {
                        val cType = tds[1]
                        val category = when {
                            cType.contains("экзамен", ignoreCase = true) -> CurriculumControlType.EXAM
                            cType.contains("диф", ignoreCase = true) || cType.contains("зачет с оценкой", ignoreCase = true) -> CurriculumControlType.GRADED_TEST
                            cType.contains("зачет", ignoreCase = true) -> CurriculumControlType.TEST
                            else -> CurriculumControlType.OTHER
                        }
                        subjects.add(
                            CurriculumSubject(
                                subject = tds[0],
                                controlType = cType,
                                controlCategory = category,
                                hours = tds.getOrNull(2).orEmpty(),
                                zet = tds.getOrNull(3).orEmpty(),
                                currentScore = tds.getOrNull(4).orEmpty(),
                                currentRating = tds.getOrNull(5).orEmpty(),
                                termRating = tds.getOrNull(6).orEmpty(),
                                examScore = tds.getOrNull(7).orEmpty(),
                                finalRating = tds.getOrNull(8).orEmpty(),
                                grade = if (tds.size > 9) tds[9] else ""
                            )
                        )
                    }
                }
                if (subjects.isNotEmpty()) {
                    terms.add(
                        CurriculumTerm(
                            termNum = idx + 1,
                            termTitle = "${idx + 1} семестр",
                            isOpened = true,
                            isCurrent = idx == 0,
                            subjects = subjects
                        )
                    )
                }
            }
        }

        // Exactly ONE term must be marked as current
        val openedTerms = terms.filter { it.isOpened }
        var chosenCurrentTermNum: Int? = null

        if (openedTerms.isNotEmpty()) {
            // Check if an opened term has active non-zero scores and no final grades yet
            val termsWithActiveScores = openedTerms.filter { t ->
                t.subjects.any { s ->
                    s.currentScore.isNotBlank() &&
                            s.currentScore != "0" &&
                            s.currentScore != "0.0" &&
                            s.currentScore != "-" &&
                            s.grade.isBlank()
                }
            }

            if (termsWithActiveScores.isNotEmpty()) {
                chosenCurrentTermNum = termsWithActiveScores.first().termNum
            } else {
                // Determine by month: autumn/winter (Aug-Jan) is odd term (e.g. 7), spring (Feb-Jul) is even (e.g. 8)
                val nowMonth = LocalDate.now().monthValue
                val isAutumn = (nowMonth >= 8 || nowMonth == 1)
                val target = openedTerms.firstOrNull { (it.termNum % 2 != 0) == isAutumn }
                chosenCurrentTermNum = target?.termNum ?: openedTerms.first().termNum
            }
        } else if (terms.isNotEmpty()) {
            chosenCurrentTermNum = terms.first().termNum
        }

        val finalizedTerms = terms.map { term ->
            term.copy(isCurrent = (term.termNum == chosenCurrentTermNum))
        }.sortedByDescending { it.termNum }

        return finalizedTerms
    }
}
