package ru.nya.nyeios.data.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ru.nya.nyeios.MainActivity
import ru.nya.nyeios.R

/**
 * Показ уведомления о сработавшем напоминании.
 *
 * Вынесено из [ReminderAlarmReceiver], чтобы один и тот же код могли использовать
 * и будильник, и (в будущем) ручной повтор. При выключенных уведомлениях молча
 * возвращает false: напоминание не должно ронять процесс.
 */
object ReminderNotifications {

    const val EXTRA_REMINDER_ID: String = "ru.nya.nyeios.extra.reminder_id"

    fun show(context: Context, reminder: Reminder): Boolean {
        if (!ReminderPermissions.notificationsEnabled(context)) return false
        ReminderPermissions.ensureNotificationChannel(context)

        val title = ReminderFormatter.notificationTitle(reminder)
        val body = ReminderFormatter.notificationBody(reminder)
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId(reminder.id),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_REMINDER_ID, reminder.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderPermissions.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(reminder.triggerAtUtcMs)
            .setShowWhen(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId(reminder.id), notification)
            true
        } catch (_: SecurityException) {
            // Разрешение отозвали между проверкой и показом.
            false
        } catch (_: Exception) {
            false
        }
    }

    fun notificationId(reminderId: String): Int = ("reminder:$reminderId").hashCode() and 0x7FFFFFFF
}
