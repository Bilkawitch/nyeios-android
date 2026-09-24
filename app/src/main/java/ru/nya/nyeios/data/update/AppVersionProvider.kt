package ru.nya.nyeios.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import ru.nya.nyeios.BuildConfig

/**
 * Поставщик актуальной версии приложения во время выполнения (Runtime).
 *
 * ## Почему это необходимо:
 * `BuildConfig.VERSION_NAME` в байткоде Kotlin компилируется как строковый литерал (LDC),
 * так как в Java-классе `BuildConfig` это константа `public static final String`.
 * При инкрементальной компиляции Kotlin Gradle Plugin (KGP), если меняется только
 * `versionName` в `build.gradle.kts`, сигнатуры (ABI) `BuildConfig.java` не меняются,
 * и KGP может посчитать файлы `MainActivity.kt` и `UpdateRepository.kt` актуальными (UP-TO-DATE),
 * оставив в готовом APK старую захардкоженную версию в байткоде классов.
 *
 * `AppVersionProvider` обращается напрямую к системному [PackageManager], извлекая `versionName`
 * из финального скомпилированного `AndroidManifest.xml` установленного APK.
 */
object AppVersionProvider {

    @Volatile
    private var cachedVersionName: String? = null

    fun getVersionName(context: Context): String {
        cachedVersionName?.let { return it }
        val ver = try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            BuildConfig.VERSION_NAME
        }
        cachedVersionName = ver
        return ver
    }
}
