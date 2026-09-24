package ru.nya.nyeios.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.EventListener
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import ru.nya.nyeios.data.model.AuthSession
import ru.nya.nyeios.data.model.CurriculumTerm
import ru.nya.nyeios.data.model.FeedPost
import ru.nya.nyeios.data.model.FeedSyncProgress
import ru.nya.nyeios.data.model.FeedSyncStage
import ru.nya.nyeios.data.model.UserProfile
import ru.nya.nyeios.data.model.WeekSchedule
import ru.nya.nyeios.data.net.NetworkLogger
import ru.nya.nyeios.data.net.NetworkLogLevel
import ru.nya.nyeios.data.net.NetworkMetricsTracker
import ru.nya.nyeios.data.parser.CurriculumParser
import ru.nya.nyeios.data.parser.FeedParser
import ru.nya.nyeios.data.parser.ProfileParser
import ru.nya.nyeios.data.parser.ScheduleParser
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class EiosRepository(private val context: Context) {

    private val gson = Gson()
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val securePrefs by lazy {
        try {
            EncryptedSharedPreferences.create(
                "nyeios_secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("nyeios_secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val networkMutex = Mutex()

    private val _feedSyncProgress = MutableStateFlow(FeedSyncProgress())
    val feedSyncProgress: StateFlow<FeedSyncProgress> = _feedSyncProgress.asStateFlow()
    val isFeedSyncing: Boolean get() = _feedSyncProgress.value.isSyncing
    private val activeFeedCall = AtomicReference<okhttp3.Call?>(null)

    private val _authSession = MutableStateFlow(loadAuthSession())
    val authSession: StateFlow<AuthSession> = _authSession.asStateFlow()

    private val _userProfile = MutableStateFlow(loadUserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(securePrefs.getLong("last_sync_timestamp", 0L))
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    fun updateLastSyncTime() {
        val now = System.currentTimeMillis()
        _lastSyncTime.value = now
        try {
            securePrefs.edit().putLong("last_sync_timestamp", now).apply()
        } catch (e: Exception) {
            // Ignore pref save failure
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        createOkHttpClient()
    }

    fun getActiveCookieString(): String = getActiveCookies()
    fun isUserLoggedIn(): Boolean = _authSession.value.isLoggedIn

    companion object {
        const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        @Volatile
        private var INSTANCE: EiosRepository? = null

        fun getInstance(context: Context): EiosRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EiosRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun createOkHttpClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        return OkHttpClient.Builder()
            // Force HTTP/1.1: Bitrix on PHP 7.2 stalls on HTTP/2 stream multiplexing when under load
            .protocols(listOf(Protocol.HTTP_1_1))
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .retryOnConnectionFailure(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                builder.header("User-Agent", BROWSER_USER_AGENT)
                if (original.header("Accept") == null) {
                    builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                }
                if (original.header("Accept-Language") == null) {
                    builder.header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                }
                builder.header("Sec-CH-UA", "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"")
                builder.header("Sec-CH-UA-Mobile", "?1")
                builder.header("Sec-CH-UA-Platform", "\"Android\"")
                builder.header("Upgrade-Insecure-Requests", "1")
                val finalReq = builder.build()

                val startNs = System.nanoTime()
                val isLogin = finalReq.url.encodedPath.contains("login") || finalReq.url.query?.contains("login=yes") == true
                val requestInfo = if (isLogin) {
                    "Отправка данных входа (AUTH_FORM=Y, TYPE=AUTH, USER_REMEMBER=Y, логин и пароль переданы)"
                } else null

                NetworkLogger.logRequest(
                    method = finalReq.method,
                    url = finalReq.url.toString(),
                    userAgent = BROWSER_USER_AGENT,
                    summary = requestInfo
                )

                val response: Response
                try {
                    response = chain.proceed(finalReq)
                } catch (e: Exception) {
                    val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
                    NetworkLogger.logError(
                        tag = "NET / ERROR",
                        message = "Ошибка сети: ${e.javaClass.simpleName} (${e.localizedMessage ?: e.message})",
                        error = e,
                        durationMs = tookMs
                    )
                    throw e
                }

                val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
                val setCookies = response.headers("Set-Cookie").mapNotNull { h ->
                    h.split(";").firstOrNull()?.split("=", limit = 2)?.firstOrNull()?.trim()
                }
                val redirectLoc = response.header("Location")
                val details = buildString {
                    if (!redirectLoc.isNullOrEmpty()) append("Редирект на: $redirectLoc\n")
                    if (setCookies.isNotEmpty()) append("Установлены куки: ${setCookies.joinToString(", ")}\n")
                    val cl = response.body?.contentLength() ?: -1L
                    if (cl > 0) append("Размер ответа: $cl байт\n")
                }

                NetworkLogger.logResponse(
                    code = response.code,
                    message = response.message.ifEmpty { "HTTP/${response.protocol}" },
                    url = finalReq.url.toString(),
                    durationMs = tookMs,
                    details = details.trim().ifEmpty { null }
                )

                if (finalReq.method.equals("GET", ignoreCase = true) && finalReq.url.host.contains("gukolomna.ru")) {
                    try {
                        NetworkMetricsTracker.getInstance(context).recordEiosGet(tookMs, finalReq.url.toString())
                    } catch (_: Exception) {
                        // Ignore metric recording failures
                    }
                }

                response
            }
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val hostKey = url.host
                    val existing = cookieStore.getOrPut(hostKey) { mutableListOf() }
                    for (c in cookies) {
                        existing.removeAll { it.name == c.name }
                        existing.add(c)
                    }
                    val parts = hostKey.split(".")
                    if (parts.size >= 2) {
                        val rootDomain = parts.takeLast(2).joinToString(".")
                        val rootList = cookieStore.getOrPut(rootDomain) { mutableListOf() }
                        for (c in cookies) {
                            rootList.removeAll { it.name == c.name }
                            rootList.add(c)
                        }
                    }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val result = mutableListOf<Cookie>()
                    val hostKey = url.host
                    cookieStore[hostKey]?.let { result.addAll(it) }

                    // Also include parent domain cookies (e.g. gukolomna.ru)
                    val parts = hostKey.split(".")
                    if (parts.size >= 2) {
                        val rootDomain = parts.takeLast(2).joinToString(".")
                        if (rootDomain != hostKey) {
                            cookieStore[rootDomain]?.let { parentCookies ->
                                for (pc in parentCookies) {
                                    if (result.none { it.name == pc.name }) {
                                        result.add(pc)
                                    }
                                }
                            }
                        }
                    }

                    // Fallback to active session cookies if any missing in store
                    val activeCookies = getActiveCookies()
                    if (activeCookies.isNotEmpty()) {
                        for (part in activeCookies.split(";")) {
                            val pair = part.trim().split("=", limit = 2)
                            if (pair.size == 2) {
                                val name = pair[0].trim()
                                val value = pair[1].trim()
                                if (result.none { it.name == name }) {
                                    try {
                                        result.add(
                                            Cookie.Builder()
                                                .name(name)
                                                .value(value)
                                                .domain(url.host)
                                                .path("/")
                                                .build()
                                        )
                                    } catch (e: Exception) {
                                        // ignore
                                    }
                                }
                            }
                        }
                    }

                    return result
                }
            })
            .build()
    }

    private fun loadAuthSession(): AuthSession {
        val username = securePrefs.getString("user_login", "") ?: ""
        val cookies = securePrefs.getString("user_cookies", "") ?: ""
        val isLoggedIn = cookies.contains("BITRIX_SM_UIDH") || cookies.contains("BITRIX_SM_LOGIN")

        // Prepopulate cookieStore from saved cookies
        if (cookies.isNotEmpty()) {
            val host = "eios.gukolomna.ru"
            val list = cookieStore.getOrPut(host) { mutableListOf() }
            for (part in cookies.split(";")) {
                val pair = part.trim().split("=", limit = 2)
                if (pair.size == 2) {
                    try {
                        val c = Cookie.Builder()
                            .name(pair[0].trim())
                            .value(pair[1].trim())
                            .domain("gukolomna.ru")
                            .path("/")
                            .build()
                        list.removeAll { it.name == c.name }
                        list.add(c)
                    } catch (e: Exception) {
                        // ignore malformed
                    }
                }
            }
        }

        return AuthSession(
            username = username,
            cookies = cookies,
            isLoggedIn = isLoggedIn,
            authSource = if (isLoggedIn) "saved" else "none"
        )
    }

    private fun loadUserProfile(): UserProfile {
        val name = securePrefs.getString("profile_name", "") ?: ""
        val userId = securePrefs.getString("profile_user_id", "") ?: ""
        val currid = securePrefs.getString("profile_currid", "") ?: ""
        return UserProfile(name = name, userId = userId, currid = currid)
    }

    private fun updateProfileIfFound(html: String) {
        val parsed = ProfileParser.parse(html)
        var updated = false
        var current = _userProfile.value

        if (parsed.name.isNotEmpty() && parsed.name != current.name) {
            current = current.copy(name = parsed.name)
            updated = true
        }
        if (parsed.userId.isNotEmpty() && parsed.userId != current.userId) {
            current = current.copy(userId = parsed.userId)
            updated = true
        }
        if (parsed.currid.isNotEmpty() && parsed.currid != current.currid) {
            current = current.copy(currid = parsed.currid)
            updated = true
        }

        if (updated) {
            securePrefs.edit()
                .putString("profile_name", current.name)
                .putString("profile_user_id", current.userId)
                .putString("profile_currid", current.currid)
                .apply()
            _userProfile.value = current
        }
    }

    private fun getActiveCookies(): String {
        val session = _authSession.value
        return session.cookies.ifEmpty {
            val u = securePrefs.getString("user_login", "") ?: ""
            val p = securePrefs.getString("user_password", "") ?: ""
            if (u.isNotEmpty() && p.isNotEmpty()) {
                // If credentials are saved, we can return stored cookies
                securePrefs.getString("user_cookies", "") ?: ""
            } else ""
        }
    }

    suspend fun login(username: String, pass: String): Result<String> = networkMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                NetworkLogger.logInfo("AUTH", "Инициализация входа в аккаунт: ${username.take(3)}***@...")

                val loginUrl = "https://eios.gukolomna.ru/index.php?login=yes"
                val formBody = FormBody.Builder()
                    .add("AUTH_FORM", "Y")
                    .add("TYPE", "AUTH")
                    .add("backurl", "/index.php")
                    .add("USER_LOGIN", username)
                    .add("USER_PASSWORD", pass)
                    .add("USER_REMEMBER", "Y")
                    .build()

                val request = Request.Builder()
                    .url(loginUrl)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .post(formBody)
                    .build()

                // Do not follow redirects automatically: on success, Bitrix responds with 302 Found
                // within ~300ms containing session cookies, avoiding heavy 7.57MB /eios/ page download.
                val loginClient = okHttpClient.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                val response = loginClient.newCall(request).execute()

                // Collect all Set-Cookie headers
                val collectedCookies = mutableMapOf<String, String>()
                for (h in response.headers("Set-Cookie")) {
                    val pair = h.split(";").firstOrNull()?.split("=", limit = 2)
                    if (pair != null && pair.size == 2) {
                        collectedCookies[pair[0].trim()] = pair[1].trim()
                    }
                }

                // Also check cookies in CookieJar
                val httpUrl = "https://eios.gukolomna.ru/".toHttpUrl()
                val jarCookies = okHttpClient.cookieJar.loadForRequest(httpUrl)
                for (c in jarCookies) {
                    collectedCookies[c.name] = c.value
                }

                val hasSession = collectedCookies.containsKey("BITRIX_SM_UIDH") ||
                        collectedCookies.containsKey("BITRIX_SM_LOGIN") ||
                        (response.code in 300..399 && collectedCookies.containsKey("PHPSESSID"))

                val html = response.body?.string().orEmpty()
                if (hasSession || response.code in 300..399) {
                    val cookieStr = collectedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    securePrefs.edit()
                        .putString("user_login", username)
                        .putString("user_password", pass)
                        .putString("user_cookies", cookieStr)
                        .apply()

                    // Synchronize collected cookies directly into in-memory cookieStore
                    for ((k, v) in collectedCookies) {
                        try {
                            val c1 = Cookie.Builder().name(k).value(v).domain("eios.gukolomna.ru").path("/").build()
                            val c2 = Cookie.Builder().name(k).value(v).domain("gukolomna.ru").path("/").build()
                            cookieStore.getOrPut("eios.gukolomna.ru") { mutableListOf() }.apply {
                                removeAll { it.name == k }
                                add(c1)
                            }
                            cookieStore.getOrPut("gukolomna.ru") { mutableListOf() }.apply {
                                removeAll { it.name == k }
                                add(c2)
                            }
                        } catch (e: Exception) {
                            // ignore malformed cookie
                        }
                    }

                    val newSession = AuthSession(
                        username = username,
                        cookies = cookieStr,
                        isLoggedIn = true,
                        authSource = "saved"
                    )
                    _authSession.value = newSession

                    NetworkLogger.logSuccess(
                        tag = "AUTH",
                        message = "Авторизация успешна (HTTP ${response.code})",
                        details = "Установлены куки: ${collectedCookies.keys.joinToString(", ")}"
                    )

                    updateProfileIfFound(html)
                    updateLastSyncTime()
                    Result.success("Успешный вход")
                } else {
                    val doc = Jsoup.parse(html)
                    val errorEl = doc.selectFirst(".errortext") ?: doc.selectFirst(".error")
                    val detailedError = errorEl?.text()?.trim()

                    val errorMsg = when {
                        !detailedError.isNullOrEmpty() -> detailedError
                        html.contains("Неверный логин или пароль", ignoreCase = true) -> "Неверный логин или пароль"
                        html.contains("Пользователь заблокирован", ignoreCase = true) -> "Пользователь заблокирован"
                        else -> "Неверный логин или пароль портала ЭИОС"
                    }

                    NetworkLogger.logError("AUTH", "Сервер отклонил вход: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                NetworkLogger.logError("AUTH", "Сетевая ошибка при авторизации: ${e.localizedMessage ?: e.message}", e)
                Result.failure(Exception("Сетевая ошибка при авторизации: ${e.localizedMessage ?: e.message}"))
            }
        }
    }

    fun logout() {
        securePrefs.edit()
            .remove("user_login")
            .remove("user_password")
            .remove("user_cookies")
            .remove("profile_name")
            .remove("profile_user_id")
            .remove("profile_currid")
            .apply()

        cookieStore.clear()
        _authSession.value = AuthSession()
        _userProfile.value = UserProfile()
    }

    fun getWeekDates(offsetWeeks: Int): Pair<String, String> {
        val today = LocalDate.now().plusWeeks(offsetWeeks.toLong())
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

        val dFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault())
        return Pair(monday.format(dFmt), sunday.format(dFmt))
    }

    suspend fun getSchedule(offsetWeeks: Int, forceNetwork: Boolean = false): Result<WeekSchedule> = withContext(Dispatchers.IO) {
        val (startD, endD) = getWeekDates(offsetWeeks)
        val cacheFile = File(context.cacheDir, "schedule_${startD}_${endD}.json")

        // 1. Return from disk cache if network is not forced and cache exists
        if (!forceNetwork && cacheFile.exists()) {
            try {
                val json = cacheFile.readText()
                val cached = gson.fromJson(json, WeekSchedule::class.java)
                if (cached != null) {
                    return@withContext Result.success(sanitizeCachedSchedule(cached))
                }
            } catch (e: Exception) {
                // Ignore corrupt cache
            }
        }

        // 2. If feed is currently syncing, serve cache without hitting the network to avoid PHP session lock contention
        if (_feedSyncProgress.value.isSyncing && cacheFile.exists()) {
            try {
                val json = cacheFile.readText()
                val cached = gson.fromJson(json, WeekSchedule::class.java)
                if (cached != null) {
                    NetworkLogger.logInfo("SCHEDULE", "Идёт синхронизация ленты: расписание отдано из кэша")
                    return@withContext Result.success(sanitizeCachedSchedule(cached))
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // 3. Fetch from network
        return@withContext networkMutex.withLock {
            val (startDCurrent, endDCurrent) = getWeekDates(offsetWeeks)
            val currentCacheFile = File(context.cacheDir, "schedule_${startDCurrent}_${endDCurrent}.json")
            val url = "https://eios.gukolomna.ru/eios/contacts/timetable/?startDate=$startDCurrent&endDate=$endDCurrent"
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)

            try {
                val response = okHttpClient.newCall(reqBuilder.build()).execute()
                if (!response.isSuccessful) {
                    if (currentCacheFile.exists()) {
                        val cached = gson.fromJson(currentCacheFile.readText(), WeekSchedule::class.java)
                        if (cached != null) return@withLock Result.success(sanitizeCachedSchedule(cached))
                    }
                    return@withLock Result.failure(Exception("Ошибка сервера: HTTP ${response.code}"))
                }

                val html = response.body?.string() ?: ""
                updateProfileIfFound(html)

                if (ScheduleParser.isAuthRequired(html)) {
                    if (currentCacheFile.exists()) {
                        val cached = gson.fromJson(currentCacheFile.readText(), WeekSchedule::class.java)
                        if (cached != null) return@withLock Result.success(sanitizeCachedSchedule(cached))
                    }
                    return@withLock Result.failure(Exception("Сессия завершена. Требуется авторизация в ЭИОС."))
                }

                val schedule = ScheduleParser.parse(html, offsetWeeks, startDCurrent, endDCurrent)
                if (schedule != null) {
                    try {
                        currentCacheFile.writeText(gson.toJson(schedule))
                    } catch (e: Exception) {
                        // Ignore cache write error
                    }
                    updateLastSyncTime()
                    Result.success(schedule)
                } else {
                    if (currentCacheFile.exists()) {
                        val cached = gson.fromJson(currentCacheFile.readText(), WeekSchedule::class.java)
                        if (cached != null) return@withLock Result.success(sanitizeCachedSchedule(cached))
                    }
                    Result.failure(Exception("Не удалось разобрать страницу расписания."))
                }
            } catch (e: Exception) {
                if (currentCacheFile.exists()) {
                    val cached = gson.fromJson(currentCacheFile.readText(), WeekSchedule::class.java)
                    if (cached != null) return@withLock Result.success(sanitizeCachedSchedule(cached))
                }
                Result.failure(Exception("Сетевая ошибка: ${e.localizedMessage ?: e.message}"))
            }
        }
    }

    private fun sanitizeCachedSchedule(cached: WeekSchedule): WeekSchedule {
        val moscowZone = try {
            java.time.ZoneId.of("Europe/Moscow")
        } catch (e: Exception) {
            java.time.ZoneId.systemDefault()
        }
        val moscowToday = LocalDate.now(moscowZone)
        val todayDayMonth = moscowToday.format(DateTimeFormatter.ofPattern("dd.MM", Locale.getDefault()))
        val updatedDays = cached.days.map { day ->
            val cleanDate = Regex("""(\d{1,2}\.\d{1,2})""").find(day.dateString.ifEmpty { day.dayTitle })?.value ?: day.dateString
            val isToday = (cached.offsetWeeks == 0 && cleanDate == todayDayMonth)
            day.copy(dateString = cleanDate, isToday = isToday)
        }
        return cached.copy(days = updatedDays, isCached = true)
    }

    fun getCachedFeed(): List<FeedPost>? {
        val cacheFile = File(context.cacheDir, "feed_cache.json")
        val listType = object : TypeToken<List<FeedPost>>() {}.type
        if (cacheFile.exists()) {
            try {
                return gson.fromJson(cacheFile.readText(), listType)
            } catch (e: Exception) {
                // ignore
            }
        }
        return null
    }

    fun cancelFeedSync() {
        activeFeedCall.getAndSet(null)?.cancel()
        _feedSyncProgress.value = FeedSyncProgress(
            isSyncing = false,
            stage = FeedSyncStage.IDLE,
            statusText = "Синхронизация отменена пользователем"
        )
    }

    suspend fun syncFeed(maxPosts: Int = 100): Result<List<FeedPost>> = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "feed_cache.json")
        val listType = object : TypeToken<List<FeedPost>>() {}.type

        networkMutex.withLock {
            val startTime = System.currentTimeMillis()
            var timerJob: kotlinx.coroutines.Job? = null

            _feedSyncProgress.value = FeedSyncProgress(
                isSyncing = true,
                stage = FeedSyncStage.CONNECTING,
                elapsedSeconds = 0,
                statusText = "Подключение к eios.gukolomna.ru...",
                subStatusText = "Установка TLSv1.3 соединения"
            )

            // Background ticker updating elapsed seconds and dynamic server phase every 1000ms
            timerJob = CoroutineScope(Dispatchers.Default).launch {
                var lastLogTime = 0
                while (isActive && _feedSyncProgress.value.isSyncing) {
                    delay(1000)
                    val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                    val cur = _feedSyncProgress.value

                    if (cur.stage == FeedSyncStage.SERVER_PROCESSING) {
                        val serverPhaseText = when {
                            elapsed < 10 -> "Запрос передан на сервер 1С-Битрикс..."
                            elapsed < 25 -> "Сервер опрашивает БД (посты групп, задания и вложения)..."
                            elapsed < 60 -> "PHP 7.2 генерирует тяжелую HTML-разметку (~7.5 МБ)..."
                            elapsed < 120 -> "Сервер ГСГУ под высокой нагрузкой. Соединение активно..."
                            elapsed < 240 -> "Сервер продолжает рендеринг страницы. Пожалуйста, подождите..."
                            elapsed < 480 -> "Большая очередь запросов на сервере. Канал связи удерживается..."
                            else -> "Сервер отвечает с большой задержкой, связь активна (таймаут 25 мин)..."
                        }
                        val subText = "Соединение активно • Прошло ${formatDuration(elapsed)}"
                        _feedSyncProgress.value = cur.copy(
                            elapsedSeconds = elapsed,
                            statusText = serverPhaseText,
                            subStatusText = subText,
                            isReceivingPackets = false
                        )

                        if (elapsed - lastLogTime >= 30) {
                            lastLogTime = elapsed
                            NetworkLogger.logInfo(
                                "FEED",
                                "Ожидание ответа сервера: $elapsed сек (соединение удерживается, GET /eios/)"
                            )
                        }
                    } else {
                        _feedSyncProgress.value = cur.copy(elapsedSeconds = elapsed)
                    }
                }
            }

            try {
                NetworkLogger.logInfo("FEED", "Запуск синхронизации Живой ленты (GET https://eios.gukolomna.ru/eios/)")

                // Dedicated client with 25-minute read timeout and detailed event listening
                val feedSyncClient = okHttpClient.newBuilder()
                    .connectTimeout(2, TimeUnit.MINUTES)
                    .readTimeout(25, TimeUnit.MINUTES)
                    .writeTimeout(2, TimeUnit.MINUTES)
                    .eventListener(object : EventListener() {
                        override fun responseHeadersStart(call: Call) {
                            NetworkLogger.logInfo("FEED", "Сервер передал первые байты ответа (заголовки)")
                            _feedSyncProgress.value = _feedSyncProgress.value.copy(
                                stage = FeedSyncStage.DOWNLOADING,
                                statusText = "Сервер ответил, начинается передача данных...",
                                subStatusText = "Получены HTTP заголовки"
                            )
                        }
                    })
                    .build()

                val url = "https://eios.gukolomna.ru/eios/"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()

                val call = feedSyncClient.newCall(request)
                activeFeedCall.set(call)

                _feedSyncProgress.value = _feedSyncProgress.value.copy(
                    stage = FeedSyncStage.SERVER_PROCESSING,
                    statusText = "Запрос передан на сервер 1С-Битрикс...",
                    subStatusText = "Соединение установлено, ожидание ответа PHP"
                )

                val response = call.execute()
                if (!response.isSuccessful) {
                    throw Exception("Сервер вернул HTTP ${response.code}")
                }

                val body = response.body ?: throw Exception("Сервер вернул пустой ответ")
                val totalLength = body.contentLength()
                val inputStream = body.byteStream()
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var downloaded = 0L
                var lastSpeedCalc = System.currentTimeMillis()
                var bytesSinceLastSpeed = 0L
                var currentSpeed = 0L

                _feedSyncProgress.value = _feedSyncProgress.value.copy(
                    stage = FeedSyncStage.DOWNLOADING,
                    statusText = "Сервер ответил, начало скачивания данных...",
                    subStatusText = "Поток данных открыт"
                )

                while (true) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    downloaded += read
                    bytesSinceLastSpeed += read

                    val now = System.currentTimeMillis()
                    val dt = now - lastSpeedCalc
                    if (dt >= 350) {
                        currentSpeed = (bytesSinceLastSpeed * 1000) / dt
                        bytesSinceLastSpeed = 0L
                        lastSpeedCalc = now

                        val speedStr = formatSpeed(currentSpeed)
                        val sizeStr = formatBytes(downloaded)
                        val estimatedTotal = if (totalLength > 0) totalLength else 7_800_000L
                        val percent = ((downloaded.toDouble() / estimatedTotal) * 100).toInt().coerceIn(1, 99)
                        val remainingBytes = (estimatedTotal - downloaded).coerceAtLeast(0)
                        val etaSec = if (currentSpeed > 0) (remainingBytes / currentSpeed).toInt() else null
                        val etaStr = if (etaSec != null && etaSec in 1..600) " • ост. ~${etaSec}с" else ""

                        val mainStatus = if (totalLength > 0) {
                            "Получено $sizeStr из ${formatBytes(totalLength)} ($percent%)$etaStr"
                        } else {
                            "Получено $sizeStr (~$percent%)$etaStr"
                        }

                        val subStatus = "Скорость: $speedStr • Поток данных активен ●"

                        _feedSyncProgress.value = _feedSyncProgress.value.copy(
                            stage = FeedSyncStage.DOWNLOADING,
                            bytesDownloaded = downloaded,
                            totalBytes = estimatedTotal,
                            speedBps = currentSpeed,
                            statusText = mainStatus,
                            subStatusText = subStatus,
                            etaSeconds = etaSec,
                            isReceivingPackets = true
                        )
                    }
                }

                _feedSyncProgress.value = _feedSyncProgress.value.copy(
                    stage = FeedSyncStage.PARSING,
                    bytesDownloaded = downloaded,
                    statusText = "Обработка объявлений и файлов (${formatBytes(downloaded)})...",
                    subStatusText = "Парсинг постов и документов"
                )

                val html = out.toString("UTF-8")
                updateProfileIfFound(html)

                val posts = FeedParser.parse(html, maxPosts = maxPosts)
                if (posts.isNotEmpty()) {
                    try {
                        cacheFile.writeText(gson.toJson(posts))
                    } catch (e: Exception) {
                        // ignore write failure
                    }
                }

                updateLastSyncTime()

                val totalElapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                val durationStr = formatDuration(totalElapsed)
                _feedSyncProgress.value = FeedSyncProgress(
                    isSyncing = false,
                    stage = FeedSyncStage.COMPLETED,
                    elapsedSeconds = totalElapsed,
                    bytesDownloaded = downloaded,
                    totalBytes = downloaded,
                    statusText = "Синхронизация завершена: загружено ${posts.size} постов (${formatBytes(downloaded)} за $durationStr)",
                    subStatusText = "Лента сохранена в локальный кэш"
                )

                NetworkLogger.logSuccess(
                    tag = "FEED",
                    message = "Синхронизация Живой ленты успешно завершена",
                    details = "Постов: ${posts.size}, Размер: ${formatBytes(downloaded)}, Время: $durationStr"
                )

                Result.success(posts)
            } catch (e: Exception) {
                val totalElapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                val isCancelled = e is java.io.InterruptedIOException ||
                        e.message?.contains("canceled", ignoreCase = true) == true ||
                        activeFeedCall.get()?.isCanceled() == true

                val errText = if (isCancelled) {
                    "Синхронизация отменена пользователем"
                } else {
                    "Ошибка синхронизации: ${e.localizedMessage ?: e.message}"
                }

                _feedSyncProgress.value = FeedSyncProgress(
                    isSyncing = false,
                    stage = if (isCancelled) FeedSyncStage.IDLE else FeedSyncStage.ERROR,
                    elapsedSeconds = totalElapsed,
                    statusText = errText,
                    errorMessage = if (!isCancelled) errText else null
                )

                if (!isCancelled) {
                    NetworkLogger.logError("FEED", "Сбой синхронизации ленты: ${e.localizedMessage ?: e.message}", e)
                }

                if (cacheFile.exists()) {
                    try {
                        val cached: List<FeedPost>? = gson.fromJson(cacheFile.readText(), listType)
                        if (!cached.isNullOrEmpty()) return@withLock Result.success(cached)
                    } catch (ex: Exception) {}
                }

                Result.failure(Exception(errText))
            } finally {
                activeFeedCall.set(null)
                timerJob?.cancel()
                if (_feedSyncProgress.value.isSyncing) {
                    _feedSyncProgress.value = _feedSyncProgress.value.copy(isSyncing = false)
                }
            }
        }
    }

    suspend fun getFeed(forceNetwork: Boolean = false): Result<List<FeedPost>> = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "feed_cache.json")
        val listType = object : TypeToken<List<FeedPost>>() {}.type

        // 1. Check local cache
        if (!forceNetwork && cacheFile.exists()) {
            try {
                val json = cacheFile.readText()
                val cached: List<FeedPost>? = gson.fromJson(json, listType)
                if (!cached.isNullOrEmpty()) {
                    return@withContext Result.success(cached)
                }
            } catch (e: Exception) {
                // Ignore cache parse error
            }
        }

        if (forceNetwork) {
            return@withContext syncFeed()
        }

        getCachedFeed()?.let { return@withContext Result.success(it) }
        Result.success(emptyList())
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.US, "%d КБ", bytes / 1024)
            bytes > 0 -> "$bytes Б"
            else -> "0 Б"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f МБ/с", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format(Locale.US, "%d КБ/с", bytesPerSec / 1024)
            bytesPerSec > 0 -> "$bytesPerSec Б/с"
            else -> "0 Б/с"
        }
    }

    fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "$m мин $s с" else "$s с"
    }

    fun getCachedCurriculum(): List<CurriculumTerm>? {
        val cacheFile = File(context.cacheDir, "curriculum_cache.json")
        val listType = object : TypeToken<List<CurriculumTerm>>() {}.type
        if (cacheFile.exists()) {
            try {
                return gson.fromJson(cacheFile.readText(), listType)
            } catch (e: Exception) {
                // ignore
            }
        }
        return null
    }

    suspend fun getCurriculum(forceNetwork: Boolean = false): Result<List<CurriculumTerm>> = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "curriculum_cache.json")
        val listType = object : TypeToken<List<CurriculumTerm>>() {}.type

        // 1. Check local cache
        if (!forceNetwork && cacheFile.exists()) {
            try {
                val json = cacheFile.readText()
                val cached: List<CurriculumTerm>? = gson.fromJson(json, listType)
                if (!cached.isNullOrEmpty()) {
                    return@withContext Result.success(cached)
                }
            } catch (e: Exception) {
                // Ignore cache parse error
            }
        }

        // 2. If feed is syncing, serve cache without hitting the network
        if (_feedSyncProgress.value.isSyncing && cacheFile.exists()) {
            try {
                val json = cacheFile.readText()
                val cached: List<CurriculumTerm>? = gson.fromJson(json, listType)
                if (!cached.isNullOrEmpty()) {
                    NetworkLogger.logInfo("CURRICULUM", "Идёт синхронизация ленты: успеваемость отдана из кэша")
                    return@withContext Result.success(cached)
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // 3. Fetch from network
        return@withContext networkMutex.withLock {
            val currid = _userProfile.value.currid
            val userId = _userProfile.value.userId

            val urlBuilder = StringBuilder("https://eios.gukolomna.ru/eios/contacts/curriculum/")
            val params = mutableListOf<String>()
            if (currid.isNotEmpty()) params.add("currid=$currid")
            if (userId.isNotEmpty()) params.add("user_id=$userId")
            if (params.isNotEmpty()) {
                urlBuilder.append("?").append(params.joinToString("&"))
            }

            val reqBuilder = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", BROWSER_USER_AGENT)

            try {
                val response = okHttpClient.newCall(reqBuilder.build()).execute()
                if (!response.isSuccessful) {
                    if (cacheFile.exists()) {
                        val cached: List<CurriculumTerm>? = gson.fromJson(cacheFile.readText(), listType)
                        if (!cached.isNullOrEmpty()) return@withLock Result.success(cached)
                    }
                    return@withLock Result.failure(Exception("Ошибка сервера БРС: HTTP ${response.code}"))
                }

                val html = response.body?.string() ?: ""
                updateProfileIfFound(html)

                val terms = CurriculumParser.parse(html)
                if (terms.isNotEmpty()) {
                    try {
                        cacheFile.writeText(gson.toJson(terms))
                    } catch (e: Exception) {
                        // Ignore cache write error
                    }
                    updateLastSyncTime()
                    Result.success(terms)
                } else {
                    if (cacheFile.exists()) {
                        val cached: List<CurriculumTerm>? = gson.fromJson(cacheFile.readText(), listType)
                        if (!cached.isNullOrEmpty()) return@withLock Result.success(cached)
                    }
                    Result.failure(Exception("Не удалось загрузить успеваемость. Проверьте авторизацию."))
                }
            } catch (e: Exception) {
                if (cacheFile.exists()) {
                    val cached: List<CurriculumTerm>? = gson.fromJson(cacheFile.readText(), listType)
                    if (!cached.isNullOrEmpty()) return@withLock Result.success(cached)
                }
                Result.failure(Exception("Сетевая ошибка успеваемости: ${e.localizedMessage ?: e.message}"))
            }
        }
    }

    suspend fun downloadAttachment(
        fileUrl: String,
        targetFile: File,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val cookies = getActiveCookies()
        val reqBuilder = Request.Builder()
            .url(fileUrl)
            .header("User-Agent", BROWSER_USER_AGENT)

        if (cookies.isNotEmpty()) {
            reqBuilder.header("Cookie", cookies)
        }

        try {
            val response = okHttpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Ошибка загрузки файла: HTTP ${response.code}"))
            }

            val finalUrl = response.request.url.toString()
            if (finalUrl.contains("login=yes") || response.header("X-Bitrix-Ajax-Status") == "Authorize") {
                return@withContext Result.failure(Exception("Для скачивания требуется авторизация в ЭИОС"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Пустой ответ сервера"))
            val totalBytes = body.contentLength()

            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            if (tempFile.exists()) tempFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        onProgress?.invoke(bytesRead, totalBytes)
                    }
                    output.flush()
                }
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(Exception("Не удалось скачать файл: ${e.localizedMessage ?: e.message}"))
        }
    }
}
