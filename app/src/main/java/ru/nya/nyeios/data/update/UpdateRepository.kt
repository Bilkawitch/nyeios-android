package ru.nya.nyeios.data.update

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.nya.nyeios.data.model.GithubAsset
import ru.nya.nyeios.data.model.GithubRelease
import ru.nya.nyeios.data.model.UpdateInfo
import java.util.concurrent.TimeUnit

class UpdateRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nyeios_update_prefs", Context.MODE_PRIVATE)

    private val localVersion: String by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager
                    .getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                    .versionName ?: "0.0.0"
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
            }
        } catch (e: Exception) { "0.0.0" }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Проверяет наличие нового релиза на GitHub.
     * Возвращает [UpdateInfo] если версия новее текущей, иначе null.
     * Throttle: не чаще раза в 24 часа.
     */
    fun checkForUpdate(forceCheck: Boolean = false): UpdateInfo? {
        val lastChecked = prefs.getLong(KEY_LAST_CHECKED, 0L)
        val now = System.currentTimeMillis()
        if (!forceCheck && now - lastChecked < THROTTLE_MS) return null

        return try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NyEIOS-Android/$localVersion")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
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

                if (!isNewer(release.tagName, localVersion)) return null

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
            null
        }
    }

    /**
     * Сравнивает версии в формате semver (x.y.z).
     * Возвращает true если [remote] > [local].
     */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = parseSemver(remote.trimStart('v', 'V'))
        val l = parseSemver(local.trimStart('v', 'V'))
        return r > l
    }

    private fun parseSemver(v: String): Triple<Int, Int, Int> {
        val parts = v.split(".").map { it.toIntOrNull() ?: 0 }
        return Triple(parts.getOrElse(0) { 0 }, parts.getOrElse(1) { 0 }, parts.getOrElse(2) { 0 })
    }

    private operator fun Triple<Int, Int, Int>.compareTo(other: Triple<Int, Int, Int>): Int {
        val c0 = first.compareTo(other.first)
        if (c0 != 0) return c0
        val c1 = second.compareTo(other.second)
        if (c1 != 0) return c1
        return third.compareTo(other.third)
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
        private const val THROTTLE_MS = 24 * 60 * 60 * 1000L // 24 hours

        @Volatile private var INSTANCE: UpdateRepository? = null

        fun getInstance(context: Context): UpdateRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: UpdateRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
