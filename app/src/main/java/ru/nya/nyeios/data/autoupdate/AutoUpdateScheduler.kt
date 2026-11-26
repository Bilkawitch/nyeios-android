package ru.nya.nyeios.data.autoupdate

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Планировщик периодического автообновления через WorkManager.
 *
 * Никаких AlarmManager/точных будильников: периодичность — системная, с учётом Doze.
 * Ограничения сети дублируют политику [AutoUpdateNetworkPolicy], но окончательное решение
 * (включая MOBILE_ONLY при активном Wi-Fi) принимает сам воркер.
 */
object AutoUpdateScheduler {

    const val UNIQUE_WORK_NAME = "nyeios_auto_update"
    private const val WORK_TAG = "nyeios_auto_update"

    /** enabled -> (пере)поставить периодическую работу, иначе — отменить её. */
    fun sync(context: Context, config: AutoUpdateConfig) {
        if (!config.enabled) {
            cancel(context)
            return
        }
        val normalized = config.normalized()
        try {
            val workManager = WorkManager.getInstance(context.applicationContext)
            val requiredNetwork = if (normalized.network == AutoUpdateNetwork.WIFI_ONLY) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(requiredNetwork)
                .build()
            val request =
                PeriodicWorkRequestBuilder<AutoUpdateWorker>(
                    normalized.intervalHours.toLong(),
                    TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .addTag(WORK_TAG)
                    .build()
            // UPDATE (WM 2.8+) обновляет интервал/ограничения существующей работы, не теряя её.
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } catch (_: Exception) {
            // Планировщик может быть недоступен (например, WorkManager отключён в тестах/на
            // устройстве с ограничениями) — настройки всё равно сохранены.
        }
    }

    fun cancel(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        } catch (_: Exception) {
            // ignore
        }
    }

    /** Псевдоним [sync] по контракту: перепланировать следующее обновление. */
    fun scheduleNext(context: Context, config: AutoUpdateConfig) = sync(context, config)
}
