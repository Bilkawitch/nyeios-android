package ru.nya.nyeios.data.autoupdate

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Хранилище настроек автообновления в обычном `SharedPreferences("nyeios_auto_update")`.
 *
 * Любое изменение сразу персистится, публикуется в [config] и синхронизирует планировщик
 * WorkManager (см. [AutoUpdateScheduler.sync]). Синглтон на application-context, безопасен
 * для обращения из UI и воркера.
 */
class AutoUpdateSettingsStore private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<AutoUpdateConfig> = _config.asStateFlow()

    /** Сохраняет настройки и перепланирует/отменяет фоновую работу. Не блокирует UI. */
    fun update(config: AutoUpdateConfig) {
        val normalized = config.normalized()
        persist(normalized)
        _config.value = normalized
        AutoUpdateScheduler.sync(appContext, normalized)
    }

    fun setEnabled(enabled: Boolean) {
        update(_config.value.copy(enabled = enabled))
    }

    fun setIntervalHours(hours: Int) {
        update(_config.value.copy(intervalHours = hours))
    }

    fun setNetwork(network: AutoUpdateNetwork) {
        update(_config.value.copy(network = network))
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        update(_config.value.copy(notificationsEnabled = enabled))
    }

    private fun persist(config: AutoUpdateConfig) {
        try {
            prefs.edit()
                .putBoolean(KEY_ENABLED, config.enabled)
                .putInt(KEY_INTERVAL, config.intervalHours)
                .putString(KEY_NETWORK, config.network.name)
                .putBoolean(KEY_NOTIFICATIONS, config.notificationsEnabled)
                .apply()
        } catch (_: Exception) {
            // Ошибка записи настроек не должна ронять UI.
        }
    }

    private fun load(): AutoUpdateConfig {
        val defaults = AutoUpdateConfig.DEFAULT
        val network = try {
            AutoUpdateNetwork.valueOf(
                prefs.getString(KEY_NETWORK, defaults.network.name) ?: defaults.network.name
            )
        } catch (_: Exception) {
            defaults.network
        }
        return AutoUpdateConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            intervalHours = prefs.getInt(KEY_INTERVAL, defaults.intervalHours),
            network = network,
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, defaults.notificationsEnabled)
        ).normalized()
    }

    companion object {
        private const val PREFS_NAME = "nyeios_auto_update"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "interval_hours"
        private const val KEY_NETWORK = "network"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"

        @Volatile
        private var INSTANCE: AutoUpdateSettingsStore? = null

        fun getInstance(context: Context): AutoUpdateSettingsStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AutoUpdateSettingsStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
