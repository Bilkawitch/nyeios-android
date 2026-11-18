package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.nya.nyeios.data.reminders.Reminder
import ru.nya.nyeios.data.reminders.ReminderStore
import java.io.File

/**
 * JVM-проверки файлового хранилища: [ReminderStore] намеренно не трогает Android API,
 * поэтому тест работает с обычным временным каталогом.
 */
class ReminderStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = ReminderStore(folder.root)

    private fun sampleReminder(
        id: String = "reminder-1",
        subject: String = "Математика",
        noteText: String = "Решить задачу 5",
        triggerAtUtcMs: Long = 1_764_000_000_000L
    ) = Reminder(
        id = id,
        noteKey = "note-key-1",
        subject = subject,
        lessonDate = "12.11.2026",
        lessonTime = "10:15 - 11:45",
        lessonNumber = "2",
        noteText = noteText,
        triggerAtUtcMs = triggerAtUtcMs,
        createdAtUtcMs = 1_764_000_000_001L,
        updatedAtUtcMs = 1_764_000_000_002L
    )

    @Test
    fun storageFileLivesDirectlyInFilesDir() {
        val file = store().storageFile()
        assertEquals(folder.root.canonicalFile, file.parentFile?.canonicalFile)
        assertEquals("reminders.json", file.name)
    }

    @Test
    fun missingFileLoadsEmptyList() {
        assertTrue(store().load().isEmpty())
    }

    @Test
    fun saveAndLoadRoundTrip() {
        val reminder = sampleReminder()
        val store = store()
        store.save(listOf(reminder))
        assertTrue(store.storageFile().isFile)

        val loaded = ReminderStore(folder.root).load()
        assertEquals(listOf(reminder), loaded)
    }

    @Test
    fun loadPreservesOrder() {
        val first = sampleReminder(id = "a", triggerAtUtcMs = 1_000L)
        val second = sampleReminder(id = "b", subject = "Физика", triggerAtUtcMs = 2_000L)
        store().save(listOf(first, second))

        assertEquals(listOf("a", "b"), store().load().map { it.id })
    }

    @Test
    fun saveReplacesPreviousContent() {
        val store = store()
        store.save(listOf(sampleReminder(id = "old")))
        store.save(listOf(sampleReminder(id = "new-1"), sampleReminder(id = "new-2")))

        assertEquals(listOf("new-1", "new-2"), store.load().map { it.id })
    }

    @Test
    fun saveEmptyListRoundTrips() {
        val store = store()
        store.save(emptyList())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun saveLeavesNoTemporaryFileBehind() {
        val store = store()
        store.save(listOf(sampleReminder()))

        val leftovers = folder.root.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }
        assertTrue("temporary files left: ${leftovers.map { it.name }}", leftovers.isEmpty())
    }

    @Test
    fun corruptedJsonLoadsEmptyList() {
        val store = store()
        store.storageFile().writeText("{ this is not valid json ]")
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun nullJsonLoadsEmptyList() {
        val store = store()
        store.storageFile().writeText("null")
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun blankFileLoadsEmptyList() {
        val store = store()
        store.storageFile().writeText("   ")
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun nullEntriesAreIgnored() {
        val store = store()
        store.storageFile().writeText("[null]")
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun unknownFieldsAreTolerated() {
        val store = store()
        store.storageFile().writeText(
            """
            [
              {
                "id": "legacy-1",
                "noteKey": "k",
                "subject": "История",
                "lessonDate": "01.09.2026",
                "lessonTime": "08:30 - 10:00",
                "lessonNumber": "1",
                "noteText": "Прочитать главу",
                "triggerAtUtcMs": 1000,
                "createdAtUtcMs": 2,
                "updatedAtUtcMs": 3,
                "legacyField": "ignored",
                "notificationSnooze": 42
              }
            ]
            """.trimIndent()
        )

        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals("legacy-1", loaded.single().id)
        assertEquals("История", loaded.single().subject)
        assertEquals(1000L, loaded.single().triggerAtUtcMs)
    }
}
