package ru.nya.nyeios.data.model

enum class CurriculumControlType(val title: String) {
    EXAM("Экзамен"),
    GRADED_TEST("Диф. зачёт"),
    TEST("Зачёт"),
    COURSEWORK("Курсовая"),
    OTHER("Контроль")
}

data class CurriculumSubject(
    val subject: String,
    val controlType: String,
    val controlCategory: CurriculumControlType = CurriculumControlType.OTHER,
    val hours: String = "",
    val zet: String = "",
    val currentScore: String = "",
    val currentRating: String = "",
    val termRating: String = "",
    val examScore: String = "",
    val finalRating: String = "",
    val grade: String = ""
)

data class CurriculumTerm(
    val termNum: Int,
    val termTitle: String,
    val isOpened: Boolean = false,
    val isCurrent: Boolean = false,
    val subjects: List<CurriculumSubject> = emptyList()
)

sealed interface CurriculumUiState {
    data object Loading : CurriculumUiState
    data object NotLoggedIn : CurriculumUiState
    data class Success(
        val terms: List<CurriculumTerm>,
        val selectedTermNum: Int,
        val isRefreshing: Boolean = false
    ) : CurriculumUiState
    data class Error(
        val message: String,
        val cachedTerms: List<CurriculumTerm>? = null
    ) : CurriculumUiState
}
