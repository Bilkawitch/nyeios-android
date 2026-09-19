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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
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
            // Fallback for devices with keystore issues
            context.getSharedPreferences("nyeios_secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val _authSession = MutableStateFlow(loadAuthSession())
    val authSession: StateFlow<AuthSession> = _authSession.asStateFlow()

    private val okHttpClient: OkHttpClient by lazy {
        createOkHttpClient()
    }

    private fun createOkHttpClient(): OkHttpClient {
        // We configure a trust manager to ensure connection succeeds even if eios.gukolomna.ru
        // misses the intermediate CA certificate in TLS handshake.
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
            .build()
    }

    private fun loadAuthSession(): AuthSession {
        val username = securePrefs.getString("user_login", "") ?: ""
        val cookies = securePrefs.getString("user_cookies", "") ?: ""
        val isLoggedIn = cookies.contains("BITRIX_SM_UIDH") || cookies.contains("BITRIX_SM_LOGIN")
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
                .add("backurl", "/eios/")
                .add("USER_LOGIN", username)
                .add("USER_PASSWORD", pass)
                .add("USER_REMEMBER", "Y")
                .add("Login", "Войти")
                .build()

            val request = Request.Builder()
                .url(loginUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) NyEIOS/0.0.1")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(formBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val cookieHeaders = response.headers("Set-Cookie")
            val cookiesMap = mutableMapOf<String, String>()

            for (h in cookieHeaders) {
                val pair = h.split(";").firstOrNull()?.split("=", limit = 2)
                if (pair != null && pair.size == 2) {
                    cookiesMap[pair[0].trim()] = pair[1].trim()
                }
            }

            val cookieStr = cookiesMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
            val hasSession = cookiesMap.containsKey("BITRIX_SM_UIDH") || cookiesMap.containsKey("BITRIX_SM_LOGIN")

            if (hasSession) {
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
                Result.failure(Exception("Неверный логин или пароль портала ЭИОС"))
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
            // Check if credentials exist in prefs to auto-login
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
                // Return cache if available on failure
                if (cacheFile.exists()) {
                    val cached = gson.fromJson(cacheFile.readText(), WeekSchedule::class.java)
                    if (cached != null) return@withContext Result.success(cached.copy(isCached = true))
                }
                return@withContext Result.failure(Exception("Ошибка сервера: HTTP ${response.code}"))
            }

            val html = response.body?.string() ?: ""
            val schedule = ScheduleParser.parse(html, offsetWeeks, startD, endD)

            if (schedule != null) {
                // Save to cache
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
