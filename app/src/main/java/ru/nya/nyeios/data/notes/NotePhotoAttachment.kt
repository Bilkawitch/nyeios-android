package ru.nya.nyeios.data.notes

/**
 * Locally stored photo attachment. [relativePath] is relative to app filesDir so the
 * metadata survives app updates and backup/restore without exposing absolute paths.
 */
data class NotePhotoAttachment(
    val id: String = "",
    val relativePath: String = "",
    val caption: String = "",
    val mimeType: String = "",
    val addedAt: Long = 0L
)
