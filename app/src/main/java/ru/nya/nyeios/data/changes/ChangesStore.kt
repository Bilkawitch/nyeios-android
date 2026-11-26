package ru.nya.nyeios.data.changes

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Мини-абстракция, чтобы стор тестировался на JVM без Robolectric. */
interface ChangePrefs {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

/** Продакшен-реализация [ChangePrefs] поверх `SharedPreferences("nyeios_changes")`. */
class SharedPrefsChangePrefs(context: Context) : ChangePrefs {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        const val PREFS_NAME = "nyeios_changes"
    }
}

/**
 * Персистентность «что нового»: baseline/latest/changes расписания и БРС (JSON через Gson).
 *
 * Намеренно зависит только от [ChangePrefs], поэтому полностью покрывается JVM-тестами.
 * Любые битые/несовместимые данные трактуются как отсутствие: приложение не должно
 * падать из-за повреждённого кэша.
 */
class ChangesStore(private val prefs: ChangePrefs) {

    private val gson = Gson()

    fun loadScheduleBaseline(): ChangesDiff.ScheduleSnapshot? =
        read(KEY_SCHEDULE_BASELINE, ::isValidScheduleSnapshot)

    fun loadScheduleLatest(): ChangesDiff.ScheduleSnapshot? =
        read(KEY_SCHEDULE_LATEST, ::isValidScheduleSnapshot)

    fun loadScheduleChanges(): ScheduleChanges =
        read(KEY_SCHEDULE_CHANGES, ::isValidScheduleChanges) ?: ScheduleChanges()

    fun loadCurriculumBaseline(): ChangesDiff.CurriculumSnapshot? =
        read(KEY_CURRICULUM_BASELINE, ::isValidCurriculumSnapshot)

    fun loadCurriculumLatest(): ChangesDiff.CurriculumSnapshot? =
        read(KEY_CURRICULUM_LATEST, ::isValidCurriculumSnapshot)

    fun loadCurriculumChanges(): CurriculumChanges =
        read(KEY_CURRICULUM_CHANGES, ::isValidCurriculumChanges) ?: CurriculumChanges()

    fun saveScheduleBaseline(s: ChangesDiff.ScheduleSnapshot) {
        write(KEY_SCHEDULE_BASELINE, gson.toJson(s))
    }

    fun saveScheduleLatest(s: ChangesDiff.ScheduleSnapshot) {
        write(KEY_SCHEDULE_LATEST, gson.toJson(s))
    }

    fun saveScheduleChanges(c: ScheduleChanges) {
        write(KEY_SCHEDULE_CHANGES, gson.toJson(c))
    }

    fun saveCurriculumBaseline(s: ChangesDiff.CurriculumSnapshot) {
        write(KEY_CURRICULUM_BASELINE, gson.toJson(s))
    }

    fun saveCurriculumLatest(s: ChangesDiff.CurriculumSnapshot) {
        write(KEY_CURRICULUM_LATEST, gson.toJson(s))
    }

    fun saveCurriculumChanges(c: CurriculumChanges) {
        write(KEY_CURRICULUM_CHANGES, gson.toJson(c))
    }

    fun clearAll() {
        ALL_KEYS.forEach(prefs::remove)
    }

    // --- low level -----------------------------------------------------------

