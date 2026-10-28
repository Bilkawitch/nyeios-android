package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.notes.LessonNote
import ru.nya.nyeios.data.notes.LessonNoteKey
import ru.nya.nyeios.data.notes.LessonNoteMigration
import ru.nya.nyeios.data.notes.NotePhotoAttachment

class LessonNoteMigrationTest {

    private fun note(
        key: String,
        date: String,
        number: String,
        time: String,
        subject: String,
        rawText: String,
        plainSnippet: String = rawText,
        updatedAt: Long = 1_000L,
        photos: List<NotePhotoAttachment> = emptyList()
    ) = LessonNote(
        key = key,
        date = date,
        lessonNumber = number,
        time = time,
        subject = subject,
        rawText = rawText,
        plainSnippet = plainSnippet,
        updatedAt = updatedAt,
        photoAttachments = photos
    )

    @Test
    fun migrate_repairsKeyThatDoesNotMatchFields() {
        val note = note(
            key = "broken_legacy_key",
            date = "20.10.2026",
            number = "2",
            time = "10:10 - 11:40",
            subject = "Пакеты прикладных программ современного учителя",
            rawText = "<b>Презентация по чуме</b>"
        )

        val result = LessonNoteMigration.migrate(listOf(note))

        assertEquals(1, result.rekeyedCount)
        assertEquals(1, result.notes.size)
        assertEquals(
            ru.nya.nyeios.data.notes.LessonNoteKey.buildKey(
                "20.10.2026", "10:10 - 11:40", "2", "Пакеты прикладных программ современного учителя"
            ),
            result.notes.single().key
        )
        assertEquals("<b>Презентация по чуме</b>", result.notes.single().rawText)
    }

    @Test
    fun migrate_collapsesIdenticalNotesSpilledOntoOtherPairs() {
        val chem = note(
            key = "chem", date = "22.10", number = "2", time = "10:10 - 11:40",
            subject = "Аналитическая химия", rawText = "Презентация по чуме", updatedAt = 100L
        )
        val plekhanova = note(
            key = "plekhanova", date = "20.10", number = "2", time = "10:10 - 11:40",
            subject = "Пакеты прикладных программ современного учителя", rawText = "Презентация по чуме", updatedAt = 200L
        )
        val fizra = note(
            key = "fizra", date = "20.10", number = "3", time = "12:10 - 13:40",
            subject = "Элективные дисциплины по физической культуре и спорту", rawText = "Презентация по чуме", updatedAt = 300L
        )

        val result = LessonNoteMigration.migrate(listOf(chem, plekhanova, fizra))

        assertEquals(1, result.notes.size)
        assertEquals("Аналитическая химия", result.notes.single().subject)
        assertEquals(2, result.removedDuplicates.size)
    }

    @Test
    fun migrate_doesNotTouchSameTextFromDifferentWeeks() {
        val weekOne = note(
            key = "a", date = "20.10", number = "2", time = "10:10 - 11:40",
            subject = "Аналитическая химия", rawText = "Презентация по чуме", updatedAt = 100L
        )
        val weekTwo = note(
            key = "b", date = "27.10", number = "2", time = "10:10 - 11:40",
            subject = "Пакеты прикладных программ современного учителя", rawText = "Презентация по чуме", updatedAt = 200L
        )

        val result = LessonNoteMigration.migrate(listOf(weekOne, weekTwo))

        assertEquals(2, result.notes.size)
        assertTrue(result.removedDuplicates.isEmpty())
    }

    @Test
    fun migrate_doesNotTouchNotesWithPhotos() {
        val photo = NotePhotoAttachment(
            id = "photo-1",
            relativePath = "note_photos/hash/photo.png",
            mimeType = "image/png"
        )
        val withPhoto = note(
            key = "a", date = "20.10", number = "2", time = "10:10 - 11:40",
            subject = "Аналитическая химия", rawText = "Презентация по чуме", updatedAt = 100L,
            photos = listOf(photo)
        )
        val withoutPhoto = note(
            key = "b", date = "20.10", number = "3", time = "12:10 - 13:40",
            subject = "Элективные дисциплины по физической культуре и спорту", rawText = "Презентация по чуме", updatedAt = 200L
        )

        val result = LessonNoteMigration.migrate(listOf(withPhoto, withoutPhoto))

        assertEquals(2, result.notes.size)
        assertTrue(result.removedDuplicates.isEmpty())
    }

    @Test
    fun migrate_doesNotTouchDifferentText() {
        val first = note(
            key = "a", date = "20.10", number = "2", time = "10:10 - 11:40",
            subject = "Аналитическая химия", rawText = "Презентация по чуме", updatedAt = 100L
        )
        val second = note(
            key = "b", date = "20.10", number = "3", time = "12:10 - 13:40",
            subject = "Пакеты прикладных программ современного учителя", rawText = "Конспект главы 4", updatedAt = 200L
        )

        val result = LessonNoteMigration.migrate(listOf(first, second))

        assertEquals(2, result.notes.size)
        assertTrue(result.removedDuplicates.isEmpty())
    }

