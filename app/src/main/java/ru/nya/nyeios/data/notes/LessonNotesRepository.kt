package ru.nya.nyeios.data.notes

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.nya.nyeios.data.model.WeekSchedule
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class LessonNotesRepository(private val context: Context) {

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val fileMutex = Mutex()
    val photoStore = NotePhotoStore(context)
    private val notesFile: File
        get() = File(context.filesDir, "lesson_notes.json")

    private val notesMap = ConcurrentHashMap<String, LessonNote>()
    private val _notesFlow = MutableStateFlow<Map<String, LessonNote>>(emptyMap())
    val notesFlow: StateFlow<Map<String, LessonNote>> = _notesFlow.asStateFlow()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        try {
            val file = notesFile
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<List<LessonNote>>() {}.type
                val list: List<LessonNote>? = gson.fromJson(json, type)
                if (list != null) {
                    list.forEach { loaded ->
                        val note = loaded.normalized()
                        notesMap[note.key] = note
                    }
                    _notesFlow.value = notesMap.toMap()
                }
            }
            photoStore.cleanupOrphans(notesMap.values)
        } catch (_: Exception) {
            // Ignore corrupted disk file
        }
    }

    fun getNote(key: String): LessonNote? {
        return notesMap[key]
    }

    fun saveNote(note: LessonNote) {
        val normalized = note.normalized()
        notesMap[normalized.key] = normalized
        _notesFlow.value = notesMap.toMap()
        persistAsync()
    }

    fun deleteNote(key: String) {
        notesMap.remove(key)?.let { note ->
            note.photoAttachments.orEmpty().forEach(photoStore::deleteAttachment)
            photoStore.deleteNoteFiles(key)
        }
        _notesFlow.value = notesMap.toMap()
        persistAsync()
    }

    private fun persistAsync() {
        scope.launch {
            persistSync()
        }
    }

    suspend fun persistSync() = fileMutex.withLock {
        try {
            val file = notesFile
            val tempFile = File(context.filesDir, "lesson_notes.tmp")
            val list = notesMap.values.toList()
            val json = gson.toJson(list)
            FileOutputStream(tempFile).use { fos ->
                fos.write(json.toByteArray(Charsets.UTF_8))
                fos.flush()
            }
            if (tempFile.exists()) {
                if (file.exists()) file.delete()
                tempFile.renameTo(file)
            }
        } catch (_: Exception) {
            // Log or ignore persistence failures
        }
    }

    /**
     * Создает устойчивый ключ пары, привязанный к дате, номеру пары и названию дисциплины.
     */
    fun buildKey(
        dateString: String,
        time: String,
        lessonNumber: String,
        subject: String
    ): String = Companion.buildKey(dateString, time, lessonNumber, subject)

    /**
     * Загружает все доступные кэшированные недели из дискового кэша, объединяет с [currentSchedule],
     * динамически рассчитывает строгое смещение [offsetWeeks] относительно текущего понедельника
     * и упорядочивает их по дате начала (хронологически).
     */
    fun loadAllCachedWeeks(currentSchedule: WeekSchedule?): List<WeekSchedule> {
        val weeks = mutableMapOf<String, WeekSchedule>()

        if (currentSchedule != null && currentSchedule.startDate.isNotEmpty()) {
            weeks[currentSchedule.startDate] = currentSchedule
        }

        try {
            val cacheFiles = context.cacheDir.listFiles { file ->
                file.name.startsWith("schedule_") && file.name.endsWith(".json")
            } ?: emptyArray()

            for (file in cacheFiles) {
                try {
                    val json = file.readText()
                    val schedule = gson.fromJson(json, WeekSchedule::class.java)
                    if (schedule != null && schedule.startDate.isNotEmpty() && !weeks.containsKey(schedule.startDate)) {
                        weeks[schedule.startDate] = schedule
                    }
                } catch (_: Exception) {
                    // Ignore corrupt cache entry
                }
            }
        } catch (_: Exception) {
            // Ignore filesystem error
        }

        val dFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault())
        val moscowZone = try {
            java.time.ZoneId.of("Europe/Moscow")
        } catch (_: Exception) {
            java.time.ZoneId.systemDefault()
        }
        val currentMonday = LocalDate.now(moscowZone).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

        return weeks.values
            .map { schedule ->
                val weekMonday = try {
                    LocalDate.parse(schedule.startDate, dFmt)
                } catch (_: Exception) {
                    null
                }
                val realOffset = if (weekMonday != null) {
                    java.time.temporal.ChronoUnit.WEEKS.between(currentMonday, weekMonday).toInt()
                } else {
                    schedule.offsetWeeks
                }
                schedule.copy(offsetWeeks = realOffset)
            }
            .sortedBy { schedule ->
                try {
                    LocalDate.parse(schedule.startDate, dFmt)
                } catch (_: Exception) {
                    LocalDate.MIN
                }
            }
    }

    /**
     * Вычисляет точный линейный индекс карточки-разделителя целевой недели в LazyColumn
     * с учетом текущего состояния свернутых/развернутых недель (аккордеона).
     */
    fun calculateWeekItemIndex(
        targetStartDate: String?,
        allWeeks: List<WeekSchedule>,
        expandedWeeks: Set<String>
    ): Int = Companion.calculateWeekItemIndex(targetStartDate, allWeeks, expandedWeeks)

    companion object {
        @Volatile
        private var instance: LessonNotesRepository? = null

        fun getInstance(context: Context): LessonNotesRepository {
            return instance ?: synchronized(this) {
                instance ?: LessonNotesRepository(context.applicationContext).also { instance = it }
            }
        }

        fun computeRelativeWeekOffset(
            weekStartDate: String,
            currentMonday: LocalDate
        ): Int {
            val dFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault())
            val weekMonday = try {
                LocalDate.parse(weekStartDate, dFmt)
            } catch (_: Exception) {
                return 0
            }
            return java.time.temporal.ChronoUnit.WEEKS.between(currentMonday, weekMonday).toInt()
        }

        fun calculateWeekItemIndex(
            targetStartDate: String?,
            allWeeks: List<WeekSchedule>,
            expandedWeeks: Set<String>
        ): Int {
            if (targetStartDate.isNullOrEmpty() || allWeeks.isEmpty()) return 0
            var currentIndex = 0
            for (week in allWeeks) {
                if (week.startDate == targetStartDate) {
                    return currentIndex
                }
                currentIndex++ // WeekDividerCard
                if (week.startDate in expandedWeeks) {
                    for (day in week.days) {
                        if (day.lessons.isNotEmpty()) {
                            currentIndex++ // DayHeaderItem
                            currentIndex += day.lessons.size // WeeklyLessonNoteCard items
                        }
                    }
                }
            }
            return 0
        }

        fun buildKey(
            dateString: String,
            time: String,
            lessonNumber: String,
            subject: String
        ): String {
            val cleanDate = dateString.trim().replace('/', '.').replace('-', '.')
            val cleanNum = lessonNumber.trim().ifEmpty { "0" }
            val cleanTime = time.trim().replace(' ', '_')
            val cleanSubj = subject.trim().lowercase(Locale.ROOT).replace(Regex("""[^a-zA-Zа-яА-Я0-9]"""), "_")
            return "${cleanDate}_${cleanNum}_${cleanTime}_${cleanSubj.take(40)}"
        }
    }
}