    private inline fun <reified T> read(
        key: String,
        noinline validate: (JsonObject) -> Boolean
    ): T? {
        val obj = readObject(key, validate) ?: return null
        return try {
            gson.fromJson(obj, T::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun readObject(key: String, validate: (JsonObject) -> Boolean): JsonObject? {
        val raw = prefs.getString(key) ?: return null
        if (raw.isBlank()) return null
        return try {
            val element = JsonParser.parseString(raw)
            if (!element.isJsonObject) return null
            element.asJsonObject.takeIf(validate)
        } catch (_: Exception) {
            null
        }
    }

    private fun write(key: String, json: String) {
        prefs.putString(key, json)
    }

    // --- validation ----------------------------------------------------------

    private fun isValidScheduleSnapshot(obj: JsonObject): Boolean {
        val weeks = obj.get("weeks") ?: return false
        if (!weeks.isJsonObject) return false
        return weeks.asJsonObject.entrySet().all { (_, lessons) -> isValidLessonArray(lessons) }
    }

    private fun isValidScheduleChanges(obj: JsonObject): Boolean {
        val weeks = obj.get("weeks") ?: return false
        if (!weeks.isJsonObject) return false
        return weeks.asJsonObject.entrySet().all { (_, weekElement) ->
            if (!weekElement.isJsonObject) return@all false
            val week = weekElement.asJsonObject
            isString(week, "weekKey") &&
                isValidLessonArray(week.get("added")) &&
                isValidLessonArray(week.get("modified")) &&
                isValidLessonArray(week.get("removed"))
        }
    }

    private fun isValidCurriculumSnapshot(obj: JsonObject): Boolean {
        val subjects = obj.get("subjects") ?: return false
        if (!subjects.isJsonArray) return false
        return subjects.asJsonArray.all { element ->
            if (!element.isJsonObject) return@all false
            val subject = element.asJsonObject
            isNumber(subject, "termNum") &&
                isString(subject, "subject") &&
                isString(subject, "controlType") &&
                isValidStringMap(subject.get("scores"))
        }
    }

    private fun isValidCurriculumChanges(obj: JsonObject): Boolean {
        val terms = obj.get("terms") ?: return false
        if (!terms.isJsonObject) return false
        return terms.asJsonObject.entrySet().all { (_, termElement) ->
            if (!termElement.isJsonObject) return@all false
            val term = termElement.asJsonObject
            if (!isNumber(term, "termNum")) return@all false
            val scoreChanges = term.get("scoreChanges") ?: return@all false
            if (!scoreChanges.isJsonArray) return@all false
            scoreChanges.asJsonArray.all { changeElement ->
                if (!changeElement.isJsonObject) return@all false
                val change = changeElement.asJsonObject
                isNumber(change, "termNum") &&
                    isString(change, "subject") &&
                    isString(change, "controlType") &&
                    isString(change, "field") &&
                    isString(change, "oldValue") &&
                    isString(change, "newValue")
            }
        }
    }

    private fun isValidLessonArray(element: JsonElement?): Boolean {
        if (element == null || !element.isJsonArray) return false
        return element.asJsonArray.all { lessonElement ->
            lessonElement.isJsonObject && isValidLessonFields(lessonElement.asJsonObject)
        }
    }

    private fun isValidLessonFields(lesson: JsonObject): Boolean =
        isString(lesson, "weekKey") &&
            isString(lesson, "dateString") &&
            isString(lesson, "dayName") &&
            isString(lesson, "lessonNumber") &&
            isString(lesson, "subgroup") &&
            isString(lesson, "time") &&
            isString(lesson, "subject") &&
            isString(lesson, "room") &&
            isString(lesson, "teacher") &&
            isString(lesson, "type")

    private fun isValidStringMap(element: JsonElement?): Boolean {
        if (element == null || !element.isJsonObject) return false
        return element.asJsonObject.entrySet().all { (_, value) ->
            value.isJsonPrimitive && value.asJsonPrimitive.isString
        }
    }

    private fun isString(obj: JsonObject, name: String): Boolean {
        val value = obj.get(name) ?: return false
        return value.isJsonPrimitive && value.asJsonPrimitive.isString
    }

    private fun isNumber(obj: JsonObject, name: String): Boolean {
        val value = obj.get(name) ?: return false
        return value.isJsonPrimitive && value.asJsonPrimitive.isNumber
    }

    companion object {
        const val KEY_SCHEDULE_BASELINE = "schedule_baseline"
        const val KEY_SCHEDULE_LATEST = "schedule_latest"
        const val KEY_SCHEDULE_CHANGES = "schedule_changes"
        const val KEY_CURRICULUM_BASELINE = "curriculum_baseline"
        const val KEY_CURRICULUM_LATEST = "curriculum_latest"
        const val KEY_CURRICULUM_CHANGES = "curriculum_changes"

        private val ALL_KEYS = listOf(
            KEY_SCHEDULE_BASELINE,
            KEY_SCHEDULE_LATEST,
            KEY_SCHEDULE_CHANGES,
            KEY_CURRICULUM_BASELINE,
            KEY_CURRICULUM_LATEST,
            KEY_CURRICULUM_CHANGES
        )
    }
}
