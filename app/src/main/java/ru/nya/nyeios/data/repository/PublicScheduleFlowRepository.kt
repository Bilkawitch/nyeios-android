package ru.nya.nyeios.data.repository

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.nya.nyeios.data.AppLocale
import ru.nya.nyeios.data.model.FlowGroupMatcher
import ru.nya.nyeios.data.model.FlowWeekState
import ru.nya.nyeios.data.model.PublicGroupInfo
import ru.nya.nyeios.data.model.PublicLesson
import ru.nya.nyeios.data.model.PublicLessonKey
import ru.nya.nyeios.data.net.NetworkLogger
import ru.nya.nyeios.data.parser.PublicScheduleParser
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Тихая фоновая сверка недельного расписания с расписанием соседних групп с публичного
 * сайта вуза, чтобы отличить потоковую пару от пары для одной группы.
 *
 * Правила:
 * - основное расписание (ЭИОС) уже показано пользователю, сверка идёт строго после него;
 * - запросы к сайту идут строго последовательно: сайт отбрасывает параллельные запросы;
 * - результат применяется по мере поступления данных (по одной группе) и кэшируется;
 * - ошибки сети не показываются пользователю.
 */
class PublicScheduleFlowRepository private constructor(private val context: Context) {

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val httpClient: OkHttpClient by lazy {
        // Портал отдаёт неполную цепочку сертификата (Windows/браузер дотягивают её сами),
        // поэтому используем ту же схему, что и основной клиент ЭИОС.
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
            object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
        )
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        OkHttpClient.Builder()
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", EiosRepository.BROWSER_USER_AGENT)
                        .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                        .build()
                )
            }
            .build()
    }

    private val _weekStates = MutableStateFlow<Map<String, FlowWeekState>>(emptyMap())
    val weekStates: StateFlow<Map<String, FlowWeekState>> = _weekStates.asStateFlow()

    /** Последняя запрошенная неделя: сверка устаревшей недели прекращается. */
    @Volatile
    private var latestWeekKey: String? = null

    fun requestWeek(
        ownGroup: String?,
        weekStart: String,
        weekEnd: String,
        forceRefresh: Boolean = false
    ) {
        val group = ownGroup?.trim().orEmpty()
        if (group.isEmpty() || weekStart.isBlank() || weekEnd.isBlank()) return
        val weekKey = "${weekStart}_$weekEnd"
        latestWeekKey = weekKey
        scope.launch {
            try {
                mutex.withLock {
                    if (latestWeekKey != weekKey) return@withLock
                    resolveWeek(group, weekStart, weekEnd, weekKey, forceRefresh)
                }
            } catch (_: Exception) {
                // Тихая сверка: любые сбои не должны влиять на основной экран.
            }
        }
    }

    private suspend fun resolveWeek(
        ownGroupName: String,
        weekStart: String,
        weekEnd: String,
        weekKey: String,
        forceRefresh: Boolean
    ) {
        val cachedWeek = if (forceRefresh) null else loadWeekCache(weekKey)
        if (cachedWeek != null && cachedWeek.isComplete && cachedWeek.isFresh(WEEK_CACHE_TTL_MS)) {
            emit(stateOf(weekKey, cachedWeek.lessonsByGroup, complete = true, expected = cachedWeek.expectedGroups))
            return
        }

        val groupsCache = loadGroupsCache()
        val groups = when {
            groupsCache != null && groupsCache.isFresh(GROUPS_CACHE_TTL_MS) -> groupsCache.groups
            else -> fetchGroups()?.also { saveGroupsCache(it) } ?: groupsCache?.groups
        }
        if (groups.isNullOrEmpty()) return

        val ownGroup = findOwnGroup(groups, ownGroupName) ?: return
        val siblings = FlowGroupMatcher.siblings(ownGroup, groups)
        if (siblings.isEmpty()) {
            emit(FlowWeekState(weekKey = weekKey, isComplete = true))
            saveWeekCache(weekKey, emptyMap(), complete = true, expectedGroups = 0)
            return
        }

        val reusable = if (!forceRefresh && cachedWeek != null && cachedWeek.isFresh(WEEK_CACHE_TTL_MS)) {
            cachedWeek.lessonsByGroup.filterKeys { name -> siblings.any { it.name == name } }
        } else {
            emptyMap()
        }
        val lessonsByGroup = reusable.toMutableMap()
        var fetched = lessonsByGroup.size

        emit(stateOf(weekKey, lessonsByGroup, complete = false, expected = siblings.size, fetched = fetched))
        Log.info("FLOW", AppLocale.pick(
            "Тихая сверка потока $weekKey: групп ${siblings.size}",
            "Quiet stream check $weekKey: ${siblings.size} groups"
        ))

        for (sibling in siblings) {
            if (latestWeekKey != weekKey) return
            if (lessonsByGroup.containsKey(sibling.name)) continue

            val lessons = fetchWeek(sibling, weekStart, weekEnd)
            if (lessons != null) {
                lessonsByGroup[sibling.name] = lessons
                fetched += 1
                saveWeekCache(
                    weekKey,
                    lessonsByGroup.toMap(),
                    complete = false,
                    expectedGroups = siblings.size
                )
            } else {
                Log.info("FLOW", AppLocale.pick(
                    "Сверка: не удалось получить ${sibling.name}",
                    "Flow check: failed to fetch ${sibling.name}"
                ))
            }

            emit(stateOf(weekKey, lessonsByGroup, complete = false, expected = siblings.size, fetched = fetched))
            delay(REQUEST_GAP_MS)
        }

        val complete = siblings.all { lessonsByGroup.containsKey(it.name) }
        emit(stateOf(weekKey, lessonsByGroup, complete = complete, expected = siblings.size, fetched = fetched))
        saveWeekCache(weekKey, lessonsByGroup.toMap(), complete = complete, expectedGroups = siblings.size)
        Log.info("FLOW", AppLocale.pick(
            "Тихая сверка потока $weekKey завершена (${lessonsByGroup.size}/${siblings.size})",
            "Quiet stream check $weekKey finished (${lessonsByGroup.size}/${siblings.size})"
        ))
        cleanupOldWeekCaches(keep = 8)
    }

    private fun stateOf(
        weekKey: String,
        lessonsByGroup: Map<String, List<PublicLesson>>,
        complete: Boolean,
        expected: Int = 0,
        fetched: Int = lessonsByGroup.size.coerceAtMost(expected)
    ): FlowWeekState {
        val keys = lessonsByGroup.values
            .asSequence()
            .flatten()
            .map { PublicLessonKey.of(it.dayIndex, it.time, it.subject, it.room) }
            .toSet()
        return FlowWeekState(
            weekKey = weekKey,
            streamLessonKeys = keys,
            isComplete = complete,
            fetchedGroups = fetched,
            expectedGroups = expected
        )
    }

    private fun emit(state: FlowWeekState) {
        _weekStates.update { it + (state.weekKey to state) }
    }

    private fun fetchGroups(): List<PublicGroupInfo>? = runCatching {
        val request = Request.Builder().url(PUBLIC_SCHEDULE_URL).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            PublicScheduleParser.parseGroups(response.body?.string().orEmpty()).ifEmpty { null }
        }
    }.getOrNull()

    private fun fetchWeek(
        group: PublicGroupInfo,
        weekStart: String,
        weekEnd: String
    ): List<PublicLesson>? = runCatching {
        val formBody = FormBody.Builder()
            .add("ScheduleFilterFaculty", group.facultyUid)
            .add("ScheduleFilterEduform", group.eduFormUid)
            .add("ScheduleFilterAcademicGroup", group.compoundKey)
            .add("ScheduleFilterPeriod", "${weekStart}_$weekEnd")
            .add("set_filter", "Показать")
            .build()
        val request = Request.Builder().url(PUBLIC_SCHEDULE_URL).post(formBody).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            PublicScheduleParser.parseWeek(response.body?.string().orEmpty())
        }
    }.getOrNull()

    private fun findOwnGroup(groups: List<PublicGroupInfo>, groupName: String): PublicGroupInfo? {
        val target = canonicalGroupName(groupName)
        if (target.isEmpty()) return null
        return groups.firstOrNull { canonicalGroupName(it.name) == target }
    }

    private fun canonicalGroupName(value: String): String =
        value.trim().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    // ---------- кэш ----------

    private data class GroupsCache(
        val savedAt: Long = 0L,
        val groups: List<PublicGroupInfo> = emptyList()
    )

    private data class WeekCache(
        val savedAt: Long = 0L,
        val isComplete: Boolean = false,
        val expectedGroups: Int = 0,
        val lessonsByGroup: Map<String, List<PublicLesson>> = emptyMap()
    )

    private fun loadGroupsCache(): GroupsCache? = runCatching {
        val file = groupsCacheFile()
        if (!file.exists()) return null
        gson.fromJson(file.readText(), GroupsCache::class.java)
    }.getOrNull()

    private fun saveGroupsCache(groups: List<PublicGroupInfo>) {
        runCatching {
            groupsCacheFile().writeText(
                gson.toJson(GroupsCache(savedAt = System.currentTimeMillis(), groups = groups))
            )
        }
    }

    private fun loadWeekCache(weekKey: String): WeekCache? = runCatching {
        val file = weekCacheFile(weekKey)
        if (!file.exists()) return null
        gson.fromJson(file.readText(), WeekCache::class.java)
    }.getOrNull()

    private fun saveWeekCache(
        weekKey: String,
        lessonsByGroup: Map<String, List<PublicLesson>>,
        complete: Boolean,
        expectedGroups: Int
    ) {
        runCatching {
            weekCacheFile(weekKey).writeText(
                gson.toJson(
                    WeekCache(
                        savedAt = System.currentTimeMillis(),
                        isComplete = complete,
                        expectedGroups = expectedGroups,
                        lessonsByGroup = lessonsByGroup
                    )
                )
            )
        }
    }

    private fun groupsCacheFile(): File = File(context.cacheDir, "public_schedule_groups.json")

    private fun weekCacheFile(weekKey: String): File =
        File(context.cacheDir, "public_schedule_flow_$weekKey.json")

    private fun cleanupOldWeekCaches(keep: Int) {
        runCatching {
            context.cacheDir.listFiles { file -> file.name.startsWith("public_schedule_flow_") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(keep)
                ?.forEach { it.delete() }
        }
    }

    private fun WeekCache.isFresh(ttlMs: Long): Boolean =
        savedAt > 0 && System.currentTimeMillis() - savedAt < ttlMs

    private fun GroupsCache.isFresh(ttlMs: Long): Boolean =
        savedAt > 0 && System.currentTimeMillis() - savedAt < ttlMs

    private object Log {
        fun info(tag: String, message: String) {
            runCatching { NetworkLogger.logInfo(tag, message) }
        }
    }

    companion object {
        private const val GROUPS_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val WEEK_CACHE_TTL_MS = 6L * 60 * 60 * 1000
        private const val REQUEST_GAP_MS = 400L
        private const val PUBLIC_SCHEDULE_URL = "https://gukolomna.ru/studentu/schedule/"

        @Volatile
        private var instance: PublicScheduleFlowRepository? = null

        fun getInstance(context: Context): PublicScheduleFlowRepository {
            return instance ?: synchronized(this) {
                instance ?: PublicScheduleFlowRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
