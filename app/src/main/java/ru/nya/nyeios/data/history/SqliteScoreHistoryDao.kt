package ru.nya.nyeios.data.history

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * SQLite-хранилище истории баллов (`SQLiteOpenHelper`, WAL).
 *
 * Схема:
 * `score_points(id, group_name, term_num, subject, control_type, recorded_at, scores)`,
 * `scores` — JSON-объект (Gson), уникальный ключ — UNIQUE INDEX
 * `(group_name, term_num, subject, control_type, recorded_at)`.
 *
 * Все операции synchronized. Ошибки БД не пробрасываются наружу: вызывающий код (загрузка БРС,
 * UI, воркер) не должен падать из-за локальной истории.
 */
class SqliteScoreHistoryDao(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION),
    ScoreHistoryDao {

    private val gson = Gson()
    private val scoresType = object : TypeToken<Map<String, String?>>() {}.type

    private val lock = Any()

    init {
        // WAL: запись истории не блокирует чтение графиков. Если БД уже открыта в другом
        // режиме — режим просто не переключится, это не повод падать.
        runCatching { setWriteAheadLoggingEnabled(true) }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE)
        db.execSQL(CREATE_UNIQUE_INDEX)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // История — производные данные: при смене схемы пересоздаём таблицу.
        // При появлении реальной миграции сюда нужно добавить ALTER TABLE.
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    override fun insert(points: List<ScoreHistoryPoint>): Int {
        if (points.isEmpty()) return 0
        synchronized(lock) {
            return runCatching {
                val db = writableDatabase
                var inserted = 0
                db.beginTransaction()
                try {
                    for (point in points) {
                        val rowId = db.insertWithOnConflict(
                            TABLE,
                            null,
                            toValues(point),
                            SQLiteDatabase.CONFLICT_IGNORE
                        )
                        if (rowId != -1L) inserted++
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                inserted
            }.getOrDefault(0)
        }
    }

    override fun queryAll(): List<ScoreHistoryPoint> = synchronized(lock) {
        runCatching {
            readableDatabase.rawQuery(
                "SELECT $COLUMNS FROM $TABLE ORDER BY $COL_RECORDED_AT ASC, id ASC",
                null
            ).use { cursor -> readPoints(cursor) }
        }.getOrDefault(emptyList())
    }

    override fun querySeries(key: ScoreSeriesKey): List<ScoreHistoryPoint> = synchronized(lock) {
        runCatching {
            readableDatabase.rawQuery(
                "SELECT $COLUMNS FROM $TABLE WHERE $COL_GROUP = ? AND $COL_TERM = ? " +
                    "AND $COL_SUBJECT = ? AND $COL_CONTROL = ? " +
                    "ORDER BY $COL_RECORDED_AT ASC, id ASC",
                arrayOf(key.group, key.termNum.toString(), key.subject, key.controlType)
            ).use { cursor -> readPoints(cursor) }
        }.getOrDefault(emptyList())
    }

    override fun latestPoint(key: ScoreSeriesKey): ScoreHistoryPoint? = synchronized(lock) {
        runCatching {
            readableDatabase.rawQuery(
                "SELECT $COLUMNS FROM $TABLE WHERE $COL_GROUP = ? AND $COL_TERM = ? " +
                    "AND $COL_SUBJECT = ? AND $COL_CONTROL = ? " +
                    "ORDER BY $COL_RECORDED_AT DESC, id DESC LIMIT 1",
                arrayOf(key.group, key.termNum.toString(), key.subject, key.controlType)
            ).use { cursor -> readPoints(cursor).firstOrNull() }
        }.getOrNull()
    }

    override fun distinctSeriesKeys(): List<ScoreSeriesKey> = synchronized(lock) {
        runCatching {
            val result = ArrayList<ScoreSeriesKey>()
            readableDatabase.rawQuery(
                "SELECT $COL_GROUP, $COL_TERM, $COL_SUBJECT, $COL_CONTROL FROM $TABLE " +
                    "GROUP BY $COL_GROUP, $COL_TERM, $COL_SUBJECT, $COL_CONTROL " +
                    "ORDER BY $COL_GROUP ASC, $COL_TERM ASC, $COL_SUBJECT ASC, $COL_CONTROL ASC",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    result += ScoreSeriesKey(
                        group = cursor.getString(0).orEmpty(),
                        termNum = cursor.getInt(1),
                        subject = cursor.getString(2).orEmpty(),
                        controlType = cursor.getString(3).orEmpty()
                    )
                }
            }
            result
        }.getOrDefault(emptyList())
    }

    override fun count(): Int = synchronized(lock) {
        runCatching {
            readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }.getOrDefault(0)
    }

    override fun stats(): HistoryStats = synchronized(lock) {
        runCatching {
            var total = 0
            var first: Long? = null
            var last: Long? = null
            readableDatabase.rawQuery(
                "SELECT COUNT(*), MIN($COL_RECORDED_AT), MAX($COL_RECORDED_AT) FROM $TABLE",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    total = cursor.getInt(0)
                    if (!cursor.isNull(1)) first = cursor.getLong(1)
                    if (!cursor.isNull(2)) last = cursor.getLong(2)
                }
            }
            var subjects = 0
            readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM (SELECT 1 FROM $TABLE " +
                    "GROUP BY $COL_GROUP, $COL_TERM, $COL_SUBJECT, $COL_CONTROL)",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) subjects = cursor.getInt(0)
            }
            HistoryStats(
                totalPoints = total,
                subjectsCount = subjects,
                firstAtMs = first,
                lastAtMs = last
            )
        }.getOrDefault(HistoryStats(0, 0, null, null))
    }

    override fun clear() {
        synchronized(lock) {
            runCatching { writableDatabase.delete(TABLE, null, null) }
        }
    }

    // --- internal ------------------------------------------------------------

    private fun toValues(point: ScoreHistoryPoint): ContentValues = ContentValues().apply {
        put(COL_GROUP, point.group)
        put(COL_TERM, point.termNum)
        put(COL_SUBJECT, point.subject)
        put(COL_CONTROL, point.controlType)
        put(COL_RECORDED_AT, point.recordedAtMs)
        put(COL_SCORES, gson.toJson(point.scores))
    }

    private fun readPoints(cursor: Cursor): List<ScoreHistoryPoint> {
        val result = ArrayList<ScoreHistoryPoint>(cursor.count)
        val groupIndex = cursor.getColumnIndexOrThrow(COL_GROUP)
        val termIndex = cursor.getColumnIndexOrThrow(COL_TERM)
        val subjectIndex = cursor.getColumnIndexOrThrow(COL_SUBJECT)
        val controlIndex = cursor.getColumnIndexOrThrow(COL_CONTROL)
        val recordedIndex = cursor.getColumnIndexOrThrow(COL_RECORDED_AT)
        val scoresIndex = cursor.getColumnIndexOrThrow(COL_SCORES)
        while (cursor.moveToNext()) {
            result += ScoreHistoryPoint(
                recordedAtMs = cursor.getLong(recordedIndex),
                group = cursor.getString(groupIndex).orEmpty(),
                termNum = cursor.getInt(termIndex),
                subject = cursor.getString(subjectIndex).orEmpty(),
                controlType = cursor.getString(controlIndex).orEmpty(),
                scores = decodeScores(cursor.getString(scoresIndex))
            )
        }
        return result
    }

    /** Битый JSON в строке конкретной точки не должен ронять чтение истории. */
    private fun decodeScores(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val parsed: Map<String, String?>? = gson.fromJson(raw, scoresType)
            parsed.orEmpty().mapValues { it.value.orEmpty() }
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val DB_NAME = "nyeios_score_history.db"
        const val DB_VERSION = 1

        const val TABLE = "score_points"
        const val COL_GROUP = "group_name"
        const val COL_TERM = "term_num"
        const val COL_SUBJECT = "subject"
        const val COL_CONTROL = "control_type"
        const val COL_RECORDED_AT = "recorded_at"
        const val COL_SCORES = "scores"

        const val COLUMNS =
            "$COL_GROUP, $COL_TERM, $COL_SUBJECT, $COL_CONTROL, $COL_RECORDED_AT, $COL_SCORES"

        const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_GROUP TEXT NOT NULL DEFAULT '',
                $COL_TERM INTEGER NOT NULL,
                $COL_SUBJECT TEXT NOT NULL,
                $COL_CONTROL TEXT NOT NULL,
                $COL_RECORDED_AT INTEGER NOT NULL,
                $COL_SCORES TEXT NOT NULL
            )
        """

        const val CREATE_UNIQUE_INDEX = """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_score_points_unique
            ON $TABLE($COL_GROUP, $COL_TERM, $COL_SUBJECT, $COL_CONTROL, $COL_RECORDED_AT)
        """
    }
}
