package ru.nya.nyeios.data.autoupdate

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Чистая политика «можно ли сейчас обновляться в выбранной сети».
 *
 * Тестируется на JVM: не зависит от Android, кроме [AutoUpdateNetworkState], который лежит
 * в этом же файле, но не участвует в проверках политики.
 */
object AutoUpdateNetworkPolicy {

    data class Connectivity(
        val hasInternet: Boolean,
        val onWifi: Boolean,
        val onCellular: Boolean
    )

    fun isAllowed(policy: AutoUpdateNetwork, c: Connectivity): Boolean = when (policy) {
        // Только Wi-Fi (мобильный трафик при этом не важен).
        AutoUpdateNetwork.WIFI_ONLY -> c.hasInternet && c.onWifi
        // Только сотовая сеть. Если активен Wi-Fi — не запускаемся, чтобы не тратить мобильный
        // трафик впустую: приоритет у Wi-Fi.
        AutoUpdateNetwork.MOBILE_ONLY -> c.hasInternet && c.onCellular && !c.onWifi
        // Любая сеть с выходом в интернет.
        AutoUpdateNetwork.ANY -> c.hasInternet
    }
}

/** Снимок текущего состояния сети через ConnectivityManager. */
object AutoUpdateNetworkState {

    fun current(context: Context): AutoUpdateNetworkPolicy.Connectivity {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return AutoUpdateNetworkPolicy.Connectivity(false, false, false)
            val network = manager.activeNetwork
                ?: return AutoUpdateNetworkPolicy.Connectivity(false, false, false)
            val capabilities = manager.getNetworkCapabilities(network)
                ?: return AutoUpdateNetworkPolicy.Connectivity(false, false, false)

            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val onWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val onCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

            AutoUpdateNetworkPolicy.Connectivity(
                hasInternet = hasInternet,
                onWifi = onWifi,
                onCellular = onCellular
            )
        } catch (_: Exception) {
            // Нет разрешения/менеджера сети — считаем, что сети нет: лучше пропустить цикл,
            // чем запустить его в неизвестных условиях.
            AutoUpdateNetworkPolicy.Connectivity(false, false, false)
        }
    }
}
