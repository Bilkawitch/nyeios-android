package ru.nya.nyeios.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
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
import ru.nya.nyeios.data.model.WeekSchedule
import ru.nya.nyeios.data.parser.ScheduleParser
import java.io.File
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

    private val okHttpClient: OkHttpClient by lazy {
        createOkHttpClient()
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

    suspend fun login(username: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        try {
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
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) NyEIOS/0.0.1")
                .post(formBody)
                .build()

            // We make the call with client that handles cookies automatically,
            // and we also inspect ALL responses in the redirect chain (both final and priorResponse)
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
                Result.success("Успешный вход")
            } else {
                // Parse HTML response for specific Bitrix error text if present
                val html = response.body?.string().orEmpty()
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
            .apply()

        cookieStore.clear()
        _authSession.value = AuthSession()
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
        val session = _authSession.value
        val cookies = session.cookies.ifEmpty {
            val u = securePrefs.getString("user_login", "") ?: ""
            val p = securePrefs.getString("user_password", "") ?: ""
            if (u.isNotEmpty() && p.isNotEmpty()) {
                val loginRes = login(u, p)
                if (loginRes.isSuccess) {
                    _authSession.value.cookies
                } else ""
            } else ""
        }

        val url = "https://eios.gukolomna.ru/eios/contacts/timetable/?startDate=$startD&endDate=$endD"
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) NyEIOS/0.0.1")

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
            val schedule = ScheduleParser.parse(html, offsetWeeks, startD, endD)

            if (schedule != null) {
                try {
                    cacheFile.writeText(gson.toJson(schedule))
                } catch (e: Exception) {
                    // Ignore cache write error
                }
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
}
