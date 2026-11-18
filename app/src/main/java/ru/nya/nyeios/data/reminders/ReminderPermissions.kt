package ru.nya.nyeios.data.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.nya.nyeios.data.AppLocale

/** Состояние разрешений, влияющих на доставку напоминаний. */
data class ReminderSetupStatus(
    val notificationsEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val batteryOptimizationIgnored: Boolean
) {
    val hasIssues: Boolean
        get() = !notificationsEnabled || !exactAlarmsAllowed || !batteryOptimizationIgnored
}

/**
 * Разрешения и системные экраны, от которых зависит доставка напоминаний:
 * уведомления (API 33+), точные будильники (API 31+) и оптимизация батареи.
 */
object ReminderPermissions {

    const val CHANNEL_ID: String = "homework_reminders"

    fun ensureNotificationChannel(context: Context): Unit {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            AppLocale.pick("Напоминания о ДЗ", "Homework reminders"),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = AppLocale.pick(
                "Напоминания о домашних заданиях к парам",
                "Reminders about homework for lessons"
            )
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun notificationsEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun setupStatus(context: Context): ReminderSetupStatus = ReminderSetupStatus(
        notificationsEnabled = notificationsEnabled(context),
        exactAlarmsAllowed = canScheduleExactAlarms(context),
        batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context)
    )

    /** Открывает системный экран «Будильники и напоминания». false — экран недоступен. */
    fun openExactAlarmSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                packageUri(context)
            )
            if (startSafely(context, intent)) return true
        }
        return openAppDetailsSettings(context)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (!startSafely(context, intent)) {
            openAppDetailsSettings(context)
        }
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        if (!startSafely(context, intent)) {
            openAppDetailsSettings(context)
        }
    }

    /** Прямой запрос исключения из оптимизации; при недоступности — общий экран настроек. */
    fun requestBatteryOptimizationExemption(context: Context) {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            packageUri(context)
        )
        if (!startSafely(context, intent)) {
            openBatteryOptimizationSettings(context)
        }
    }

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

    private fun openAppDetailsSettings(context: Context): Boolean {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            packageUri(context)
        )
        return startSafely(context, intent)
    }

    private fun startSafely(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
