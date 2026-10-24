package ru.nya.nyeios

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.notes.LessonNote
import ru.nya.nyeios.data.notes.LessonTag
import ru.nya.nyeios.data.notes.NotePhotoAttachment
import ru.nya.nyeios.data.notes.hasVisibleContent

class LessonTagTest {

    @Test
    fun derivesTagFromLessonSubject() {
        assertEquals(
            "#практика-устной-и-письменной-речи-немецкого-языка",
            LessonTag.fromSubject("Практика устной и письменной речи немецкого языка")
        )
    }

    @Test
    fun dropsDigitsAndPunctuationWithoutGlueingWordsTogether() {
        assertEquals("#английский-язык-b", LessonTag.fromSubject("Английский язык (B1.2)"))
        assertEquals("#тема-итоги", LessonTag.fromSubject("Тема 2. Итоги"))
        assertEquals("#история-россии-xix-xx-вв", LessonTag.fromSubject("История России: XIX–XX вв."))
    }

    @Test
    fun returnsNullWhenSubjectHasNoLetters() {
        assertNull(LessonTag.fromSubject("2026 ..."))
        assertNull(LessonTag.fromSubject("   "))
    }

    @Test
    fun tagDoesNotMakeNoteVisible() {
        val note = note(rawText = "", plainSnippet = "")
        assertFalse(note.hasVisibleContent)
        assertEquals("#философия-семестр", note.lessonTag)
    }

    @Test
    fun photoMakesNoteVisible() {
        val note = note(
            photoAttachments = listOf(
                NotePhotoAttachment(id = "1", relativePath = "note_photos/a/1.jpg", caption = "")
            )
        )
        assertTrue(note.hasVisibleContent)
    }

    @Test
    fun legacyJsonWithoutAttachmentsNormalizesToEmptyList() {
        val json = """
            [{"key":"20.10_1_10:00_философия","date":"20.10","lessonNumber":"1",
            "time":"10:00","subject":"Философия","teacher":"","rawText":"",
            "plainSnippet":"","updatedAt":1}]
        """.trimIndent()
        val old = Gson().fromJson(json, Array<LessonNote>::class.java).first()
        assertTrue(old.normalized().photoAttachments.isEmpty())
    }

    private fun note(
        rawText: String = "<b>домашка</b>",
        plainSnippet: String = "домашка",
        photoAttachments: List<NotePhotoAttachment> = emptyList()
    ) = LessonNote(
        key = "20.10_1_10:00_философия",
        date = "20.10",
        lessonNumber = "1",
        time = "10:00",
        subject = "Философия (2 семестр)",
        teacher = "",
        rawText = rawText,
        plainSnippet = plainSnippet,
        photoAttachments = photoAttachments
    )
}
