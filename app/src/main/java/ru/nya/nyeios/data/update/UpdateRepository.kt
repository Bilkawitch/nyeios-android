package ru.nya.nyeios.data.update

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.nya.nyeios.BuildConfig
import ru.nya.nyeios.data.model.GithubAsset
import ru.nya.nyeios.data.model.GithubRelease
import ru.nya.nyeios.data.model.UpdateInfo
import ru.nya.nyeios.data.net.NetworkLogger
import java.util.concurrent.TimeUnit

internal data class VersionPart(val num: Int, val suffix: String) : Comparable<VersionPart> {
    override fun compareTo(other: VersionPart): Int {
        val c = num.compareTo(other.num)
        if (c != 0) return c
        return suffix.compareTo(other.suffix)
    }
}

internal data class AppVersion(val parts: List<VersionPart>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val maxLen = maxOf(parts.size, other.parts.size)
        for (i in 0 until maxLen) {
            val p1 = parts.getOrElse(i) { VersionPart(0, "") }
            val p2 = other.parts.getOrElse(i) { VersionPart(0, "") }
            val c = p1.compareTo(p2)
            if (c != 0) return c
        }
        return 0
    }

    companion object {
        fun parse(version: String): AppVersion {
            val clean = version.trim().trimStart('v', 'V')
            val segments = clean.split('.').map { seg ->
                val digits = seg.takeWhile { it.isDigit() }
                val suffix = seg.drop(digits.length)
                VersionPart(digits.toIntOrNull() ?: 0, suffix)
            }
            return AppVersion(segments)
        }
    }
}

class UpdateRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nyeios_update_prefs", Context.MODE_PRIVATE)

    private val localVersion: String = BuildConfig.VERSION_NAME

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Проверяет наличие нового релиза на GitHub.
     * Возвращает [UpdateInfo] если версия новее текущей, иначе null.
     *
     * Политика проверки:
     * - Проверка выполняется при каждом запуске приложения с нуля (открытие после закрытия).
     * - Защита от спама: минимальный интервал между запросами [MIN_INTERVAL_MS] (15 сек).
     * - Лимит GitHub API: 60 запросов/час для неавторизованных IP. Отслеживается по заголовкам
     *   `x-ratelimit-remaining` и `x-ratelimit-reset`. Пока лимит не исчерпан — проверяет всегда.
     */
    fun checkForUpdate(forceCheck: Boolean = false): UpdateInfo? {
        val now = System.currentTimeMillis()
        val lastChecked = prefs.getLong(KEY_LAST_CHECKED, 0L)
        val resetMs = prefs.getLong(KEY_RATELIMIT_RESET_MS, 0L)
        val remaining = prefs.getInt(KEY_RATELIMIT_REMAINING, 60)

        if (!forceCheck) {
            // Защита от дублирующих запросов при пересоздании активности
            if (now - lastChecked < MIN_INTERVAL_MS) {
                return null
            }

            // Проверка лимита GitHub API: если окно ещё не сбросилось и запросы исчерпаны
            if (now < resetMs && remaining <= 1) {
                val minsLeft = maxOf(1, ((resetMs - now) / 60000).toInt())
                NetworkLogger.logInfo(
                    tag = "UPDATE",
                    message = "Лимит GitHub API (60/час) исчерпан",
                    details = "Автопроверка отложена. Сброс лимита через ~$minsLeft мин."
                )
                return null
            }
        }

        return try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NyEIOS-Android/$localVersion")
                .build()

            client.newCall(request).execute().use { response ->
                val remHeader = response.header("x-ratelimit-remaining")?.toIntOrNull()
                val resetHeader = response.header("x-ratelimit-reset")?.toLongOrNull()

                if (remHeader != null && resetHeader != null) {
                    prefs.edit()
                        .putInt(KEY_RATELIMIT_REMAINING, remHeader)
                        .putLong(KEY_RATELIMIT_RESET_MS, resetHeader * 1000L)
                        .apply()
                }

                if (response.code == 403 && (remHeader == 0 || response.header("x-ratelimit-remaining") == "0")) {
                    val minsLeft = if (resetHeader != null) maxOf(1, ((resetHeader * 1000L - now) / 60000).toInt()) else 60
                    NetworkLogger.logError(
                        tag = "UPDATE",
                        message = "Лимит GitHub API (60/час) исчерпан (HTTP 403)",
                        details = "Сброс лимита через ~$minsLeft мин."
                    )
                    return null
                }

                if (!response.isSuccessful) {
                    NetworkLogger.logError(
                        tag = "UPDATE",
                        message = "GitHub API вернул HTTP ${response.code}",
                        details = "URL: $RELEASES_URL"
                    )
                    return null
                }

                val body = response.body?.string() ?: return null
                prefs.edit().putLong(KEY_LAST_CHECKED, now).apply()

                val dto = gson.fromJson(body, GithubReleaseDto::class.java) ?: return null
                val release = GithubRelease(
                    tagName = dto.tagName ?: return null,
                    body = dto.body.orEmpty(),
                    assets = dto.assets?.map {
                        GithubAsset(
                            name = it.name.orEmpty(),
                            browserDownloadUrl = it.browserDownloadUrl.orEmpty(),
                            size = it.size ?: 0L
                        )
                    } ?: emptyList()
                )

                if (!isNewer(release.tagName, localVersion)) {
                    val remainingText = if (remHeader != null) " (осталось $remHeader/60 зап/ч)" else ""
                    NetworkLogger.logInfo(
                        tag = "UPDATE",
                        message = "Обновлений нет$remainingText",
                        details = "Текущая: $localVersion, последняя: ${release.tagName}"
                    )
                    return null
                }

                val apkAsset = release.assets.firstOrNull {
                    it.name.startsWith("NyEIOS-") && it.name.endsWith(".apk")
                } ?: return null

                UpdateInfo(
                    version = release.tagName.trimStart('v', 'V'),
                    changelog = release.body.lines().take(3).joinToString("\n").trim(),
                    apkUrl = apkAsset.browserDownloadUrl,
                    apkSize = apkAsset.size
                )
            }
        } catch (e: Exception) {
            NetworkLogger.logError(
                tag = "UPDATE",
                message = "Ошибка при проверке обновлений",
                error = e
            )
            null
        }
    }

    /**
     * Сравнивает версии с поддержкой семантического версионирования и буквенных суффиксов
     * (например: 0.1.2a > 0.1.2, 0.1.2b > 0.1.2a, 0.1.3 > 0.1.2a).
     * Возвращает true если [remote] новее [local].
     */
    fun isNewer(remote: String, local: String): Boolean {
        return AppVersion.parse(remote) > AppVersion.parse(local)
    }

    // ── DTO ──────────────────────────────────────────────────────────────────

    private data class GithubReleaseDto(
        @SerializedName("tag_name") val tagName: String?,
        @SerializedName("body") val body: String?,
        @SerializedName("assets") val assets: List<GithubAssetDto>?
    )

    private data class GithubAssetDto(
        @SerializedName("name") val name: String?,
        @SerializedName("browser_download_url") val browserDownloadUrl: String?,
        @SerializedName("size") val size: Long?
    )

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/Bilkawitch/nyeios-android/releases/latest"
        private const val KEY_LAST_CHECKED = "last_checked_at"
        private const val KEY_RATELIMIT_REMAINING = "ratelimit_remaining"
        private const val KEY_RATELIMIT_RESET_MS = "ratelimit_reset_ms"
        private const val MIN_INTERVAL_MS = 15_000L // 15 seconds cooldown

        @Volatile private var INSTANCE: UpdateRepository? = null

        fun getInstance(context: Context): UpdateRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: UpdateRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
