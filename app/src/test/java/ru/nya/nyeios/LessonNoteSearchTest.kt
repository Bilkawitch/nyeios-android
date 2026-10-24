package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.notes.LessonNote
import ru.nya.nyeios.data.notes.LessonNoteSearch
import ru.nya.nyeios.data.notes.NotePhotoAttachment

class LessonNoteSearchTest {

    private val philosophy = note(
        key = "philosophy",
        subject = "Философия",
        rawText = "<b>Конспект по философии</b>: всё готово"
    )
    private val german = note(
        key = "german",
        subject = "Практика устной и письменной речи немецкого языка",
        rawText = "Подготовить доклад",
        photos = listOf(
            NotePhotoAttachment(
                id = "p1",
                relativePath = "note_photos/german/p1.jpg",
                caption = "Фото конспекта с доски"
            )
        )
    )

    @Test
    fun searchesFullNoteTextBeyondSnippetLimit() {
        val longText = "начало " + "очень длинный текст ".repeat(20) + "уникальноеслово"
        val index = LessonNoteSearch.build(listOf(note(key = "long", rawText = longText)))

        assertEquals(setOf("long"), index.search("уникальноеслово", null))
    }

    @Test
    fun searchesPhotoCaptions() {
        val index = LessonNoteSearch.build(listOf(philosophy, german))

        assertEquals(setOf("german"), index.search("конспекта", null))
    }

    @Test
    fun supportsPrefixAndRussianCaseFolding() {
        val index = LessonNoteSearch.build(listOf(philosophy, german))

        assertEquals(setOf("philosophy"), index.search("ФИЛОС", null))
        assertEquals(setOf("philosophy"), index.search("все", null))
    }

    @Test
    fun toleratesOneRussianTypoInLongWord() {
        val index = LessonNoteSearch.build(listOf(philosophy, german))

        assertEquals(setOf("philosophy"), index.search("филосфии", null))
    }

    @Test
    fun requiresEveryQueryWordToMatch() {
        val index = LessonNoteSearch.build(listOf(philosophy, german))

        assertEquals(setOf("philosophy"), index.search("конспект готово", null))
        assertTrue(index.search("конспект немецкого", null).isEmpty())
    }

    @Test
    fun filtersByLessonTagAndCombinesItWithText() {
        val index = LessonNoteSearch.build(listOf(philosophy, german))
        val germanTag = LessonNoteSearch.tagSlug(german)

        assertEquals(setOf("german"), index.search("", germanTag))
        assertEquals(setOf("german"), index.search("доклад", germanTag))
        assertTrue(index.search("конспект", germanTag).isEmpty())
    }

    @Test
    fun blankQueryWithoutTagReturnsOnlyNotesWithVisibleContent() {
        val empty = note(key = "empty", rawText = "", snippet = "")
        val index = LessonNoteSearch.build(listOf(philosophy, empty))

        assertEquals(setOf("philosophy"), index.search("", null))
    }

    private fun note(
        key: String,
        subject: String = "Философия",
        rawText: String,
        snippet: String = rawText.take(120),
        photos: List<NotePhotoAttachment> = emptyList()
    ) = LessonNote(
        key = key,
        date = "20.10",
        lessonNumber = "1",
        time = "10:00",
        subject = subject,
        teacher = "",
        rawText = rawText,
        plainSnippet = snippet,
        photoAttachments = photos
    )
}
