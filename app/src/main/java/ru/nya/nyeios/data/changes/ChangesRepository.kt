package ru.nya.nyeios.data.changes

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nya.nyeios.data.model.CurriculumTerm
import ru.nya.nyeios.data.model.WeekSchedule

/**
 * Фасад подсистемы «что нового»: принимает успешные загрузки расписания/БРС,
 * хранит baseline/latest/changes в [ChangesStore] и транслирует изменения в [StateFlow].
 *
 * Семантика накопительная: baseline меняется только через mark*Seen, поэтому серия
 * обновлений 5 -> 6 -> 7 -> 8 всегда даёт один diff 5 -> 8.
 *
 * Все публичные операции синхронизированы: воркер автообновления и UI могут обращаться
 * одновременно (гонка между ними иначе теряет/дублирует изменения).
 *
 * @param store инъецируемое хранилище; в проде создаётся через [getInstance],
 *   в JVM-тестах — через [forTesting].
 */
class ChangesRepository internal constructor(private val store: ChangesStore) {

    private val lock = Any()

    private val _scheduleChanges = MutableStateFlow(store.loadScheduleChanges())
    val scheduleChanges: StateFlow<ScheduleChanges> = _scheduleChanges.asStateFlow()

    private val _curriculumChanges = MutableStateFlow(store.loadCurriculumChanges())
    val curriculumChanges: StateFlow<CurriculumChanges> = _curriculumChanges.asStateFlow()

    /**
     * Вызывать после КАЖДОЙ успешной сетевой/кэш-загрузки недели расписания.
     * Обновляет latest и diff только пришедшей недели, не затирая другие недели.
     */
    fun onScheduleFetched(schedule: WeekSchedule) {
        synchronized(lock) {
            val fetched = ChangesDiff.scheduleSnapshot(schedule)
            val weekKey = ScheduleChanges.weekKey(schedule.startDate, schedule.endDate)
            val fetchedWeek = fetched.weeks[weekKey].orEmpty()

            val storedLatest = store.loadScheduleLatest() ?: ChangesDiff.ScheduleSnapshot()
            val mergedLatest = ChangesDiff.ScheduleSnapshot(
                storedLatest.weeks + (weekKey to fetchedWeek)
            )
            store.saveScheduleLatest(mergedLatest)

            val baselineBefore = store.loadScheduleBaseline()
            val baselineWeek: List<ChangesDiff.LessonSnapshot>? = when {
                baselineBefore == null -> {
                    // Первая загрузка: молча фиксируем baseline, изменений нет.
                    store.saveScheduleBaseline(mergedLatest)
                    null
                }

                baselineBefore.weeks.containsKey(weekKey) -> baselineBefore.weeks[weekKey]

                else -> {
                    // Пользователь эту неделю ещё не видел — тихо пополняем baseline.
                    store.saveScheduleBaseline(
                        ChangesDiff.ScheduleSnapshot(
                            baselineBefore.weeks + (weekKey to fetchedWeek)
                        )
                    )
                    null
                }
            }

            val currentChanges = store.loadScheduleChanges()
            val weekChanges = currentChanges.weeks.toMutableMap()
            if (baselineWeek == null) {
                weekChanges.remove(weekKey)
            } else {
                val computed = ChangesDiff.scheduleChanges(
                    ChangesDiff.ScheduleSnapshot(mapOf(weekKey to baselineWeek)),
                    ChangesDiff.ScheduleSnapshot(mapOf(weekKey to fetchedWeek))
                )[weekKey]
                if (computed == null || !computed.hasChanges) {
                    weekChanges.remove(weekKey)
                } else {
                    weekChanges[weekKey] = computed
                }
            }

            val updated = ScheduleChanges(weekChanges)
            store.saveScheduleChanges(updated)
            _scheduleChanges.value = updated
        }
    }

