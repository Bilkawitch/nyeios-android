package ru.nya.nyeios.data.autoupdate

/** Политика использования сети для фонового автообновления. */
enum class AutoUpdateNetwork { WIFI_ONLY, MOBILE_ONLY, ANY }

/**
 * Пользовательские настройки автообновления.
 *
 * Значения по умолчанию безопасны: функция выключена и ничего не делает до явного включения.
 */
data class AutoUpdateConfig(
    val enabled: Boolean = false,
    val intervalHours: Int = 3,
    val network: AutoUpdateNetwork = AutoUpdateNetwork.ANY,
    val notificationsEnabled: Boolean = false
) {
    companion object {
        /** Единственные допустимые интервалы периодической работы (в часах). */
        val ALLOWED_INTERVALS: List<Int> = listOf(1, 2, 3, 6, 12, 24)

        val DEFAULT: AutoUpdateConfig = AutoUpdateConfig()
    }
}

/**
 * Приводит конфиг к допустимому виду (инварианты подсистемы):
 * - интервал всегда из [AutoUpdateConfig.ALLOWED_INTERVALS], иначе — значение по умолчанию;
 * - уведомления невозможны при выключенном автообновлении.
 *
 * Функция чистая: не читает Android-ресурсы и не пишет настройки.
 */
fun AutoUpdateConfig.normalized(): AutoUpdateConfig {
    val safeInterval = if (intervalHours in AutoUpdateConfig.ALLOWED_INTERVALS) {
        intervalHours
    } else {
        AutoUpdateConfig.DEFAULT.intervalHours
    }
    return copy(
        intervalHours = safeInterval,
        notificationsEnabled = enabled && notificationsEnabled
    )
}
