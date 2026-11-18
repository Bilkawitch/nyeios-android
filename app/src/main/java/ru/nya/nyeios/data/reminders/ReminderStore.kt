package ru.nya.nyeios.data.reminders

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Файловое хранилище напоминаний (JSON, атомарная запись через временный файл).
 *
 * Намеренно не зависит от Android [android.content.Context]: принимает каталог,
 * поэтому покрывается обычными JVM-тестами.
 */
class ReminderStore(private val filesDir: File) {

    private val gson = Gson()
    private val listType = object : TypeToken<List<Reminder>>() {}.type

    @Synchronized
    fun storageFile(): File = File(filesDir, FILE_NAME)

    @Synchronized
    fun load(): List<Reminder> {
        val file = storageFile()
        if (!file.isFile) return emptyList()
        return try {
            val json = file.readText(Charsets.UTF_8)
            if (json.isBlank()) return emptyList()
            val parsed: List<Reminder?>? = gson.fromJson(json, listType)
            parsed.orEmpty().filterNotNull().map { it.normalized() }
        } catch (_: Exception) {
            // Битый файл не должен ронять приложение: напоминаний просто нет.
            emptyList()
        }
    }

    @Synchronized
    fun save(reminders: List<Reminder>) {
        filesDir.mkdirs()
        val target = storageFile()
        val temp = File(filesDir, TEMP_FILE_NAME)
        try {
            temp.writeText(gson.toJson(reminders.map { it.normalized() }), Charsets.UTF_8)
            moveReplacing(temp, target)
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
            return
        } catch (_: Exception) {
            // Некоторые ФС не поддерживают ATOMIC_MOVE — повторяем без него.
        }
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            if (!source.renameTo(target)) throw e
        }
    }

    companion object {
        const val FILE_NAME: String = "reminders.json"
        private const val TEMP_FILE_NAME: String = "$FILE_NAME.tmp"
    }
}
