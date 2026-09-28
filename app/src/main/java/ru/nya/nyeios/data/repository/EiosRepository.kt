package ru.nya.nyeios.data.repository

import ru.nya.nyeios.data.AppLocale

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
import ru.nya.nyeios.data.net.EndpointHealthItem
import ru.nya.nyeios.data.net.EndpointStatus
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
        /** Resolved per call: the data layer has no Activity context, so these follow [AppLocale]. */
        val ERROR_SERVER_EMPTY_OR_DOWN: String
            get() = AppLocale.pick(
                "Ошибка со стороны сервера, сервер вернул пустую страницу. Попробуйте позже, может починят",
                "Server-side error: the server returned an empty page. Try again later"
            )
        val ERROR_NO_CONNECTION: String
            get() = AppLocale.pick(
                "Ошибка со стороны сервера... или вашего интернета. Сайт никак не отреагировал",
                "Server-side error... or your own connection. The site did not respond at all"
            )

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
                    AppLocale.pick("Отправка данных входа (AUTH_FORM=Y, TYPE=AUTH, USER_REMEMBER=Y, логин и пароль переданы)", "Submitting login data (AUTH_FORM=Y, TYPE=AUTH, USER_REMEMBER=Y, login and password sent)")
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
                        message = AppLocale.pick("Ошибка сети: ${e.javaClass.simpleName} (${e.localizedMessage ?: e.message})", "Network error: ${e.javaClass.simpleName} (${e.localizedMessage ?: e.message})"),
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
                    if (!redirectLoc.isNullOrEmpty()) append(AppLocale.pick("Редирект на: $redirectLoc\n", "Redirect to: $redirectLoc\n"))
                    if (setCookies.isNotEmpty()) append(
                        AppLocale.pick(
                            "Установлены куки: ${setCookies.joinToString(", ")}\n",
                            "Cookies set: ${setCookies.joinToString(", ")}\n"
                        )
                    )
                    val cl = response.body?.contentLength() ?: -1L
                    if (cl > 0) append(AppLocale.pick("Размер ответа: $cl байт\n", "Response size: $cl bytes\n"))
                }

                var isDegraded200 = false
                var degradedReason: String? = null
                var rawResponseBody: String? = null
                if (response.code == 200) {
                    try {
                        rawResponseBody = response.peekBody(1048576L).string()
                        val errText = ScheduleParser.extractErrorMessage(rawResponseBody)
                        if (errText != null) {
                            isDegraded200 = true
                            degradedReason = AppLocale.pick("Сбой сервера: $errText", "Server failure: $errText")
                        } else if (finalReq.url.encodedPath.contains("timetable") &&
                            !rawResponseBody.contains("schedule-table") &&
                            !rawResponseBody.contains("schedule-body") &&
                            !rawResponseBody.contains("group-select") &&
                            !rawResponseBody.contains("bx_auth_serv")
                        ) {
                            isDegraded200 = true
                            degradedReason = AppLocale.pick("Пустая страница расписания (таблица и контейнер отсутствуют)", "Empty schedule page (table and container are missing)")
                        }
                    } catch (_: Exception) {}
                }

                NetworkLogger.logResponse(
                    code = response.code,
                    message = response.message.ifEmpty { "HTTP/${response.protocol}" },
                    url = finalReq.url.toString(),
                    durationMs = tookMs,
                    details = details.trim().ifEmpty { null },
                    isDegraded = isDegraded200,
                    degradedReason = degradedReason,
                    rawResponse = rawResponseBody
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
                NetworkLogger.logInfo("AUTH", AppLocale.pick("Инициализация входа в аккаунт: ${username.take(3)}***@...", "Initializing account login: ${username.take(3)}***@..."))

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
                        message = AppLocale.pick("Авторизация успешна (HTTP ${response.code})", "Authorization successful (HTTP ${response.code})"),
                        details = AppLocale.pick(
                            "Установлены куки: ${collectedCookies.keys.joinToString(", ")}",
                            "Cookies set: ${collectedCookies.keys.joinToString(", ")}"
                        )
                    )

                    updateProfileIfFound(html)
                    updateLastSyncTime()
                    Result.success(AppLocale.pick("Успешный вход", "Login successful"))
                } else {
                    val doc = Jsoup.parse(html)
                    val errorEl = doc.selectFirst(".errortext") ?: doc.selectFirst(".error")
                    val detailedError = errorEl?.text()?.trim()

                    val errorMsg = when {
                        !detailedError.isNullOrEmpty() -> detailedError
                html.contains("Неверный логин или пароль", ignoreCase = true) ->
                    AppLocale.pick("Неверный логин или пароль", "Invalid login or password")
                html.contains("Пользователь заблокирован", ignoreCase = true) ->
                    AppLocale.pick("Пользователь заблокирован", "User is blocked")
                        else -> AppLocale.pick("Неверный логин или пароль портала ЭИОС", "Invalid login or password for the EIOS portal")
                    }

                    NetworkLogger.logError("AUTH", AppLocale.pick("Сервер отклонил вход: $errorMsg", "Server rejected the login: $errorMsg"))
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                NetworkLogger.logError("AUTH", AppLocale.pick("Сетевая ошибка при авторизации: ${e.localizedMessage ?: e.message}", "Network error during authorization: ${e.localizedMessage ?: e.message}"), e)
                Result.failure(Exception(AppLocale.pick("Сетевая ошибка при авторизации: ${e.localizedMessage ?: e.message}", "Network error during authorization: ${e.localizedMessage ?: e.message}")))
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
                    NetworkLogger.logInfo("SCHEDULE", AppLocale.pick("Идёт синхронизация ленты: расписание отдано из кэша", "Feed sync in progress: schedule served from cache"))
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
                    val msg = if (response.code in 500..599) ERROR_SERVER_EMPTY_OR_DOWN else AppLocale.pick("Ошибка сервера: HTTP ${response.code}", "Server error: HTTP ${response.code}")
                    return@withLock Result.failure(Exception(msg))
                }

                val html = response.body?.string() ?: ""
                updateProfileIfFound(html)

                if (ScheduleParser.isAuthRequired(html)) {
                    if (currentCacheFile.exists()) {
                        val cached = gson.fromJson(currentCacheFile.readText(), WeekSchedule::class.java)
                        if (cached != null) return@withLock Result.success(sanitizeCachedSchedule(cached))
                    }
                    return@withLock Result.failure(Exception(AppLocale.pick("Сессия завершена. Требуется авторизация в ЭИОС.", "Session expired. Authorization in EIOS is required.")))
                }

                val schedule = ScheduleParser.parse(html, offsetWeeks, startDCurrent, endDCurrent)
                if (schedule != null) {
                    val isAllDaysEmpty = schedule.days.all { it.lessons.isEmpty() }
                    val shouldOverwrite = if (isAllDaysEmpty && currentCacheFile.exists()) {
                        try {
                            val cached = gson.fromJson(currentCacheFile.readText(), WeekSchedule::class.java)
                            cached == null || cached.days.all { it.lessons.isEmpty() }
                        } catch (_: Exception) {
                            true
                        }
                    } else {
                        true
                    }

                    if (shouldOverwrite) {
                        try {
                            currentCacheFile.writeText(gson.toJson(schedule))
                        } catch (e: Exception) {
                            // Ignore cache write error
                        }
                    }
                    updateLastSyncTime()
                    Result.success(schedule)
                } else {
                    if (currentCacheFile.exists()) {
                        val cached = gson.fromJson(currentCacheFile.readText(), WeekSchedule::class.java)
                        if (cached != null) {
                            NetworkLogger.logInfo("SCHEDULE", AppLocale.pick("Сетевая ошибка расписания, данные взяты из локального кэша", "Schedule network error, data taken from the local cache"))
                            return@withLock Result.success(sanitizeCachedSchedule(cached))
                        }
                    }
                    Result.failure(Exception(ERROR_SERVER_EMPTY_OR_DOWN))
                }
            } catch (e: Exception) {
                if (currentCacheFile.exists()) {
                    val cached = gson.fromJson(currentCacheFile.readText(), WeekSchedule::class.java)
                    if (cached != null) return@withLock Result.success(sanitizeCachedSchedule(cached))
                }
                Result.failure(Exception(ERROR_NO_CONNECTION))
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
            statusText = AppLocale.pick("Синхронизация отменена пользователем", "Sync cancelled by the user")
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
                statusText = AppLocale.pick("Подключение к eios.gukolomna.ru...", "Connecting to eios.gukolomna.ru..."),
                subStatusText = AppLocale.pick("Установка TLSv1.3 соединения", "Establishing TLSv1.3 connection")
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
                            elapsed < 10 -> AppLocale.pick("Запрос передан на сервер 1С-Битрикс...", "Request handed over to the 1C-Bitrix server...")
                            elapsed < 25 -> AppLocale.pick("Сервер опрашивает БД (посты групп, задания и вложения)...", "The server is querying the database (group posts, assignments and attachments)...")
                            elapsed < 60 -> AppLocale.pick("PHP 7.2 генерирует тяжелую HTML-разметку (~7.5 МБ)...", "PHP 7.2 is generating heavy HTML markup (~7.5 MB)...")
                            elapsed < 120 -> AppLocale.pick("Сервер ГСГУ под высокой нагрузкой. Соединение активно...", "The GSGU server is under heavy load. Connection is alive...")
                            elapsed < 240 -> AppLocale.pick("Сервер продолжает рендеринг страницы. Пожалуйста, подождите...", "The server is still rendering the page. Please wait...")
                            elapsed < 480 -> AppLocale.pick("Большая очередь запросов на сервере. Канал связи удерживается...", "Long request queue on the server. The connection is being held...")
                            else -> AppLocale.pick("Сервер отвечает с большой задержкой, связь активна (таймаут 25 мин)...", "The server responds slowly, the connection is alive (25 min timeout)...")
                        }
                        val subText = AppLocale.pick("Соединение активно • Прошло ${formatDuration(elapsed)}", "Connection alive • Elapsed ${formatDuration(elapsed)}")
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
                                AppLocale.pick("Ожидание ответа сервера: $elapsed сек (соединение удерживается, GET /eios/)", "Waiting for the server response: $elapsed s (connection held, GET /eios/)")
                            )
                        }
                    } else {
                        _feedSyncProgress.value = cur.copy(elapsedSeconds = elapsed)
                    }
                }
            }

            try {
                NetworkLogger.logInfo("FEED", AppLocale.pick("Запуск синхронизации Живой ленты (GET https://eios.gukolomna.ru/eios/)", "Starting Live Feed sync (GET https://eios.gukolomna.ru/eios/)"))

                // Dedicated client with 25-minute read timeout and detailed event listening
                val feedSyncClient = okHttpClient.newBuilder()
                    .connectTimeout(2, TimeUnit.MINUTES)
                    .readTimeout(25, TimeUnit.MINUTES)
                    .writeTimeout(2, TimeUnit.MINUTES)
                    .eventListener(object : EventListener() {
                        override fun responseHeadersStart(call: Call) {
                            NetworkLogger.logInfo("FEED", AppLocale.pick("Сервер передал первые байты ответа (заголовки)", "The server sent the first response bytes (headers)"))
                            _feedSyncProgress.value = _feedSyncProgress.value.copy(
                                stage = FeedSyncStage.DOWNLOADING,
                                statusText = AppLocale.pick("Сервер ответил, начинается передача данных...", "The server responded, data transfer is starting..."),
                                subStatusText = AppLocale.pick("Получены HTTP заголовки", "HTTP headers received")
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
                    statusText = AppLocale.pick("Запрос передан на сервер 1С-Битрикс...", "Request handed over to the 1C-Bitrix server..."),
                    subStatusText = AppLocale.pick("Соединение установлено, ожидание ответа PHP", "Connection established, waiting for the PHP response")
                )

                val response = call.execute()
                if (!response.isSuccessful) {
                    val msg = if (response.code in 500..599) ERROR_SERVER_EMPTY_OR_DOWN
            else AppLocale.pick("Сервер вернул HTTP ${response.code}", "The server returned HTTP ${response.code}")
                    throw Exception(msg)
                }

                val body = response.body ?: throw Exception(ERROR_SERVER_EMPTY_OR_DOWN)
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
                    statusText = AppLocale.pick("Сервер ответил, начало скачивания данных...", "The server responded, download is starting..."),
                    subStatusText = AppLocale.pick("Поток данных открыт", "Data stream opened")
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
                        val etaStr = if (etaSec != null && etaSec in 1..600) AppLocale.pick(" • ост. ~${etaSec}с", " • ~${etaSec}s left") else ""

                        val mainStatus = if (totalLength > 0) {
                            AppLocale.pick("Получено $sizeStr из ${formatBytes(totalLength)} ($percent%)$etaStr", "Received $sizeStr of ${formatBytes(totalLength)} ($percent%)$etaStr")
                        } else {
                            AppLocale.pick("Получено $sizeStr (~$percent%)$etaStr", "Received $sizeStr (~$percent%)$etaStr")
                        }

                        val subStatus = AppLocale.pick("Скорость: $speedStr • Поток данных активен ●", "Speed: $speedStr • Data stream active ●")

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
                    statusText = AppLocale.pick("Обработка объявлений и файлов (${formatBytes(downloaded)})...", "Processing posts and files (${formatBytes(downloaded)})..."),
                    subStatusText = AppLocale.pick("Парсинг постов и документов", "Parsing posts and documents")
                )

                val html = out.toString("UTF-8")
                updateProfileIfFound(html)

                if (ScheduleParser.isAuthRequired(html)) {
                    throw Exception(AppLocale.pick("Сессия завершена. Требуется авторизация в ЭИОС.", "Session expired. Authorization in EIOS is required."))
                }

                val posts = FeedParser.parse(html, maxPosts = maxPosts)
                if (posts.isNotEmpty()) {
                    try {
                        cacheFile.writeText(gson.toJson(posts))
                    } catch (e: Exception) {
                        // ignore write failure
                    }
                    updateLastSyncTime()
                } else {
                    val hasErr = html.contains("errortext") || html.contains("Отсутствует соединение") || html.length < 500
                    if (hasErr) {
                        throw Exception(ERROR_SERVER_EMPTY_OR_DOWN)
                    }
                    updateLastSyncTime()
                }

                val totalElapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                val durationStr = formatDuration(totalElapsed)
                _feedSyncProgress.value = FeedSyncProgress(
                    isSyncing = false,
                    stage = FeedSyncStage.COMPLETED,
                    elapsedSeconds = totalElapsed,
                    bytesDownloaded = downloaded,
                    totalBytes = downloaded,
                    statusText = AppLocale.pick("Синхронизация завершена: загружено ${posts.size} постов (${formatBytes(downloaded)} за $durationStr)", "Sync complete: ${posts.size} posts downloaded (${formatBytes(downloaded)} in $durationStr)"),
                    subStatusText = AppLocale.pick("Лента сохранена в локальный кэш", "Feed saved to the local cache")
                )

                NetworkLogger.logSuccess(
                    tag = "FEED",
                    message = AppLocale.pick("Синхронизация Живой ленты успешно завершена", "Live Feed sync completed successfully"),
                    details = AppLocale.pick("Постов: ${posts.size}, Размер: ${formatBytes(downloaded)}, Время: $durationStr", "Posts: ${posts.size}, Size: ${formatBytes(downloaded)}, Time: $durationStr")
                )

                Result.success(posts)
            } catch (e: Exception) {
                val totalElapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                val isCancelled = e is java.io.InterruptedIOException ||
                        e.message?.contains("canceled", ignoreCase = true) == true ||
                        activeFeedCall.get()?.isCanceled() == true

                val isNetworkDown = (e is java.io.IOException || e is java.net.SocketTimeoutException || e is java.net.UnknownHostException) &&
                        !isCancelled && e.message != ERROR_SERVER_EMPTY_OR_DOWN

                val errText = when {
                    isCancelled -> AppLocale.pick("Синхронизация отменена пользователем", "Sync cancelled by the user")
                    e.message == ERROR_SERVER_EMPTY_OR_DOWN -> ERROR_SERVER_EMPTY_OR_DOWN
                    e.message == AppLocale.pick("Сессия завершена. Требуется авторизация в ЭИОС.", "Session expired. Authorization in EIOS is required.") -> e.message!!
                    isNetworkDown -> ERROR_NO_CONNECTION
                    else -> e.localizedMessage ?: ERROR_NO_CONNECTION
                }

                _feedSyncProgress.value = FeedSyncProgress(
                    isSyncing = false,
                    stage = if (isCancelled) FeedSyncStage.IDLE else FeedSyncStage.ERROR,
                    elapsedSeconds = totalElapsed,
                    statusText = errText,
                    errorMessage = if (!isCancelled) errText else null
                )

                if (!isCancelled) {
                    NetworkLogger.logError("FEED", AppLocale.pick("Сбой синхронизации ленты: ${e.localizedMessage ?: e.message}", "Feed sync failure: ${e.localizedMessage ?: e.message}"), e)
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
            bytes >= 1024 * 1024 -> String.format(Locale.US, AppLocale.pick("%.1f МБ", "%.1f MB"), bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.US, AppLocale.pick("%d КБ", "%d KB"), bytes / 1024)
            bytes > 0 -> AppLocale.pick("$bytes Б", "$bytes B")
            else -> AppLocale.pick("0 Б", "0 B")
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, AppLocale.pick("%.1f МБ/с", "%.1f MB/s"), bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format(Locale.US, AppLocale.pick("%d КБ/с", "%d KB/s"), bytesPerSec / 1024)
            bytesPerSec > 0 -> AppLocale.pick("$bytesPerSec Б/с", "$bytesPerSec B/s")
            else -> AppLocale.pick("0 Б/с", "0 B/s")
        }
    }

    fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) AppLocale.pick("$m мин $s с", "$m min $s s") else AppLocale.pick("$s с", "$s s")
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
                    NetworkLogger.logInfo("CURRICULUM", AppLocale.pick("Идёт синхронизация ленты: успеваемость отдана из кэша", "Feed sync in progress: curriculum served from cache"))
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
                    val msg = if (response.code in 500..599) ERROR_SERVER_EMPTY_OR_DOWN else AppLocale.pick("Ошибка сервера БРС: HTTP ${response.code}", "Curriculum (BRS) server error: HTTP ${response.code}")
                    return@withLock Result.failure(Exception(msg))
                }

                val html = response.body?.string() ?: ""
                updateProfileIfFound(html)

                if (ScheduleParser.isAuthRequired(html)) {
                    if (cacheFile.exists()) {
                        val cached: List<CurriculumTerm>? = gson.fromJson(cacheFile.readText(), listType)
                        if (!cached.isNullOrEmpty()) return@withLock Result.success(cached)
                    }
                    return@withLock Result.failure(Exception(AppLocale.pick("Сессия завершена. Требуется авторизация в ЭИОС.", "Session expired. Authorization in EIOS is required.")))
                }

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
                    Result.failure(Exception(ERROR_SERVER_EMPTY_OR_DOWN))
                }
            } catch (e: Exception) {
                if (cacheFile.exists()) {
                    val cached: List<CurriculumTerm>? = gson.fromJson(cacheFile.readText(), listType)
                    if (!cached.isNullOrEmpty()) return@withLock Result.success(cached)
                }
                Result.failure(Exception(ERROR_NO_CONNECTION))
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
                return@withContext Result.failure(Exception(AppLocale.pick("Ошибка загрузки файла: HTTP ${response.code}", "File download error: HTTP ${response.code}")))
            }

            val finalUrl = response.request.url.toString()
            if (finalUrl.contains("login=yes") || response.header("X-Bitrix-Ajax-Status") == "Authorize") {
                return@withContext Result.failure(Exception(AppLocale.pick("Для скачивания требуется авторизация в ЭИОС", "EIOS authorization is required to download this file")))
            }

            val body = response.body ?: return@withContext Result.failure(Exception(AppLocale.pick("Пустой ответ сервера", "Empty server response")))
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
            Result.failure(Exception(AppLocale.pick("Не удалось скачать файл: ${e.localizedMessage ?: e.message}", "Failed to download the file: ${e.localizedMessage ?: e.message}")))
        }
    }

    private val diagnosticHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Выполняет диагностическую проверку ключевых эндпоинтов портала (Расписание, Лента, БРС) поочередно.
     */
    suspend fun checkEndpointsHealth(): List<EndpointHealthItem> = withContext(Dispatchers.IO) {
        listOf(
            checkEndpointHealth("timetable"),
            checkEndpointHealth("feed"),
            checkEndpointHealth("curriculum")
        )
    }

    /**
     * Выполняет проверку одного конкретного эндпоинта по его id (timetable, feed, curriculum).
     * Использует отдельный клиент с жестким таймаутом в 15 секунд.
     */
    suspend fun checkEndpointHealth(id: String): EndpointHealthItem = withContext(Dispatchers.IO) {
        when (id) {
            "timetable" -> checkTimetableEndpoint()
            "feed" -> checkFeedEndpoint()
            "curriculum" -> checkCurriculumEndpoint()
            else -> EndpointHealthItem(
                id = id,
                name = id.uppercase(Locale.getDefault()),
                path = "/eios/",
                status = EndpointStatus.ERROR,
                message = AppLocale.pick("Неизвестный эндпоинт: $id", "Unknown endpoint: $id")
            )
        }
    }

    private fun checkTimetableEndpoint(): EndpointHealthItem {
        val start = System.currentTimeMillis()
        val url = "https://eios.gukolomna.ru/eios/contacts/timetable/"
        val path = "/eios/contacts/timetable/"
        var responseCode: Int? = null
        var responseMsg: String? = null
        var responseHeaders: okhttp3.Headers? = null
        var bodyStr = ""

        try {
            val req = Request.Builder().url(url).build()
            val resp = diagnosticHttpClient.newCall(req).execute()
            val latency = System.currentTimeMillis() - start
            responseCode = resp.code
            responseMsg = resp.message
            responseHeaders = resp.headers
            bodyStr = resp.body?.string() ?: ""
            val errText = ScheduleParser.extractErrorMessage(bodyStr)
            val isAuthReq = bodyStr.contains("bx_auth_serv") || ScheduleParser.isAuthRequired(bodyStr)
            val hasTimetable = bodyStr.contains("schedule-table") || bodyStr.contains("schedule-body") || bodyStr.contains("group-select")

            val (status, message, verdict) = when {
                !resp.isSuccessful -> {
                    Triple(
                        EndpointStatus.ERROR,
                        AppLocale.pick("Ошибка сервера: HTTP $responseCode", "Server error: HTTP $responseCode"),
                        AppLocale.pick("Сервер вернул код ошибки HTTP $responseCode (${resp.message})", "The server returned HTTP error code $responseCode (${resp.message})")
                    )
                }
                errText != null -> {
                    Triple(
                        EndpointStatus.DEGRADED,
                        AppLocale.pick("200 ОК, но сбой 1С: $errText", "200 OK, but a 1C failure: $errText"),
                        AppLocale.pick("Сервер отдал HTTP 200, но в теле страницы обнаружен текст ошибки 1С: «$errText»", "The server returned HTTP 200, but the page body contains a 1C error text: «$errText»")
                    )
                }
                hasTimetable -> {
                    Triple(
                        EndpointStatus.OK,
                        if (bodyStr.contains("schedule-table")) AppLocale.pick("200 ОК · Таблица расписания получена (${latency} мс)", "200 OK · Timetable fetched (${latency} ms)")
                        else AppLocale.pick("200 ОК · Расписание получено (нет пар на неделе) (${latency} мс)", "200 OK · Schedule fetched (no lessons this week) (${latency} ms)"),
                        if (bodyStr.contains("schedule-table")) AppLocale.pick("Сервер ответил штатно. В HTML обнаружен контейнер <table class=\"schedule-table\">", "The server responded normally. The HTML contains the <table class=\"schedule-table\"> container")
                        else AppLocale.pick("Сервер ответил штатно. В HTML обнаружен контейнер расписания (пар на выбранной неделе нет)", "The server responded normally. The schedule container was found (no lessons on the selected week)")
                    )
                }
                isAuthReq -> {
                    Triple(
                        EndpointStatus.AUTH_REQUIRED,
                        AppLocale.pick("200 ОК · Требуется вход, отдана форма авторизации (${latency} мс)", "200 OK · Sign-in required, the authorization form was served (${latency} ms)"),
                        AppLocale.pick("Сервер отдал форму входа (сессия не авторизована). Эндпоинт доступен, но требует входа", "The server served the login form (session not authorized). The endpoint is reachable but requires sign-in")
                    )
                }
                else -> {
                    Triple(
                        EndpointStatus.DEGRADED,
                        AppLocale.pick("200 ОК, но пустая страница (нет таблицы)", "200 OK, but the page is empty (no table)"),
                        AppLocale.pick("Сервер вернул 200 ОК, но маркер расписания не найден", "The server returned 200 OK, but the schedule marker was not found")
                    )
                }
            }

            val rawLog = buildDiagnosticRawLog(
                method = "GET",
                url = url,
                cookiesSent = getActiveCookies(),
                responseCode = responseCode,
                responseMessage = responseMsg,
                responseHeaders = responseHeaders,
                latencyMs = latency,
                bodySnippet = bodyStr,
                bodySize = bodyStr.toByteArray(Charsets.UTF_8).size,
                verdict = verdict,
                error = null
            )

            return EndpointHealthItem(
                id = "timetable",
                name = AppLocale.pick("РАСПИСАНИЕ", "SCHEDULE"),
                path = path,
                status = status,
                httpCode = responseCode,
                latencyMs = latency,
                message = message,
                rawLog = rawLog
            )
        } catch (e: Throwable) {
            val latency = System.currentTimeMillis() - start
            val isTimeout = e is java.net.SocketTimeoutException || e is java.io.InterruptedIOException
            val message = if (isTimeout) {
                AppLocale.pick("Таймаут соединения: сервер не ответил за 15 секунд", "Connection timeout: the server did not respond within 15 seconds")
            } else {
                AppLocale.pick(
                    "Нет связи с сервером (${e.localizedMessage ?: e.message ?: "сбой сети"})",
                    "No connection to the server (${e.localizedMessage ?: e.message ?: "network failure"})"
                )
            }
            val verdict = if (isTimeout) {
                AppLocale.pick("Сервер не успел ответить за отведенные 15 секунд (таймаут истек). Возможно, сервис 1С перегружен или завис.", "The server failed to respond within the allotted 15 seconds (timeout expired). The 1C service may be overloaded or hung.")
            } else {
                AppLocale.pick("Сетевой сбой при попытке соединения с сервером: ${e.javaClass.simpleName}", "Network failure while connecting to the server: ${e.javaClass.simpleName}")
            }

            val rawLog = buildDiagnosticRawLog(
                method = "GET",
                url = url,
                cookiesSent = getActiveCookies(),
                responseCode = responseCode,
                responseMessage = responseMsg,
                responseHeaders = responseHeaders,
                latencyMs = latency,
                bodySnippet = null,
                bodySize = 0,
                verdict = verdict,
                error = e
            )

            return EndpointHealthItem(
                id = "timetable",
                name = AppLocale.pick("РАСПИСАНИЕ", "SCHEDULE"),
                path = path,
                status = EndpointStatus.ERROR,
                latencyMs = latency,
                message = message,
                rawLog = rawLog
            )
        }
    }

    private fun checkFeedEndpoint(): EndpointHealthItem {
        val start = System.currentTimeMillis()
        val url = "https://eios.gukolomna.ru/eios/"
        val path = "/eios/"
        var responseCode: Int? = null
        var responseMsg: String? = null
        var responseHeaders: okhttp3.Headers? = null
        var bodyStr = ""

        try {
            val req = Request.Builder().url(url).build()
            val resp = diagnosticHttpClient.newCall(req).execute()
            val latency = System.currentTimeMillis() - start
            responseCode = resp.code
            responseMsg = resp.message
            responseHeaders = resp.headers
            bodyStr = resp.body?.string() ?: ""

            val (status, message, verdict) = when {
                !resp.isSuccessful -> {
                    Triple(
                        EndpointStatus.ERROR,
                        AppLocale.pick("Ошибка сервера: HTTP $responseCode", "Server error: HTTP $responseCode"),
                        AppLocale.pick("Сервер вернул ошибку HTTP $responseCode (${resp.message})", "The server returned HTTP error $responseCode (${resp.message})")
                    )
                }
                bodyStr.contains("errortext") -> {
                    val errText = ScheduleParser.extractErrorMessage(bodyStr) ?: AppLocale.pick("Ошибка Битрикс", "Bitrix error")
                    Triple(
                        EndpointStatus.DEGRADED,
                        AppLocale.pick("200 ОК, но ошибка: $errText", "200 OK, but an error occurred: $errText"),
                        AppLocale.pick("Сервер отдал HTTP 200, но обнаружена ошибка Битрикса: $errText", "The server returned HTTP 200, but a Bitrix error was detected: $errText")
                    )
                }
                bodyStr.contains("feed-post-block") || bodyStr.contains("feed-wrap") || bodyStr.contains("workarea") -> {
                    Triple(
                        EndpointStatus.OK,
                        AppLocale.pick("200 ОК · Живая лента доступна (${latency} мс)", "200 OK · Live Feed is available (${latency} ms)"),
                        AppLocale.pick("Сервер ответил штатно. Обнаружены блоки живой ленты", "The server responded normally. Live Feed blocks were detected")
                    )
                }
                bodyStr.contains("bx_auth_serv") || ScheduleParser.isAuthRequired(bodyStr) -> {
                    Triple(
                        EndpointStatus.AUTH_REQUIRED,
                        AppLocale.pick("200 ОК · Требуется вход, отдана форма авторизации (${latency} мс)", "200 OK · Sign-in required, the authorization form was served (${latency} ms)"),
                        AppLocale.pick("Сервер отдал форму входа (сессия не авторизована). Эндпоинт доступен, но требует кук", "The server served the login form (session not authorized). The endpoint is reachable but requires cookies")
                    )
                }
                else -> {
                    Triple(
                        EndpointStatus.OK,
                        AppLocale.pick("200 ОК · Страница получена (${latency} мс)", "200 OK · Page fetched (${latency} ms)"),
                        AppLocale.pick("Сервер вернул 200 ОК, тело страницы получено (${bodyStr.length} симв.)", "The server returned 200 OK, the page body was fetched (${bodyStr.length} chars)")
                    )
                }
            }

            val rawLog = buildDiagnosticRawLog(
                method = "GET",
                url = url,
                cookiesSent = getActiveCookies(),
                responseCode = responseCode,
                responseMessage = responseMsg,
                responseHeaders = responseHeaders,
                latencyMs = latency,
                bodySnippet = bodyStr,
                bodySize = bodyStr.toByteArray(Charsets.UTF_8).size,
                verdict = verdict,
                error = null
            )

            return EndpointHealthItem(
                id = "feed",
                name = AppLocale.pick("ЖИВАЯ ЛЕНТА", "LIVE FEED"),
                path = path,
                status = status,
                httpCode = responseCode,
                latencyMs = latency,
                message = message,
                rawLog = rawLog
            )
        } catch (e: Throwable) {
            val latency = System.currentTimeMillis() - start
            val isTimeout = e is java.net.SocketTimeoutException || e is java.io.InterruptedIOException
            val message = if (isTimeout) {
                AppLocale.pick("Таймаут соединения: сервер не ответил за 15 секунд", "Connection timeout: the server did not respond within 15 seconds")
            } else {
                AppLocale.pick(
                    "Нет связи с сервером (${e.localizedMessage ?: e.message ?: "сбой сети"})",
                    "No connection to the server (${e.localizedMessage ?: e.message ?: "network failure"})"
                )
            }
            val verdict = if (isTimeout) {
                AppLocale.pick("Сервер не успел ответить за отведенные 15 секунд (таймаут истек). Возможно, портал перегружен.", "The server failed to respond within the allotted 15 seconds (timeout expired). The portal may be overloaded.")
            } else {
                AppLocale.pick("Сетевой сбой при попытке соединения с сервером: ${e.javaClass.simpleName}", "Network failure while connecting to the server: ${e.javaClass.simpleName}")
            }

            val rawLog = buildDiagnosticRawLog(
                method = "GET",
                url = url,
                cookiesSent = getActiveCookies(),
                responseCode = responseCode,
                responseMessage = responseMsg,
                responseHeaders = responseHeaders,
                latencyMs = latency,
                bodySnippet = null,
                bodySize = 0,
                verdict = verdict,
                error = e
            )

            return EndpointHealthItem(
                id = "feed",
                name = AppLocale.pick("ЖИВАЯ ЛЕНТА", "LIVE FEED"),
                path = path,
                status = EndpointStatus.ERROR,
                latencyMs = latency,
                message = message,
                rawLog = rawLog
            )
        }
    }

    private fun checkCurriculumEndpoint(): EndpointHealthItem {
        val start = System.currentTimeMillis()
        val currid = _userProfile.value.currid
        val userId = _userProfile.value.userId
        val urlBuilder = StringBuilder("https://eios.gukolomna.ru/eios/contacts/curriculum/")
        val params = mutableListOf<String>()
        if (currid.isNotEmpty()) params.add("currid=$currid")
        if (userId.isNotEmpty()) params.add("user_id=$userId")
        if (params.isNotEmpty()) urlBuilder.append("?").append(params.joinToString("&"))
        val url = urlBuilder.toString()
        val path = if (params.isNotEmpty()) "/eios/contacts/curriculum/?${params.joinToString("&")}" else "/eios/contacts/curriculum/"
        var responseCode: Int? = null
        var responseMsg: String? = null
        var responseHeaders: okhttp3.Headers? = null
        var bodyStr = ""

        try {
            val req = Request.Builder().url(url).build()
            val resp = diagnosticHttpClient.newCall(req).execute()
            val latency = System.currentTimeMillis() - start
            responseCode = resp.code
            responseMsg = resp.message
            responseHeaders = resp.headers
            bodyStr = resp.body?.string() ?: ""

            val (status, message, verdict) = when {
                !resp.isSuccessful -> {
                    Triple(
                        EndpointStatus.ERROR,
                        AppLocale.pick("Ошибка сервера: HTTP $responseCode", "Server error: HTTP $responseCode"),
                        AppLocale.pick("Сервер вернул ошибку HTTP $responseCode (${resp.message})", "The server returned HTTP error $responseCode (${resp.message})")
                    )
                }
                bodyStr.contains("errortext") -> {
                    val errText = ScheduleParser.extractErrorMessage(bodyStr) ?: AppLocale.pick("Ошибка Битрикс", "Bitrix error")
                    Triple(
                        EndpointStatus.DEGRADED,
                        AppLocale.pick("200 ОК, но ошибка: $errText", "200 OK, but an error occurred: $errText"),
                        AppLocale.pick("Сервер отдал HTTP 200, но обнаружена ошибка Битрикса: $errText", "The server returned HTTP 200, but a Bitrix error was detected: $errText")
                    )
                }
                bodyStr.contains("bx_auth_serv") || ScheduleParser.isAuthRequired(bodyStr) -> {
                    Triple(
                        EndpointStatus.AUTH_REQUIRED,
                        AppLocale.pick("200 ОК · Требуется вход, отдана форма авторизации (${latency} мс)", "200 OK · Sign-in required, the authorization form was served (${latency} ms)"),
                        AppLocale.pick("Сервер отдал форму входа (сессия не авторизована). Эндпоинт доступен, но требует кук", "The server served the login form (session not authorized). The endpoint is reachable but requires cookies")
                    )
                }
                bodyStr.contains("curriculum") || bodyStr.contains("Семестр") || bodyStr.contains("таблица") || bodyStr.contains("table") -> {
                    Triple(
                        EndpointStatus.OK,
                        AppLocale.pick("200 ОК · Учебный план и БРС доступны (${latency} мс)", "200 OK · Curriculum and BRS are available (${latency} ms)"),
                        AppLocale.pick("Сервер ответил штатно. Обнаружены структуры учебного плана / БРС", "The server responded normally. Curriculum / BRS structures were detected")
                    )
                }
                else -> {
                    Triple(
                        EndpointStatus.OK,
                        AppLocale.pick("200 ОК · Страница получена (${latency} мс)", "200 OK · Page fetched (${latency} ms)"),
                        AppLocale.pick("Сервер вернул 200 ОК, тело страницы получено (${bodyStr.length} симв.)", "The server returned 200 OK, the page body was fetched (${bodyStr.length} chars)")
                    )
                }
            }

            val rawLog = buildDiagnosticRawLog(
                method = "GET",
                url = url,
                cookiesSent = getActiveCookies(),
                responseCode = responseCode,
                responseMessage = responseMsg,
                responseHeaders = responseHeaders,
                latencyMs = latency,
                bodySnippet = bodyStr,
                bodySize = bodyStr.toByteArray(Charsets.UTF_8).size,
                verdict = verdict,
                error = null
            )

            return EndpointHealthItem(
                id = "curriculum",
                name = AppLocale.pick("УСПЕВАЕМОСТЬ (БРС)", "CURRICULUM (BRS)"),
                path = path,
                status = status,
                httpCode = responseCode,
                latencyMs = latency,
                message = message,
                rawLog = rawLog
            )
        } catch (e: Throwable) {
            val latency = System.currentTimeMillis() - start
            val isTimeout = e is java.net.SocketTimeoutException || e is java.io.InterruptedIOException
            val message = if (isTimeout) {
                AppLocale.pick("Таймаут соединения: сервер не ответил за 15 секунд", "Connection timeout: the server did not respond within 15 seconds")
            } else {
                AppLocale.pick(
                    "Нет связи с сервером (${e.localizedMessage ?: e.message ?: "сбой сети"})",
                    "No connection to the server (${e.localizedMessage ?: e.message ?: "network failure"})"
                )
            }
            val verdict = if (isTimeout) {
                AppLocale.pick("Сервер не успел ответить за отведенные 15 секунд (таймаут истек). Возможно, портал перегружен.", "The server failed to respond within the allotted 15 seconds (timeout expired). The portal may be overloaded.")
            } else {
                AppLocale.pick("Сетевой сбой при попытке соединения с сервером: ${e.javaClass.simpleName}", "Network failure while connecting to the server: ${e.javaClass.simpleName}")
            }

            val rawLog = buildDiagnosticRawLog(
                method = "GET",
                url = url,
                cookiesSent = getActiveCookies(),
                responseCode = responseCode,
                responseMessage = responseMsg,
                responseHeaders = responseHeaders,
                latencyMs = latency,
                bodySnippet = null,
                bodySize = 0,
                verdict = verdict,
                error = e
            )

            return EndpointHealthItem(
                id = "curriculum",
                name = AppLocale.pick("УСПЕВАЕМОСТЬ (БРС)", "CURRICULUM (BRS)"),
                path = path,
                status = EndpointStatus.ERROR,
                latencyMs = latency,
                message = message,
                rawLog = rawLog
            )
        }
    }

    private fun buildDiagnosticRawLog(
        method: String,
        url: String,
        cookiesSent: String?,
        responseCode: Int?,
        responseMessage: String?,
        responseHeaders: okhttp3.Headers?,
        latencyMs: Long,
        bodySnippet: String?,
        bodySize: Int,
        verdict: String,
        error: Throwable?
    ): String {
        val httpUrl = url.toHttpUrl()
        val host = httpUrl.host
        return buildString {
            appendLine("═══════════════════════════════════════════════════════════")
            appendLine(AppLocale.pick(">>> HTTP-ЗАПРОС (REQUEST)", ">>> HTTP REQUEST"))
            appendLine("═══════════════════════════════════════════════════════════")
            appendLine("$method $url HTTP/1.1")
            appendLine("Host: $host")
            appendLine("User-Agent: $BROWSER_USER_AGENT")
            appendLine("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            appendLine("Accept-Language: ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            appendLine("Sec-CH-UA: \"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"")
            appendLine("Sec-CH-UA-Mobile: ?1")
            appendLine("Sec-CH-UA-Platform: \"Android\"")
            appendLine("Upgrade-Insecure-Requests: 1")
            if (!cookiesSent.isNullOrEmpty()) {
                appendLine("Cookie: $cookiesSent")
            } else {
                appendLine(AppLocale.pick("Cookie: [Сессия не авторизована]", "Cookie: [session not authorized]"))
            }
            appendLine()

            if (error != null) {
                appendLine("═══════════════════════════════════════════════════════════")
                appendLine(AppLocale.pick("<<< СБОЙ СЕТЕВОГО ЗАПРОСА (${latencyMs} мс)", "<<< NETWORK REQUEST FAILURE (${latencyMs} ms)"))
                appendLine("═══════════════════════════════════════════════════════════")
                appendLine(AppLocale.pick("Исключение: ${error.javaClass.name}", "Exception: ${error.javaClass.name}"))
                appendLine(AppLocale.pick("Сообщение: ${error.localizedMessage ?: error.message}", "Message: ${error.localizedMessage ?: error.message}"))
                if (error is java.net.SocketTimeoutException || error is java.io.InterruptedIOException) {
                    appendLine(AppLocale.pick("Таймаут: Превышен лимит ожидания ответа (15.000 сек).", "Timeout: the response wait limit was exceeded (15.000 s)."))
                }
                appendLine(AppLocale.pick("Стек ошибки:", "Error stack:"))
                error.stackTrace.take(6).forEach { st ->
                    appendLine("  at $st")
                }
                appendLine()
                appendLine(AppLocale.pick("ВЕРДИКТ:", "VERDICT:"))
                appendLine(verdict)
            } else if (responseCode != null) {
                appendLine("═══════════════════════════════════════════════════════════")
                appendLine(AppLocale.pick("<<< HTTP-ОТВЕТ (RESPONSE · HTTP $responseCode · ${latencyMs} мс)", "<<< HTTP RESPONSE (HTTP $responseCode · ${latencyMs} ms)"))
                appendLine("═══════════════════════════════════════════════════════════")
                appendLine("HTTP/1.1 $responseCode ${responseMessage ?: ""}")
                responseHeaders?.forEach { (name, value) ->
                    appendLine("$name: $value")
                }
                appendLine()
                appendLine("═══════════════════════════════════════════════════════════")
                appendLine(AppLocale.pick("ДИАГНОСТИЧЕСКИЙ АНАЛИЗ ТЕЛА ($bodySize байт)", "BODY DIAGNOSTIC ANALYSIS ($bodySize bytes)"))
                appendLine("═══════════════════════════════════════════════════════════")
                appendLine(AppLocale.pick("ВЕРДИКТ: $verdict", "VERDICT: $verdict"))
                if (!bodySnippet.isNullOrEmpty()) {
                    appendLine()
                    appendLine(AppLocale.pick("Фрагмент содержимого HTML (первые 400 симв.):", "HTML content fragment (first 400 chars):"))
                    val snippetClean = bodySnippet.take(400).replace("\r", "")
                    appendLine(snippetClean)
                }
            }
        }
    }
}
