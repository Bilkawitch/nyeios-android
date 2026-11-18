package ru.nya.nyeios.data.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Восстановление будильников после перезагрузки, обновления приложения,
 * смены времени/часового пояса и выдачи разрешения на точные будильники.
 * Перепланирование идёт через [RemindersRepository], поэтому состояние
 * поднимается с диска тем же путём, что и в обычной работе.
 */
class ReminderRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in supportedActions()) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        Thread {
            try {
                RemindersRepository.getInstance(appContext).rescheduleAll()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to restore reminders after $action", t)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        private const val TAG = "ReminderRestore"

        private val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )

        fun supportedActions(): Set<String> = ACTIONS
    }
}
