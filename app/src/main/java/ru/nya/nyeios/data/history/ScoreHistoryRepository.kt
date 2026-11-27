package ru.nya.nyeios.data.history

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nya.nyeios.data.changes.ScoreField
import ru.nya.nyeios.data.model.CurriculumSubject
import ru.nya.nyeios.data.model.CurriculumTerm
import java.time.Instant
import java.time.ZoneId

/**
 * Фасад истории баллов БРС: пишет снимки с каждой загрузки БРС, отдаёт данные
 * графикам и экспорт/импорту. Все операции синхронизированы (IO-потоки ViewModel/воркера).
 *
 * @param dao инъецируемое хранилище: SQLite в проде ([getInstance]),
 *   in-memory в JVM-тестах ([forTesting]).
 * @param clock источник времени — фиксируется в тестах для проверки ежедневных точек.
 */
class ScoreHistoryRepository private constructor(
    private val dao: ScoreHistoryDao,
    private val clock: () -> Long
) {

    private val lock = Any()

    private val _revision = MutableStateFlow(0)

    /** Растёт при каждом изменении данных; UI перерисовывает графики по этому сигналу. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Volatile
    private var lastRememberedGroup: String? = null

    /** Запомнить текущую группу (из расписания) — используется при записи. */
    fun rememberGroup(group: String?) {
        lastRememberedGroup = group?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Вызывается при КАЖДОЙ загрузке БРС (сеть или кэш).
     *
     * Точка серии пишется, если:
     * 1) серии ещё нет — но только если в снимке есть реальный числовой балл
     *    ([ScoreHistoryValues.primaryValue] != null), иначе стартовый график был бы пустым;
     * 2) снимок отличается от последней точки серии;
     * 3) последняя точка сделана раньше начала текущих календарных суток (ежедневное «закрытие»).
     *
     * Пустой/нулевой снимок при уже существующей серии не пишется (график не забивается нулями).
     * `revision` инкрементится один раз за вызов при наличии вставок.
     */
    fun onCurriculumFetched(terms: List<CurriculumTerm>, group: String? = null) {
        synchronized(lock) {
            val effectiveGroup = group ?: lastRememberedGroup ?: ""
            val recordedAt = clock()
            val dayStart = startOfCurrentDay(recordedAt)
            var inserted = 0

            for (term in terms) {
                for (subject in term.subjects) {
                    val key = ScoreSeriesKey(
                        group = effectiveGroup,
                        termNum = term.termNum,
                        subject = subject.subject,
                        controlType = subject.controlType
                    )
                    val scores = snapshot(subject)
                    val primary = ScoreHistoryValues.primaryValue(scores)
                    val latest = dao.latestPoint(key)

                    val shouldWrite = when {
                        latest == null -> primary != null
                        primary == null -> false
                        latest.scores != scores -> true
                        latest.recordedAtMs < dayStart -> true
                        else -> false
                    }
                    if (!shouldWrite) continue

                    inserted += dao.insert(
                        listOf(
                            ScoreHistoryPoint(
                                recordedAtMs = recordedAt,
                                group = effectiveGroup,
                                termNum = term.termNum,
                                subject = subject.subject,
                                controlType = subject.controlType,
                                scores = scores
                            )
                        )
                    )
                }
            }

            if (inserted > 0) _revision.value += 1
        }
    }

    /** Экспорт: текущая группа + все точки. */
    fun exportPayload(exportedAtMs: Long = clock()): ScoreHistoryCodec.ExportPayload =
        synchronized(lock) {
            val points = dao.queryAll()
            // remembered-группа пуста до первой загрузки расписания (или на свежей установке) —
            // тогда берём единственную группу из самих точек, чтобы конверт и имя файла
            // не оставались без привязки к группе.
            val group = lastRememberedGroup
                ?: points.map { it.group }.filter { it.isNotBlank() }.distinct().singleOrNull()
                ?: ""
            ScoreHistoryCodec.ExportPayload(
                schema = ScoreHistoryCodec.SCHEMA_VERSION,
                app = ScoreHistoryCodec.APP_NAME,
                exportedAtMs = exportedAtMs,
                group = group,
                points = points
            )
        }

    fun exportJson(): String = ScoreHistoryCodec.encode(exportPayload())

    /** Импорт: объединение с дедупликацией. Битый/чужой JSON -> `invalid = 1`. */
    fun importJson(json: String): HistoryImportResult {
        synchronized(lock) {
            val payload = ScoreHistoryCodec.decode(json)
                ?: return HistoryImportResult(added = 0, duplicates = 0, invalid = 1)

            val envelopeGroup = payload.group.trim()
            val valid = ArrayList<ScoreHistoryPoint>(payload.points.size)
            var invalid = 0
            for (point in payload.points) {
                val normalized = point.copy(
                    group = point.group.trim().ifBlank { envelopeGroup }
                )
                if (normalized.recordedAtMs <= 0L ||
                    normalized.termNum <= 0 ||
                    normalized.subject.isBlank()
                ) {
                    invalid++
                } else {
                    valid += normalized
                }
            }

            val inserted = dao.insert(valid)
            if (inserted > 0) _revision.value += 1
            return HistoryImportResult(
                added = inserted,
                duplicates = valid.size - inserted,
                invalid = invalid
            )
        }
    }

    /** Для UI: все серии (отсортированы по группе/семестру/предмету). */
    fun seriesKeys(): List<ScoreSeriesKey> = seriesKeys(allGroups = true)

    /** Аддитивное расширение: все серии или только серии запомненной группы. */
    fun seriesKeys(allGroups: Boolean): List<ScoreSeriesKey> = synchronized(lock) {
        val all = dao.distinctSeriesKeys()
        if (allGroups) {
            all
        } else {
            val remembered = lastRememberedGroup ?: return@synchronized all
            all.filter { it.group == remembered }
        }
    }

    fun pointsFor(key: ScoreSeriesKey): List<ScoreHistoryPoint> = synchronized(lock) {
        dao.querySeries(key)
    }

    fun stats(): HistoryStats = synchronized(lock) { dao.stats() }

    fun clear() {
        synchronized(lock) {
            dao.clear()
            _revision.value += 1
        }
    }

    // --- internal ------------------------------------------------------------

    /** Снимок всех полей БРС — ровно как в `ChangesDiff.curriculumSnapshot`. */
    private fun snapshot(subject: CurriculumSubject): Map<String, String> {
        val scores = LinkedHashMap<String, String>(ScoreField.entries.size)
        for (field in ScoreField.entries) {
            scores[field.rawName] = when (field) {
                ScoreField.CURRENT_SCORE -> subject.currentScore
                ScoreField.CURRENT_RATING -> subject.currentRating
                ScoreField.TERM_RATING -> subject.termRating
                ScoreField.EXAM_SCORE -> subject.examScore
                ScoreField.FINAL_RATING -> subject.finalRating
                ScoreField.GRADE -> subject.grade
            }
        }
        return scores
    }

    /** Начало текущих календарных суток в зоне устройства. */
    private fun startOfCurrentDay(epochMs: Long): Long {
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(epochMs)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }

    companion object {
        @Volatile
        private var instance: ScoreHistoryRepository? = null

        /** Потокобезопасный синглтон с applicationContext. */
        fun getInstance(context: Context): ScoreHistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: ScoreHistoryRepository(
                    SqliteScoreHistoryDao(context.applicationContext),
                    System::currentTimeMillis
                ).also { instance = it }
            }
        }

        /** Для JVM-тестов: репозиторий на инъецированном DAO и часах. */
        fun forTesting(dao: ScoreHistoryDao, clock: () -> Long): ScoreHistoryRepository =
            ScoreHistoryRepository(dao, clock)
    }
}
