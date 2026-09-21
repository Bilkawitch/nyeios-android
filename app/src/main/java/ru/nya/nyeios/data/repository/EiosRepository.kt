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
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import ru.nya.nyeios.data.model.AuthSession
import ru.nya.nyeios.data.model.CurriculumTerm
import ru.nya.nyeios.data.model.FeedPost
import ru.nya.nyeios.data.model.UserProfile
import ru.nya.nyeios.data.model.WeekSchedule
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
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
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
                chain.proceed(builder.build())
            }
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val hostKey = url.host
                    val existing = cookieStore.getOrPut(hostKey) { mutableListOf() }
                    for (c in cookies) {
                        existing.removeAll { it.name == c.name }
                        existing.add(c)
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

    suspend fun login(username: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val loginUrl = "https://eios.gukolomna.ru/index.php?login=yes"
            val formBody = FormBody.Builder()
                .add("AUTH_FORM", "Y")
                .add("TYPE", "AUTH")
                .add("backurl", "/eios/")
                .add("USER_LOGIN", username)
                .add("USER_PASSWORD", pass)
                .add("USER_REMEMBER", "Y")
                .build()

            val request = Request.Builder()
                .url(loginUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .post(formBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            // Collect all Set-Cookie headers from the entire chain
            val collectedCookies = mutableMapOf<String, String>()
            var chainResponse: Response? = response
            while (chainResponse != null) {
                for (h in chainResponse.headers("Set-Cookie")) {
                    val pair = h.split(";").firstOrNull()?.split("=", limit = 2)
                    if (pair != null && pair.size == 2) {
                        collectedCookies[pair[0].trim()] = pair[1].trim()
                    }
                }
                chainResponse = chainResponse.priorResponse
            }

            // Also check cookies in CookieJar
            val httpUrl = "https://eios.gukolomna.ru/".toHttpUrl()
            val jarCookies = okHttpClient.cookieJar.loadForRequest(httpUrl)
            for (c in jarCookies) {
                collectedCookies[c.name] = c.value
            }

            val hasSession = collectedCookies.containsKey("BITRIX_SM_UIDH") ||
                    collectedCookies.containsKey("BITRIX_SM_LOGIN")

            val html = response.body?.string().orEmpty()
            if (hasSession) {
                val cookieStr = collectedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                securePrefs.edit()
                    .putString("user_login", username)
                    .putString("user_password", pass)
                    .putString("user_cookies", cookieStr)
                    .apply()

                val newSession = AuthSession(
                    username = username,
                    cookies = cookieStr,
                    isLoggedIn = true,
                    authSource = "saved"
                )
                _authSession.value = newSession

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

                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Сетевая ошибка при авторизации: ${e.localizedMessage ?: e.message}"))
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
                    return@withContext Result.success(cached.copy(isCached = true))
                }
            } catch (e: Exception) {
                // Ignore corrupt cache
            }
        }

        // 2. Fetch from network
        val cookies = getActiveCookies()
        val url = "https://eios.gukolomna.ru/eios/contacts/timetable/?startDate=$startD&endDate=$endD"
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)

        if (cookies.isNotEmpty()) {
            reqBuilder.header("Cookie", cookies)
        }

        try {
            val response = okHttpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) {
                if (cacheFile.exists()) {
                    val cached = gson.fromJson(cacheFile.readText(), WeekSchedule::class.java)
                    if (cached != null) return@withContext Result.success(cached.copy(isCached = true))
                }
                return@withContext Result.failure(Exception("Ошибка сервера: HTTP ${response.code}"))
            }

            val html = response.body?.string() ?: ""
            updateProfileIfFound(html)

            val schedule = ScheduleParser.parse(html, offsetWeeks, startD, endD)
            if (schedule != null) {
                try {
                    cacheFile.writeText(gson.toJson(schedule))
                } catch (e: Exception) {
                    // Ignore cache write error
                }
                updateLastSyncTime()
                Result.success(schedule)
            } else {
                if (cacheFile.exists()) {
                    val cached = gson.fromJson(cacheFile.readText(), WeekSchedule::class.java)
                    if (cached != null) return@withContext Result.success(cached.copy(isCached = true))
                }
                Result.failure(Exception("Не удалось разобрать расписание. Возможно, требуется авторизация."))
            }
        } catch (e: Exception) {
            if (cacheFile.exists()) {
                val cached = gson.fromJson(cacheFile.readText(), WeekSchedule::class.java)
                if (cached != null) return@withContext Result.success(cached.copy(isCached = true))
            }
            Result.failure(Exception("Сетевая ошибка: ${e.localizedMessage ?: e.message}"))
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

        // 2. Fetch from network
        val cookies = getActiveCookies()
        val userId = _userProfile.value.userId
        val url = if (userId.isNotEmpty()) {
            "https://eios.gukolomna.ru/eios/contacts/personal/user/$userId/"
        } else {
            "https://eios.gukolomna.ru/eios/"
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)

        if (cookies.isNotEmpty()) {
            reqBuilder.header("Cookie", cookies)
        }

        try {
            val response = okHttpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) {
                if (cacheFile.exists()) {
                    val cached: List<FeedPost>? = gson.fromJson(cacheFile.readText(), listType)
                    if (!cached.isNullOrEmpty()) return@withContext Result.success(cached)
                }
                return@withContext Result.failure(Exception("Ошибка сервера ленты: HTTP ${response.code}"))
            }

            val html = response.body?.string() ?: ""
            updateProfileIfFound(html)

            val posts = FeedParser.parse(html)
            if (posts.isNotEmpty()) {
                try {
                    cacheFile.writeText(gson.toJson(posts))
                } catch (e: Exception) {
                    // Ignore cache write error
                }
                updateLastSyncTime()
                Result.success(posts)
            } else {
                if (cacheFile.exists()) {
                    val cached: List<FeedPost>? = gson.fromJson(cacheFile.readText(), listType)
                    if (!cached.isNullOrEmpty()) return@withContext Result.success(cached)
                }
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            if (cacheFile.exists()) {
                val cached: List<FeedPost>? = gson.fromJson(cacheFile.readText(), listType)
                if (!cached.isNullOrEmpty()) return@withContext Result.success(cached)
            }
            Result.failure(Exception("Сетевая ошибка при загрузке ленты: ${e.localizedMessage ?: e.message}"))
        }
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

        // 2. Fetch from network
        val cookies = getActiveCookies()
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

        if (cookies.isNotEmpty()) {
            reqBuilder.header("Cookie", cookies)
        }

        try {
            val response = okHttpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) {
                if (cacheFile.exists()) {
                    val cached: List<CurriculumTerm>? = gson.fromJson(cacheFile.readText(), listType)
                    if (!cached.isNullOrEmpty()) return@withContext Result.success(cached)
                }
                return@withContext Result.failure(Exception("Ошибка сервера БРС: HTTP ${response.code}"))
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
                    if (!cached.isNullOrEmpty()) return@withContext Result.success(cached)
                }
                Result.failure(Exception("Не удалось загрузить успеваемость. Проверьте авторизацию."))
            }
        } catch (e: Exception) {
            if (cacheFile.exists()) {
                val cached: List<CurriculumTerm>? = gson.fromJson(cacheFile.readText(), listType)
                if (!cached.isNullOrEmpty()) return@withContext Result.success(cached)
            }
            Result.failure(Exception("Сетевая ошибка успеваемости: ${e.localizedMessage ?: e.message}"))
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
