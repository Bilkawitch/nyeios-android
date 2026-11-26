package ru.nya.nyeios

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.nya.nyeios.data.autoupdate.AutoUpdateNotificationText
import ru.nya.nyeios.data.autoupdate.AutoUpdateStage
import ru.nya.nyeios.data.changes.CurriculumChanges
import ru.nya.nyeios.data.changes.LessonChangeKind
import ru.nya.nyeios.data.changes.LessonRef
import ru.nya.nyeios.data.changes.ScheduleChanges
import ru.nya.nyeios.data.changes.ScoreChange
import ru.nya.nyeios.data.changes.ScoreField
import ru.nya.nyeios.data.changes.TermChanges
import ru.nya.nyeios.data.changes.WeekChanges
import java.util.Locale

/**
 * Форматирование строк уведомлений автообновления: RU/EN, обрезка 64 символа,
 * лимит строк InboxStyle, переполнение «+N ещё», проценты стадий.
 */
class AutoUpdateNotificationTextTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale("ru", "RU"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    // --- фабрики моделей (строго по контракту §2) ---

    private fun lesson(
        date: String = "26.07",
        day: String = "Чт",
        subject: String = "Лексика",
        lessonNumber: String = "2",
        subgroup: String = ""
    ): LessonRef = LessonRef(
        weekKey = "21.07.2026_27.07.2026",
        dateString = date,
        dayName = day,
        lessonNumber = lessonNumber,
        subgroup = subgroup,
        time = "10:15 - 11:45",
        subject = subject,
        room = "401",
        teacher = "Иванова И.И.",
        type = "PRACTICE"
    )

    private fun week(
        weekKey: String = "21.07.2026_27.07.2026",
        added: List<LessonRef> = emptyList(),
        modified: List<LessonRef> = emptyList(),
        removed: List<LessonRef> = emptyList()
    ): WeekChanges = WeekChanges(
        weekKey = weekKey,
        added = added,
        modified = modified,
        removed = removed
    )

    private fun schedule(vararg weeks: WeekChanges): ScheduleChanges =
        ScheduleChanges(weeks.associateBy { it.weekKey })

    private fun score(
        subject: String = "Математика",
        old: String = "5",
        new: String = "8",
        termNum: Int = 1
    ): ScoreChange = ScoreChange(
        termNum = termNum,
        subject = subject,
        controlType = "Экзамен",
        field = ScoreField.CURRENT_SCORE,
        oldValue = old,
        newValue = new
    )

    private fun curriculum(vararg terms: TermChanges): CurriculumChanges =
        CurriculumChanges(terms.associateBy { it.termNum })

    // --- truncate ---

    @Test
    fun truncateKeepsShortText() {
        assertEquals("Лексика", AutoUpdateNotificationText.truncate("Лексика"))
    }

    @Test
    fun truncateKeepsTextExactlyAtLimit() {
        assertEquals("abcd", AutoUpdateNotificationText.truncate("abcd", 4))
    }

    @Test
    fun truncateLongTextTo64WithEllipsis() {
        val text = "А".repeat(100)
        val truncated = AutoUpdateNotificationText.truncate(text)
        assertEquals(AutoUpdateNotificationText.MAX_LINE_LENGTH, truncated.length)
        assertEquals("А".repeat(63) + "…", truncated)
        assertTrue(truncated.endsWith("…"))
    }

    @Test
    fun truncateRespectsCustomLimit() {
        assertEquals("abc…", AutoUpdateNotificationText.truncate("abcdefgh", 4))
    }

    @Test
    fun truncateHandlesDegenerateLimits() {
        assertEquals("", AutoUpdateNotificationText.truncate("abc", 0))
        assertEquals("", AutoUpdateNotificationText.truncate("abc", -5))
        assertEquals("…", AutoUpdateNotificationText.truncate("abc", 1))
    }

    // --- строки расписания ---

    @Test
    fun scheduleLineRuFormatsAllKinds() {
        val ref = lesson()
        assertEquals(
            "26.07 ЧТ добавлена: Лексика",
            AutoUpdateNotificationText.scheduleLine(ref, LessonChangeKind.ADDED)
        )
        assertEquals(
            "26.07 ЧТ удален(а): Лексика",
            AutoUpdateNotificationText.scheduleLine(ref, LessonChangeKind.REMOVED)
        )
        assertEquals(
            "26.07 ЧТ изменена: Лексика",
            AutoUpdateNotificationText.scheduleLine(ref, LessonChangeKind.MODIFIED)
        )
    }

    @Test
    fun scheduleLineEnFormatsAllKinds() {
        Locale.setDefault(Locale.ENGLISH)
        val ref = lesson(day = "Thu", subject = "Lexis")
        assertEquals(
            "26.07 THU added: Lexis",
            AutoUpdateNotificationText.scheduleLine(ref, LessonChangeKind.ADDED)
        )
        assertEquals(
            "26.07 THU removed: Lexis",
            AutoUpdateNotificationText.scheduleLine(ref, LessonChangeKind.REMOVED)
        )
        assertEquals(
            "26.07 THU changed: Lexis",
            AutoUpdateNotificationText.scheduleLine(ref, LessonChangeKind.MODIFIED)
        )
    }

    @Test
    fun scheduleLineTruncatesLongSubjectTo64Chars() {
        val longSubject = "Очень длинное название дисциплины ".repeat(5)
        val ref = lesson(subject = longSubject)
        val line = AutoUpdateNotificationText.scheduleLine(ref, LessonChangeKind.ADDED)
        val truncated = AutoUpdateNotificationText.truncate(longSubject)

        assertTrue("line must end with truncated subject: $line", line.endsWith(truncated))
        assertTrue(truncated.endsWith("…"))
        assertEquals(AutoUpdateNotificationText.MAX_LINE_LENGTH, truncated.length)
        assertFalse(line.contains(longSubject))
    }

    // --- строки БРС ---

    @Test
    fun scoreLineFormatsDelta() {
        assertEquals("Математика: 5 → 8", AutoUpdateNotificationText.scoreLine(score()))
    }

    @Test
    fun scoreLineShowsDashForEmptyOldValue() {
        assertEquals(
            "Математика: — → 12",
            AutoUpdateNotificationText.scoreLine(score(old = "", new = "12"))
        )
    }

    @Test
    fun scoreLineTruncatesLongSubject() {
        val longSubject = "Очень длинное название предмета БРС ".repeat(5)
        val line = AutoUpdateNotificationText.scoreLine(score(subject = longSubject))
        val truncated = AutoUpdateNotificationText.truncate(longSubject)
        assertTrue(line.startsWith(truncated))
        assertTrue(line.endsWith("5 → 8"))
    }

    // --- заголовки и итоговый заголовок ---

    @Test
    fun sectionHeadersRu() {
        assertEquals("Изменения расписания:", AutoUpdateNotificationText.sectionScheduleHeader())
        assertEquals("Изменения БРС:", AutoUpdateNotificationText.sectionScoresHeader())
    }

    @Test
    fun sectionHeadersEn() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Schedule changes:", AutoUpdateNotificationText.sectionScheduleHeader())
        assertEquals("Grade changes:", AutoUpdateNotificationText.sectionScoresHeader())
    }

    @Test
    fun resultTitleDependsOnChangesAndLocale() {
        assertEquals(
            "NyEIOS: есть изменения",
            AutoUpdateNotificationText.resultTitle(true)
        )
        assertEquals("NyEIOS обновлён", AutoUpdateNotificationText.resultTitle(false))

        Locale.setDefault(Locale.ENGLISH)
        assertEquals(
            "NyEIOS: new changes",
            AutoUpdateNotificationText.resultTitle(true)
        )
        assertEquals("NyEIOS updated", AutoUpdateNotificationText.resultTitle(false))
    }

    // --- summaryLines ---

    @Test
    fun summaryLinesAreEmptyWhenNothingChanged() {
        assertTrue(
            AutoUpdateNotificationText.summaryLines(ScheduleChanges(), CurriculumChanges())
                .isEmpty()
        )
    }

    @Test
    fun summaryLinesShowOnlyScheduleSectionWhenNoScores() {
        val schedule = schedule(
            week(
                added = listOf(lesson(subject = "A"), lesson(subject = "B"))
            )
        )
        val lines = AutoUpdateNotificationText.summaryLines(schedule, CurriculumChanges())
        assertEquals(
            listOf(
                "Изменения расписания:",
                "26.07 ЧТ добавлена: A",
                "26.07 ЧТ добавлена: B"
            ),
            lines
        )
    }

    @Test
    fun summaryLinesShowOnlyScoresSectionWhenNoSchedule() {
        val changes = curriculum(TermChanges(1, listOf(score())))
        val lines = AutoUpdateNotificationText.summaryLines(ScheduleChanges(), changes)
        assertEquals(
            listOf("Изменения БРС:", "Математика: 5 → 8"),
            lines
        )
    }

    @Test
    fun summaryLinesOrderScheduleFirstThenScores() {
        val schedule = schedule(week(added = listOf(lesson(subject = "A"))))
        val changes = curriculum(TermChanges(1, listOf(score())))
        val lines = AutoUpdateNotificationText.summaryLines(schedule, changes)
        assertEquals(
            listOf(
                "Изменения расписания:",
                "26.07 ЧТ добавлена: A",
                "Изменения БРС:",
                "Математика: 5 → 8"
            ),
            lines
        )
    }

    @Test
    fun summaryLinesRespectMaxLinesForSingleSectionOverflow() {
        val schedule = schedule(
            week(added = (1..10).map { lesson(subject = "Предмет $it") })
        )
        val lines = AutoUpdateNotificationText.summaryLines(schedule, CurriculumChanges(), maxLines = 6)
        assertEquals(6, lines.size)
        assertEquals("Изменения расписания:", lines.first())
        assertEquals("+6 ещё", lines.last())
    }

    @Test
    fun summaryLinesOverflowForBothSectionsKeepsBothHeaders() {
        val schedule = schedule(
            week(added = (1..5).map { lesson(subject = "Пара $it") })
        )
        val changes = curriculum(
            TermChanges(1, (1..5).map { score(subject = "Предмет $it") })
        )
        val lines = AutoUpdateNotificationText.summaryLines(schedule, changes, maxLines = 6)
        assertEquals(6, lines.size)
        assertTrue(lines.contains("Изменения расписания:"))
        assertTrue(lines.contains("Изменения БРС:"))
        assertEquals("+7 ещё", lines.last())
    }

    @Test
    fun summaryLinesOverflowUsesEnglishWhenLocaleIsEnglish() {
        Locale.setDefault(Locale.ENGLISH)
        val schedule = schedule(
            week(weekKey = "w1", added = (1..10).map { lesson(subject = "Subject $it") })
        )
        val lines = AutoUpdateNotificationText.summaryLines(schedule, CurriculumChanges(), maxLines = 6)
        assertEquals("Schedule changes:", lines.first())
        assertEquals("+6 more", lines.last())
    }

    @Test
    fun summaryLinesNeverExceedMaxLines() {
        val structures = listOf(
            schedule(week(added = (1..10).map { lesson(subject = "A$it") })) to CurriculumChanges(),
            ScheduleChanges() to curriculum(TermChanges(1, (1..10).map { score(subject = "S$it") })),
            schedule(week(added = (1..5).map { lesson(subject = "A$it") })) to
                curriculum(TermChanges(1, (1..5).map { score(subject = "S$it") }))
        )
        for ((scheduleChanges, curriculumChanges) in structures) {
            for (maxLines in 1..10) {
                val lines = AutoUpdateNotificationText.summaryLines(
                    scheduleChanges,
                    curriculumChanges,
                    maxLines
                )
                assertTrue(
                    "maxLines=$maxLines produced ${lines.size} lines: $lines",
                    lines.size <= maxLines
                )
            }
        }
    }

    @Test
    fun summaryLinesZeroOrNegativeLimitGivesNoLines() {
        val schedule = schedule(week(added = listOf(lesson())))
        assertTrue(AutoUpdateNotificationText.summaryLines(schedule, CurriculumChanges(), 0).isEmpty())
        assertTrue(AutoUpdateNotificationText.summaryLines(schedule, CurriculumChanges(), -3).isEmpty())
    }

    @Test
    fun summaryLinesOrdersWeeksByWeekKey() {
        val first = week(
            weekKey = "01.09.2026_07.09.2026",
            added = listOf(lesson(subject = "Ранняя"))
        )
        val second = week(
            weekKey = "08.09.2026_14.09.2026",
            added = listOf(lesson(subject = "Поздняя"))
        )
        val lines = AutoUpdateNotificationText.summaryLines(
            ScheduleChanges(mapOf(second.weekKey to second, first.weekKey to first)),
            CurriculumChanges()
        )
        assertEquals(
            listOf(
                "Изменения расписания:",
                "26.07 ЧТ добавлена: Ранняя",
                "26.07 ЧТ добавлена: Поздняя"
            ),
            lines
        )
    }

    // --- проценты стадий ---

    @Test
    fun progressPercentForEachStage() {
        assertEquals(5, AutoUpdateNotificationText.progressPercentFor(AutoUpdateStage.START))
        assertEquals(45, AutoUpdateNotificationText.progressPercentFor(AutoUpdateStage.SCHEDULE))
        assertEquals(65, AutoUpdateNotificationText.progressPercentFor(AutoUpdateStage.NEXT_WEEK))
        assertEquals(85, AutoUpdateNotificationText.progressPercentFor(AutoUpdateStage.CURRICULUM))
        assertEquals(100, AutoUpdateNotificationText.progressPercentFor(AutoUpdateStage.COMPARE))
    }

    @Test
    fun progressPercentsAreMonotonic() {
        val percents = AutoUpdateStage.values().map {
            AutoUpdateNotificationText.progressPercentFor(it)
        }
        assertEquals(percents.sorted(), percents)
    }
}