    /** Вызывать после КАЖДОЙ успешной загрузки БРС. */
    fun onCurriculumFetched(terms: List<CurriculumTerm>) {
        synchronized(lock) {
            val fetched = ChangesDiff.curriculumSnapshot(terms)
            val fetchedTermNums = terms.mapTo(LinkedHashSet()) { it.termNum }
            val fetchedByTerm = fetched.subjects.groupBy { it.termNum }

            val storedLatest = store.loadCurriculumLatest() ?: ChangesDiff.CurriculumSnapshot()
            val mergedLatest = ChangesDiff.CurriculumSnapshot(
                storedLatest.subjects.filter { it.termNum !in fetchedTermNums } + fetched.subjects
            )
            store.saveCurriculumLatest(mergedLatest)

            val baselineBefore = store.loadCurriculumBaseline()
            val effectiveBaseline: ChangesDiff.CurriculumSnapshot = when {
                baselineBefore == null -> {
                    // Первая загрузка: молча фиксируем baseline, изменений нет.
                    store.saveCurriculumBaseline(mergedLatest)
                    mergedLatest
                }

                else -> {
                    val knownTerms = baselineBefore.subjects.mapTo(HashSet()) { it.termNum }
                    val unseenSubjects = fetchedTermNums
                        .filter { it !in knownTerms }
                        .flatMap { fetchedByTerm[it].orEmpty() }
                    if (unseenSubjects.isEmpty()) {
                        baselineBefore
                    } else {
                        // Семестры, которых пользователь ещё не видел, тихо попадают в baseline.
                        ChangesDiff.CurriculumSnapshot(
                            baselineBefore.subjects + unseenSubjects
                        ).also { store.saveCurriculumBaseline(it) }
                    }
                }
            }

            val currentChanges = store.loadCurriculumChanges()
            val termChanges = currentChanges.terms.toMutableMap()
            for (termNum in fetchedTermNums) {
                val baselineSubjects = effectiveBaseline.subjects.filter { it.termNum == termNum }
                if (baselineSubjects.isEmpty()) {
                    // Семестр отсутствует в baseline — молчаливое заполнение без изменений.
                    termChanges.remove(termNum)
                    continue
                }
                val computed = ChangesDiff.curriculumChanges(
                    ChangesDiff.CurriculumSnapshot(baselineSubjects),
                    ChangesDiff.CurriculumSnapshot(fetchedByTerm[termNum].orEmpty())
                )[termNum]
                if (computed == null || !computed.hasChanges) {
                    termChanges.remove(termNum)
                } else {
                    termChanges[termNum] = computed
                }
            }

            val updated = CurriculumChanges(termChanges)
            store.saveCurriculumChanges(updated)
            _curriculumChanges.value = updated
        }
    }

    /** Пользователь посмотрел неделю: baseline[week] = latest[week], changes[week] удаляются. */
    fun markWeekSeen(startDate: String, endDate: String) {
        synchronized(lock) {
            val weekKey = ScheduleChanges.weekKey(startDate, endDate)

            val latestWeek = store.loadScheduleLatest()?.weeks?.get(weekKey)
            if (latestWeek != null) {
                val baseline = store.loadScheduleBaseline() ?: ChangesDiff.ScheduleSnapshot()
                store.saveScheduleBaseline(
                    ChangesDiff.ScheduleSnapshot(baseline.weeks + (weekKey to latestWeek))
                )
            }

            val current = store.loadScheduleChanges()
            if (current.weeks.containsKey(weekKey)) {
                val updated = ScheduleChanges(current.weeks - weekKey)
                store.saveScheduleChanges(updated)
                _scheduleChanges.value = updated
            }
        }
    }

    /** Пользователь посмотрел семестр БРС: baseline[term] = latest[term], changes[term] удаляются. */
    fun markTermSeen(termNum: Int) {
        synchronized(lock) {
            val latestSubjects = store.loadCurriculumLatest()
                ?.subjects
                ?.filter { it.termNum == termNum }
                .orEmpty()
            if (latestSubjects.isNotEmpty()) {
                val baseline = store.loadCurriculumBaseline() ?: ChangesDiff.CurriculumSnapshot()
                store.saveCurriculumBaseline(
                    ChangesDiff.CurriculumSnapshot(
                        baseline.subjects.filter { it.termNum != termNum } + latestSubjects
                    )
                )
            }

            val current = store.loadCurriculumChanges()
            if (current.terms.containsKey(termNum)) {
                val updated = CurriculumChanges(current.terms - termNum)
                store.saveCurriculumChanges(updated)
                _curriculumChanges.value = updated
            }
        }
    }

    /** Пометить всё расписание просмотренным (например, ON_STOP). */
    fun markAllScheduleSeen() {
        synchronized(lock) {
            val latest = store.loadScheduleLatest()
            if (latest != null) store.saveScheduleBaseline(latest)
            store.saveScheduleChanges(ScheduleChanges())
            _scheduleChanges.value = ScheduleChanges()
        }
    }

    /** Пометить всю успеваемость просмотренной. */
    fun markAllCurriculumSeen() {
        synchronized(lock) {
            val latest = store.loadCurriculumLatest()
            if (latest != null) store.saveCurriculumBaseline(latest)
            store.saveCurriculumChanges(CurriculumChanges())
            _curriculumChanges.value = CurriculumChanges()
        }
    }

    /** Выход из аккаунта: стереть всё. */
    fun reset() {
        synchronized(lock) {
            store.clearAll()
            _scheduleChanges.value = ScheduleChanges()
            _curriculumChanges.value = CurriculumChanges()
        }
    }

    companion object {
        @Volatile
        private var instance: ChangesRepository? = null

        /** Потокобезопасный синглтон с applicationContext. */
        fun getInstance(context: Context): ChangesRepository {
            return instance ?: synchronized(this) {
                instance ?: ChangesRepository(
                    ChangesStore(SharedPrefsChangePrefs(context.applicationContext))
                ).also { instance = it }
            }
        }

        /** Для JVM-тестов: репозиторий на инъецированном сторе (без Android-контекста). */
        fun forTesting(store: ChangesStore): ChangesRepository = ChangesRepository(store)
    }
}
