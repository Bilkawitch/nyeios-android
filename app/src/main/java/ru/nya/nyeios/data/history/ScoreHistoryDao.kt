package ru.nya.nyeios.data.history

/**
 * Хранилище точек истории баллов БРС.
 *
 * Ключ точки: (group, termNum, subject, controlType, recordedAtMs).
 * Реализации обязаны пропускать дубликаты при вставке.
 */
interface ScoreHistoryDao {

    /** Вставляет точки, пропуская дубликаты (ключ ниже). Возвращает число вставленных. */
    fun insert(points: List<ScoreHistoryPoint>): Int

    /** Все точки (для экспорта/графиков), отсортированы по recordedAtMs. */
    fun queryAll(): List<ScoreHistoryPoint>

    /** Точки одной серии, отсортированы по recordedAtMs. */
    fun querySeries(key: ScoreSeriesKey): List<ScoreHistoryPoint>

    /** Последняя точка серии (для дедупликации записи). */
    fun latestPoint(key: ScoreSeriesKey): ScoreHistoryPoint?

    /** Уникальные серии (group, term, subject, controlType). */
    fun distinctSeriesKeys(): List<ScoreSeriesKey>

    fun count(): Int

    fun stats(): HistoryStats

    fun clear()
}

/** Уникальный ключ точки — общий для всех реализаций DAO. */
internal fun ScoreHistoryPoint.uniqueKey(): List<Any> = listOf(
    group,
    termNum,
    subject,
    controlType,
    recordedAtMs
)

/**
 * Реализация для JVM-тестов и preview: данные в памяти, дубликаты по тому же
 * уникальному ключу, что и у SQLite (UNIQUE INDEX).
 */
class InMemoryScoreHistoryDao : ScoreHistoryDao {

    private val lock = Any()
    private val points = ArrayList<ScoreHistoryPoint>()

    override fun insert(points: List<ScoreHistoryPoint>): Int = synchronized(lock) {
        var inserted = 0
        for (point in points) {
            if (this.points.any { it.uniqueKey() == point.uniqueKey() }) continue
            this.points += point
            inserted++
        }
        inserted
    }

    override fun queryAll(): List<ScoreHistoryPoint> = synchronized(lock) {
        points.sortedWith(compareBy({ it.recordedAtMs }, { it.uniqueKey().toString() }))
    }

    override fun querySeries(key: ScoreSeriesKey): List<ScoreHistoryPoint> = synchronized(lock) {
        points.filter { it.matches(key) }.sortedBy { it.recordedAtMs }
    }

    override fun latestPoint(key: ScoreSeriesKey): ScoreHistoryPoint? = synchronized(lock) {
        points.filter { it.matches(key) }.maxByOrNull { it.recordedAtMs }
    }

    override fun distinctSeriesKeys(): List<ScoreSeriesKey> = synchronized(lock) {
        points.map { it.seriesKey() }.distinct().sortedWith(SERIES_ORDER)
    }

    override fun count(): Int = synchronized(lock) { points.size }

    override fun stats(): HistoryStats = synchronized(lock) {
        HistoryStats(
            totalPoints = points.size,
            subjectsCount = points.map { it.seriesKey() }.distinct().size,
            firstAtMs = points.minOfOrNull { it.recordedAtMs },
            lastAtMs = points.maxOfOrNull { it.recordedAtMs }
        )
    }

    override fun clear() {
        synchronized(lock) { points.clear() }
    }

    private fun ScoreHistoryPoint.matches(key: ScoreSeriesKey): Boolean =
        group == key.group &&
            termNum == key.termNum &&
            subject == key.subject &&
            controlType == key.controlType

    private fun ScoreHistoryPoint.seriesKey(): ScoreSeriesKey = ScoreSeriesKey(
        group = group,
        termNum = termNum,
        subject = subject,
        controlType = controlType
    )

    private companion object {
        val SERIES_ORDER: Comparator<ScoreSeriesKey> = compareBy(
            { it.group },
            { it.termNum },
            { it.subject },
            { it.controlType }
        )
    }
}
