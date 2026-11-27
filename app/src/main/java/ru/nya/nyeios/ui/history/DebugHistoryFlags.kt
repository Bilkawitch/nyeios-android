package ru.nya.nyeios.ui.history

import ru.nya.nyeios.data.model.CurriculumControlType
import ru.nya.nyeios.data.model.CurriculumSubject
import ru.nya.nyeios.data.model.CurriculumTerm

/**
 * Debug-only переключатели для визуальной проверки истории баллов на эмуляторе.
 *
 * Включаются intent-extra'ми MainActivity (`nyeios_debug_fake_curriculum`), в release-сборке
 * не используются. Нужны, потому что вкладка БРС при отсутствии авторизации показывает экран
 * входа, а проверять кнопку «История» и графики надо без реальных данных портала.
 */
object DebugHistoryFlags {

    @Volatile
    var fakeCurriculum: Boolean = false

    /** Синтетический семестр, согласованный с [ru.nya.nyeios.data.history.DebugHistorySeeder]. */
    fun fakeTerms(): List<CurriculumTerm> {
        val subjects = listOf(
            CurriculumSubject(
                subject = "Практика устной и письменной речи английского языка",
                controlType = "Зачет с оценкой",
                controlCategory = CurriculumControlType.GRADED_TEST,
                hours = "144",
                zet = "4",
                currentScore = "91.6",
                finalRating = "91.6",
                grade = "отлично"
            ),
            CurriculumSubject(
                subject = "Литература немецкоязычных стран",
                controlType = "Экзамен",
                controlCategory = CurriculumControlType.EXAM,
                hours = "108",
                zet = "3",
                currentScore = "62",
                finalRating = "62",
                grade = "удовлетворительно"
            ),
            CurriculumSubject(
                subject = "Стилистика английского языка",
                controlType = "Зачет",
                controlCategory = CurriculumControlType.TEST,
                hours = "72",
                zet = "2",
                currentScore = "70",
                finalRating = "70",
                grade = "зачтено"
            )
        )
        return listOf(
            CurriculumTerm(
                termNum = 7,
                termTitle = "7 семестр",
                isOpened = true,
                isCurrent = true,
                subjects = subjects
            )
        )
    }
}
