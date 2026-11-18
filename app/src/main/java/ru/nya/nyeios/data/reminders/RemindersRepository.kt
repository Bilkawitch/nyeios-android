package ru.nya.nyeios.data.reminders

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Единая точка доступа к напоминаниям для UI.
 *
 * Репозиторий держит состояние в памяти, персистит его через [ReminderStore]
 * и после каждого изменения переставляет системный будильник через [ReminderScheduler].
 */
class RemindersRepository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val store = ReminderStore(appContext.filesDir)
    private val mutableReminders = MutableStateFlow<List<Reminder>>(emptyList())

    val remindersFlow: StateFlow<List<Reminder>> = mutableReminders.asStateFlow()

    init {
        ReminderPermissions.ensureNotificationChannel(appContext)
        mutableReminders.value = ReminderSchedulePolicy.sortForList(store.load(), now())
    }

    @Synchronized
    fun reminders(): List<Reminder> = mutableReminders.value

    @Synchronized
    fun get(id: String): Reminder? = mutableReminders.value.firstOrNull { it.id == id }

    /**
     * Сохраняет напоминание: пустой [Reminder.id] означает создание,
     * существующий — правку (время и содержание). Возвращает сохранённую запись
     * вместе со статусом будильника, чтобы UI мог сообщить о неточности или отказе.
     */
    @Synchronized
    fun upsert(reminder: Reminder): ReminderSaveResult {
        val timestamp = now()
        val existing = reminder.id.takeIf { it.isNotBlank() }?.let { get(it) }
        val id = existing?.id ?: reminder.id.ifBlank { UUID.randomUUID().toString() }
        val saved = reminder.normalized().copy(
            id = id,
            createdAtUtcMs = existing?.createdAtUtcMs
                ?: reminder.createdAtUtcMs.takeIf { it > 0L }
                ?: timestamp,
            updatedAtUtcMs = timestamp
        )

        val updated = mutableReminders.value.toMutableList()
        val index = updated.indexOfFirst { it.id == saved.id }
        if (index >= 0) updated[index] = saved else updated.add(saved)
        persist(updated)

        val schedule = ReminderScheduler.schedule(appContext, saved)
        return ReminderSaveResult(reminder = saved, schedule = schedule)
    }

    @Synchronized
    fun delete(id: String) {
        if (id.isBlank()) return
        val updated = mutableReminders.value.filterNot { it.id == id }
        persist(updated)
        ReminderScheduler.cancel(appContext, id)
    }

    /** Переставляет будильники для всех будущих напоминаний (после выдачи разрешений, загрузки). */
    @Synchronized
    fun rescheduleAll() {
        ReminderPermissions.ensureNotificationChannel(appContext)
        ReminderScheduler.rescheduleAll(appContext, mutableReminders.value)
    }

    private fun persist(reminders: List<Reminder>) {
        val ordered = ReminderSchedulePolicy.sortForList(reminders, now())
        mutableReminders.value = ordered
        try {
            store.save(ordered)
        } catch (_: Exception) {
            // Не теряем UI-состояние из-за сбоя записи на диск.
        }
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        @Volatile
        private var instance: RemindersRepository? = null

        fun getInstance(context: Context): RemindersRepository {
            return instance ?: synchronized(this) {
                instance ?: RemindersRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
