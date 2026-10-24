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
    val updatedAt: Long = System.currentTimeMillis(),
    val photoAttachments: List<NotePhotoAttachment> = emptyList()
) {
    /** Derived metadata. It is deliberately absent from [rawText]. */
    val lessonTag: String?
        get() = LessonTag.fromSubject(subject)

    fun normalized(): LessonNote = copy(
        date = date.orEmpty(),
        lessonNumber = lessonNumber.orEmpty(),
        time = time.orEmpty(),
        subject = subject.orEmpty(),
        teacher = teacher.orEmpty(),
        rawText = rawText.orEmpty(),
        plainSnippet = plainSnippet.orEmpty(),
        photoAttachments = photoAttachments.orEmpty()
            .filter { it.relativePath.isNotBlank() }
            .map {
                it.copy(
                    id = it.id.ifBlank { it.relativePath },
                    caption = it.caption.orEmpty(),
                    mimeType = it.mimeType.orEmpty()
                )
            }
    )
}

/** A tag-only note is still empty; photos and captions are real user content. */
val LessonNote.hasVisibleContent: Boolean
    get() = rawText.isNotBlank() || photoAttachments.orEmpty().isNotEmpty()
