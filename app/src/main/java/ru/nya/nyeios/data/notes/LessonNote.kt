package ru.nya.nyeios.data.notes

/**
 * Domain-модель заметки / домашнего задания к конкретной паре.
 */
data class LessonNote(
    val key: String,
    val date: String,
    val lessonNumber: String,
    val time: String,
    val subject: String,
    val teacher: String = "",
    val rawText: String = "",
    val plainSnippet: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
