package ru.nya.nyeios.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Постановка системных будильников и показ уведомлений.
 *
 * На Android 12+ точные будильники требуют special app access «Будильники и напоминания»;
 * при отказе напоминание ставится неточным `setAndAllowWhileIdle`, а UI сообщает о сдвиге.
 */
object ReminderScheduler {

    fun schedule(context: Context, reminder: Reminder): ReminderScheduleResult {
        val appContext = context.applicationContext
        ReminderPermissions.ensureNotificationChannel(appContext)

        val triggerAtUtcMs = reminder.triggerAtUtcMs
        val exactAllowed = ReminderPermissions.canScheduleExactAlarms(appContext)
        val preferredMode = ReminderSchedulePolicy.modeFor(exactAllowed)

        if (triggerAtUtcMs <= System.currentTimeMillis()) {
            // Прошедший момент будильник не ставим, но старый могли оставить при правке.
            cancel(appContext, reminder.id)
            return ReminderScheduleResult(scheduled = false, mode = preferredMode, triggerAtUtcMs = triggerAtUtcMs)
        }

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return ReminderScheduleResult(scheduled = false, mode = preferredMode, triggerAtUtcMs = triggerAtUtcMs)

        val pendingIntent = alarmPendingIntent(appContext, reminder.id)
        return try {
            setAlarm(alarmManager, preferredMode, triggerAtUtcMs, pendingIntent)
            ReminderScheduleResult(scheduled = true, mode = preferredMode, triggerAtUtcMs = triggerAtUtcMs)
        } catch (_: SecurityException) {
            // Разрешение могли отозвать между проверкой и постановкой — падаем в неточный режим.
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtUtcMs, pendingIntent)
                ReminderScheduleResult(scheduled = true, mode = ReminderAlarmMode.INEXACT, triggerAtUtcMs = triggerAtUtcMs)
            } catch (_: Exception) {
                ReminderScheduleResult(scheduled = false, mode = ReminderAlarmMode.INEXACT, triggerAtUtcMs = triggerAtUtcMs)
            }
        } catch (_: Exception) {
            ReminderScheduleResult(scheduled = false, mode = preferredMode, triggerAtUtcMs = triggerAtUtcMs)
        }
    }

    fun cancel(context: Context, reminderId: String) {
        if (reminderId.isBlank()) return
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = alarmPendingIntent(appContext, reminderId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /** Возвращает количество поставленных будильников (только будущие). */
    fun rescheduleAll(context: Context, reminders: List<Reminder>): Int {
        val appContext = context.applicationContext
        ReminderPermissions.ensureNotificationChannel(appContext)
        val now = System.currentTimeMillis()
        return ReminderSchedulePolicy.futureReminders(reminders, now).count { reminder ->
            schedule(appContext, reminder).scheduled
        }
    }

    private fun setAlarm(
        alarmManager: AlarmManager,
        mode: ReminderAlarmMode,
        triggerAtUtcMs: Long,
        pendingIntent: PendingIntent
    ) {
        when (mode) {
            ReminderAlarmMode.EXACT ->
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtUtcMs, pendingIntent)
            ReminderAlarmMode.INEXACT ->
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtUtcMs, pendingIntent)
        }
    }

    private fun alarmPendingIntent(context: Context, reminderId: String): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REMINDER_ALARM
            data = Uri.parse("nyeios://reminders/$reminderId")
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
