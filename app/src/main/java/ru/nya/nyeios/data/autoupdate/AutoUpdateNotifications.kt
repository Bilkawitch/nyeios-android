package ru.nya.nyeios.data.autoupdate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ru.nya.nyeios.MainActivity
import ru.nya.nyeios.R
import ru.nya.nyeios.data.AppLocale
import ru.nya.nyeios.data.changes.CurriculumChanges
import ru.nya.nyeios.data.changes.ScheduleChanges

/** Стадии цикла автообновления; используются для прогресс-нотификации. */
enum class AutoUpdateStage { START, SCHEDULE, NEXT_WEEK, CURRICULUM, COMPARE }

/**
 * Уведомления подсистемы автообновления.
 *
 * Канал `auto_updates` намеренно `IMPORTANCE_DEFAULT` (не HIGH): это не будильник.
 * Прогресс — детерминированная нотификация с тем же id, что используется для
 * `ForegroundInfo`; итог — `InboxStyle` со списком изменений.
 */
object AutoUpdateNotifications {

    const val CHANNEL_ID = "auto_updates"
    const val PROGRESS_NOTIFICATION_ID = 7301
    const val RESULT_NOTIFICATION_ID = 7302

    /** Разложенный по полям прогресс: заголовок + текст стадии + процент. */
    data class Progress(val percent: Int, val stageText: String, val title: String)

    /** Процент/текст для стадии (чистая часть, без Android API). */
    fun progress(percent: Int, stage: AutoUpdateStage): Progress {
        val clamped = percent.coerceIn(0, 100)
        return Progress(
            percent = clamped,
            stageText = stageText(clamped, stage),
            title = AppLocale.pick("Обновление NyEIOS", "NyEIOS update")
        )
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            AppLocale.pick("Автообновление", "Auto-update"),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = AppLocale.pick(
                "Прогресс и изменения при автообновлении",
                "Progress and changes during auto-update"
            )
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Собирает (но не показывает) детерминированную прогресс-нотификацию.
     * Возвращаемое значение подходит и для [NotificationManagerCompat.notify],
     * и для `ForegroundInfo` — notificationId общий: [PROGRESS_NOTIFICATION_ID].
     */
    fun progressNotification(
        context: Context,
        percent: Int,
        stage: AutoUpdateStage
    ): Notification {
        ensureChannel(context)
        val progress = progress(percent, stage)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(progress.title)
            .setContentText(progress.stageText)
            .setProgress(100, progress.percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent(context, PROGRESS_NOTIFICATION_ID))
            .build()
    }

    /** Показать/обновить прогресс обычным способом (без foreground-service). */
    fun postProgress(context: Context, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(PROGRESS_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Разрешение отозвали между проверкой и показом.
        } catch (_: Exception) {
            // Показ уведомления не должен ронять фоновую работу.
        }
    }

    /** Итоговая нотификация со списком изменений (InboxStyle, не больше 6 строк). */
    fun showChanges(
        context: Context,
        schedule: ScheduleChanges,
        curriculum: CurriculumChanges
    ) {
        val lines = AutoUpdateNotificationText.summaryLines(schedule, curriculum)
        if (lines.isEmpty()) return
        ensureChannel(context)

        val title = AutoUpdateNotificationText.resultTitle(true)
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
        lines.forEach { style.addLine(it) }
        val openIntent = contentIntent(context, RESULT_NOTIFICATION_ID)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(style)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent)
            .addAction(0, AppLocale.pick("Открыть", "Open"), openIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(RESULT_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Разрешение отозвали между проверкой и показом.
        } catch (_: Exception) {
            // Итоговое уведомление — не критично для цикла.
        }
    }

    /** Убрать прогресс-нотификацию (и завершить foreground-режим). */
    fun cancelProgress(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun stageText(percent: Int, stage: AutoUpdateStage): String = when (stage) {
        AutoUpdateStage.START -> AppLocale.pick("Подготовка… $percent%", "Preparing… $percent%")
        AutoUpdateStage.SCHEDULE -> AppLocale.pick("Расписание… $percent%", "Schedule… $percent%")
        AutoUpdateStage.NEXT_WEEK -> AppLocale.pick("Следующая неделя… $percent%", "Next week… $percent%")
        AutoUpdateStage.CURRICULUM -> AppLocale.pick("Успеваемость… $percent%", "Grades… $percent%")
        AutoUpdateStage.COMPARE -> AppLocale.pick("Сравнение изменений… $percent%", "Comparing changes… $percent%")
    }

    private fun contentIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
