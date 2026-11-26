package ru.nya.nyeios.data.autoupdate

import android.app.Notification
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.nya.nyeios.data.changes.ChangesRepository
import ru.nya.nyeios.data.changes.CurriculumChanges
import ru.nya.nyeios.data.changes.ScheduleChanges
import ru.nya.nyeios.data.reminders.ReminderPermissions
import ru.nya.nyeios.data.repository.EiosRepository

/**
 * Один цикл автообновления: текущая неделя -> следующая неделя -> БРС -> diff -> уведомление.
 *
 * Вызывается из [AutoUpdateWorker] (а также может быть вызван вручную). Все сетевые вызовы —
 * suspend-функции [EiosRepository], безопасные для background-потока.
 */
object AutoUpdateRunner {

    const val SKIP_DISABLED = "disabled"
    const val SKIP_NOT_LOGGED_IN = "not_logged_in"
    const val SKIP_NETWORK = "network"

    data class Result(
        val ran: Boolean,
        val skippedReason: String?,
        val scheduleChanges: ScheduleChanges,
        val curriculumChanges: CurriculumChanges,
        val errorMessage: String?
    )

    /**
     * @param withNotifications показывать прогресс/итог (воркер передаёт true только если
     *        уведомления включены в настройках и разрешены системой).
     * @param foregroundSink необязательный хук для foreground-service: получает готовую
     *        прогресс-нотификацию и возвращает true, если `setForeground` удался. При null
     *        (или false) нотификация показывается обычным `notify`.
     */
    suspend fun run(
        context: Context,
        withNotifications: Boolean,
        foregroundSink: (suspend (Notification) -> Boolean)? = null
    ): Result = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext

        val config = AutoUpdateSettingsStore.getInstance(appContext).config.value
        if (!config.enabled) return@withContext skipped(SKIP_DISABLED)

        val repository = EiosRepository.getInstance(appContext)
        if (!repository.isUserLoggedIn()) return@withContext skipped(SKIP_NOT_LOGGED_IN)

        val connectivity = AutoUpdateNetworkState.current(appContext)
        if (!AutoUpdateNetworkPolicy.isAllowed(config.network, connectivity)) {
            return@withContext skipped(SKIP_NETWORK)
        }

        val notifications = withNotifications &&
            config.notificationsEnabled &&
            ReminderPermissions.notificationsEnabled(appContext)

        val changesRepository = ChangesRepository.getInstance(appContext)

        var foregroundActive = false
        suspend fun reportProgress(stage: AutoUpdateStage) {
            if (!notifications) return
            val percent = AutoUpdateNotificationText.progressPercentFor(stage)
            val notification = AutoUpdateNotifications.progressNotification(appContext, percent, stage)
            if (foregroundSink != null) {
                foregroundActive = try {
                    foregroundSink(notification)
                } catch (_: Exception) {
                    false
                }
            }
            // Если foreground-service недоступен (Android 12+ background start restriction,
            // нет разрешения и т.п.) — просто обновляем обычную нотификацию с тем же id.
            if (!foregroundActive) {
                AutoUpdateNotifications.postProgress(appContext, notification)
            }
        }

        try {
            reportProgress(AutoUpdateStage.START)

            var failures = 0
            var lastError: String? = null

            val currentWeek = repository.getSchedule(0, forceNetwork = true)
            if (currentWeek.isSuccess) {
                currentWeek.getOrNull()?.let { changesRepository.onScheduleFetched(it) }
            } else {
                failures++
                lastError = currentWeek.exceptionOrNull()?.message ?: lastError
            }
            reportProgress(AutoUpdateStage.SCHEDULE)

            // Следующая неделя: ошибки не влияют на итог и не попадают в счётчик.
            repository.getSchedule(1, forceNetwork = true).getOrNull()?.let {
                changesRepository.onScheduleFetched(it)
            }
            reportProgress(AutoUpdateStage.NEXT_WEEK)

            val curriculum = repository.getCurriculum(forceNetwork = true)
            if (curriculum.isSuccess) {
                curriculum.getOrNull()?.let { changesRepository.onCurriculumFetched(it) }
            } else {
                failures++
                lastError = curriculum.exceptionOrNull()?.message ?: lastError
            }
            reportProgress(AutoUpdateStage.CURRICULUM)

            reportProgress(AutoUpdateStage.COMPARE)

            val scheduleChanges = changesRepository.scheduleChanges.value
            val curriculumChanges = changesRepository.curriculumChanges.value

            if (notifications) {
                AutoUpdateNotifications.cancelProgress(appContext)
                if (scheduleChanges.hasChanges || curriculumChanges.hasChanges) {
                    AutoUpdateNotifications.showChanges(
                        appContext,
                        scheduleChanges,
                        curriculumChanges
                    )
                }
                // Пустые изменения -> без шумной нотификации, прогресс просто убирается.
            }

            // Отмечаем синхронизацию, если хотя бы один основной запрос удался.
            if (failures < 2) {
                repository.updateLastSyncTime()
            }

            Result(
                ran = true,
                skippedReason = null,
                scheduleChanges = scheduleChanges,
                curriculumChanges = curriculumChanges,
                errorMessage = if (failures >= 2) {
                    lastError ?: "auto-update failed"
                } else {
                    null
                }
            )
        } catch (e: CancellationException) {
            // Отмену воркера пробрасываем: WorkManager сам решит судьбу работы.
            throw e
        } catch (e: Exception) {
            if (notifications) AutoUpdateNotifications.cancelProgress(appContext)
            Result(
                ran = true,
                skippedReason = null,
                scheduleChanges = changesRepository.scheduleChanges.value,
                curriculumChanges = changesRepository.curriculumChanges.value,
                errorMessage = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
            )
        }
    }

    private fun skipped(reason: String): Result = Result(
        ran = false,
        skippedReason = reason,
        scheduleChanges = ScheduleChanges(),
        curriculumChanges = CurriculumChanges(),
        errorMessage = null
    )
}
