package ru.nya.nyeios.data.model

enum class LessonType(val title: String) {
    LECTURE("Лекция"),
    SEMINAR("Семинар"),
    PRACTICE("Практика"),
    LAB("Лабораторная"),
    EXAM("Экзамен / Зачёт"),
    OTHER("Занятие")
}

data class LessonItem(
    val time: String,
    val lessonNumber: String = "",
    val subgroup: String = "",
    val subject: String,
    val room: String,
    val teacher: String,
    val type: LessonType
)

data class DaySchedule(
    val dayTitle: String, // e.g. "Понедельник 15.09"
    val dayName: String,  // e.g. "Пн"
    val dateString: String, // e.g. "15.09.2026"
    val isToday: Boolean = false,
    val lessons: List<LessonItem> = emptyList()
)

data class WeekSchedule(
    val weekTitle: String,
    val group: String?,
    val startDate: String,
    val endDate: String,
    val offsetWeeks: Int,
    val days: List<DaySchedule>,
    val isCached: Boolean = false
)

data class AuthSession(
    val username: String = "",
    val cookies: String = "",
    val isLoggedIn: Boolean = false,
    val authSource: String = "none" // "saved", "manual"
)

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data object NotLoggedIn : ScheduleUiState
    data class Success(
        val schedule: WeekSchedule,
        val isRefreshing: Boolean = false
    ) : ScheduleUiState
    data class Error(
        val message: String,
        val cachedSchedule: WeekSchedule? = null
    ) : ScheduleUiState
}
