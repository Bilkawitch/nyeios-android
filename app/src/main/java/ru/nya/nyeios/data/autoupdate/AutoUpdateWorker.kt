package ru.nya.nyeios.data.autoupdate

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import ru.nya.nyeios.data.reminders.ReminderPermissions
import ru.nya.nyeios.data.repository.EiosRepository

/**
 * Периодический воркер автообновления.
 *
 * Быстрые отказы (`Result.success()` без работы, чтобы не будить систему зря):
 * автообновление выключено, пользователь не залогинен, текущая сеть не проходит политику.
 * Сетевые ошибки цикла дают максимум один `Result.retry()`.
 */
class AutoUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        val config = AutoUpdateSettingsStore.getInstance(context).config.value
        Log.i(TAG, "doWork start enabled=${config.enabled} interval=${config.intervalHours}h network=${config.network} notifications=${config.notificationsEnabled} attempt=$runAttemptCount")
        if (!config.enabled) {
            Log.i(TAG, "skip: disabled")
            return Result.success()
        }

        val repository = EiosRepository.getInstance(context)
        if (!repository.isUserLoggedIn()) {
            Log.i(TAG, "skip: not logged in")
            return Result.success()
        }

        val connectivity = AutoUpdateNetworkState.current(context)
        Log.i(TAG, "connectivity=$connectivity")
        if (!AutoUpdateNetworkPolicy.isAllowed(config.network, connectivity)) {
            Log.i(TAG, "skip: network policy")
            return Result.success()
        }

        // FGS включаем только когда уведомления действительно разрешены: иначе обычная работа.
        val notificationsAllowed = config.notificationsEnabled &&
            ReminderPermissions.notificationsEnabled(context)

        val foregroundSink: (suspend (Notification) -> Boolean)? = if (notificationsAllowed) {
            { notification -> trySetForeground(notification) }
        } else {
            null
        }

        val result = AutoUpdateRunner.run(
            context = context,
            withNotifications = notificationsAllowed,
            foregroundSink = foregroundSink
        )
        Log.i(
            TAG,
            "doWork finish ran=${result.ran} skip=${result.skippedReason} " +
                "schedule=${result.scheduleChanges.totalChanges} scores=${result.curriculumChanges.totalChanges} " +
                "error=${result.errorMessage}"
        )

        return if (result.errorMessage != null && runAttemptCount < 1) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private companion object {
        const val TAG = "NyEIOS-AutoUpdate"
    }

    /**
     * Пытается перевести воркер в foreground с dataSync-нотификацией прогресса.
     * На Android 12+ запуск FGS из фона может быть запрещён, на Android 14 нужны
     * разрешение и объявленный тип — любая ошибка означает «работаем без FGS».
     */
    private suspend fun trySetForeground(notification: Notification): Boolean {
        return try {
            setForeground(
                ForegroundInfo(
                    AutoUpdateNotifications.PROGRESS_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