    @Test
    fun migrate_doesNotTouchEmptyNotes() {
        val first = note(
            key = "a", date = "20.10", number = "2", time = "10:10 - 11:40",
            subject = "Аналитическая химия", rawText = "", plainSnippet = "", updatedAt = 100L
        )
        val second = note(
            key = "b", date = "20.10", number = "3", time = "12:10 - 13:40",
            subject = "Пакеты прикладных программ современного учителя", rawText = "", plainSnippet = "", updatedAt = 200L
        )

        val result = LessonNoteMigration.migrate(listOf(first, second))

        assertEquals(2, result.notes.size)
        assertTrue(result.removedDuplicates.isEmpty())
    }

    @Test
    fun migrate_isIdempotent() {
        val chem = note(
            key = "chem", date = "22.10", number = "2", time = "10:10 - 11:40",
            subject = "Аналитическая химия", rawText = "Презентация по чуме", updatedAt = 100L
        )
        val plekhanova = note(
            key = "plekhanova", date = "20.10", number = "2", time = "10:10 - 11:40",
            subject = "Пакеты прикладных программ современного учителя", rawText = "Презентация по чуме", updatedAt = 200L
        )

        val first = LessonNoteMigration.migrate(listOf(chem, plekhanova))
        val second = LessonNoteMigration.migrate(first.notes)

        assertTrue(first.changed)
        assertFalse(second.changed)
        assertEquals(1, second.notes.size)
        assertTrue(second.removedDuplicates.isEmpty())
    }

    @Test
    fun migrate_keepsLatestVersionWhenSameCanonicalKeyAppearsTwice() {
        val older = note(
            key = "a", date = "20.10", number = "2", time = "10:10 - 11:40",
            subject = "Аналитическая химия", rawText = "старый текст", updatedAt = 100L
        )
        val newer = note(
            key = "a", date = "20.10", number = "2", time = "10:10 - 11:40",
            subject = "Аналитическая химия", rawText = "новый текст", updatedAt = 200L
        )

        val result = LessonNoteMigration.migrate(listOf(older, newer))

        assertEquals(1, result.notes.size)
        assertEquals("новый текст", result.notes.single().rawText)
        assertTrue(result.removedDuplicates.isEmpty())
    }

    @Test
    fun migrate_reportedPlagueCaseKeepsOnlyTheChemistryPair() {
        val chemistry = note(
            key = LessonNoteKey.buildKey("29.10", "10:10 - 11:40", "2", "Аналитическая химия"),
            date = "29.10", number = "2", time = "10:10 - 11:40",
            subject = "Аналитическая химия", rawText = "Презентация по чуме", updatedAt = 1_000L
        )
        val plekhanova = note(
            key = LessonNoteKey.buildKey(
                "27.10", "10:10 - 11:40", "2", "Пакеты прикладных программ современного учителя"
            ),
            date = "27.10", number = "2", time = "10:10 - 11:40",
            subject = "Пакеты прикладных программ современного учителя", rawText = "Презентация по чуме", updatedAt = 2_000L
        )
        val physicalEducation = note(
            key = LessonNoteKey.buildKey(
                "27.10", "12:10 - 13:40", "3",
                "Элективные дисциплины по физической культуре и спорту (элективные дисциплины)"
            ),
            date = "27.10", number = "3", time = "12:10 - 13:40",
            subject = "Элективные дисциплины по физической культуре и спорту (элективные дисциплины)",
            rawText = "Презентация по чуме", updatedAt = 3_000L
        )

        val result = LessonNoteMigration.migrate(listOf(chemistry, plekhanova, physicalEducation))

        assertEquals(1, result.notes.size)
        assertEquals(
            LessonNoteKey.buildKey("29.10", "10:10 - 11:40", "2", "Аналитическая химия"),
            result.notes.single().key
        )
        assertEquals(2, result.removedDuplicates.size)
        assertTrue(result.removedDuplicates.all { it.rawText == "Презентация по чуме" })
    }

    @Test
    fun migrate_doesNotDeduplicateShortRepeatedNotes() {
        val shortNo = note(
            key = LessonNoteKey.buildKey("20.10", "10:10 - 11:40", "2", "Аналитическая химия"),
            date = "20.10", number = "2", time = "10:10 - 11:40",
            subject = "Аналитическая химия", rawText = "нет", updatedAt = 100L
        )
        val shortNoOtherPair = note(
            key = LessonNoteKey.buildKey(
                "20.10", "12:10 - 13:40", "3", "Пакеты прикладных программ современного учителя"
            ),
            date = "20.10", number = "3", time = "12:10 - 13:40",
            subject = "Пакеты прикладных программ современного учителя", rawText = "нет", updatedAt = 200L
        )
        val seeLecture = note(
            key = LessonNoteKey.buildKey("22.10", "10:10 - 11:40", "2", "Аналитическая химия"),
            date = "22.10", number = "2", time = "10:10 - 11:40",
            subject = "Аналитическая химия", rawText = "см. лекцию", updatedAt = 300L
        )
        val seeLectureOtherPair = note(
            key = LessonNoteKey.buildKey(
                "22.10", "12:10 - 13:40", "3", "Элективные дисциплины по физической культуре и спорту"
            ),
            date = "22.10", number = "3", time = "12:10 - 13:40",
            subject = "Элективные дисциплины по физической культуре и спорту", rawText = "см. лекцию", updatedAt = 400L
        )

        val result = LessonNoteMigration.migrate(
            listOf(shortNo, shortNoOtherPair, seeLecture, seeLectureOtherPair)
        )

        assertEquals(4, result.notes.size)
        assertTrue(result.removedDuplicates.isEmpty())
        assertFalse(result.changed)
    }
}
