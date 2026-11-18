package ru.nya.nyeios.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Приёмник сработавшего будильника: показывает уведомление и удаляет отработавшее
 * напоминание (оно одноразовое). Работа с файлом вынесена в фоновый поток через
 * [goAsync], чтобы не блокировать главный поток широковещательной доставки.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER_ALARM) return
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID).orEmpty().trim()
        if (reminderId.isEmpty()) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        Thread {
            try {
                val repository = RemindersRepository.getInstance(appContext)
                val reminder = repository.get(reminderId) ?: return@Thread
                // Одноразовое напоминание снимаем только после реально показанного
                // уведомления: иначе при выключенных уведомлениях оно бы молча
                // пропало, и пользователь не увидел бы его даже в списке.
                if (ReminderNotifications.show(appContext, reminder)) {
                    repository.delete(reminderId)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to deliver reminder $reminderId", t)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        private const val TAG = "ReminderAlarm"
        const val ACTION_REMINDER_ALARM: String = "ru.nya.nyeios.action.REMINDER_ALARM"
        const val EXTRA_REMINDER_ID: String = "ru.nya.nyeios.extra.REMINDER_ID"
    }
}
