package ru.nya.nyeios.data.net

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.TimeUnit

sealed class PingResult {
    data object Idle : PingResult()
    data object Measuring : PingResult()
    data class Success(val latencyMs: Long, val timestamp: Long = System.currentTimeMillis()) : PingResult()
    data class Error(val message: String, val timestamp: Long = System.currentTimeMillis()) : PingResult()
}

data class EiosLastGetInfo(
    val durationMs: Long?,
    val timestamp: Long?,
    val path: String?
)

class NetworkMetricsTracker private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val ipClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private val _lastGetInfo = MutableStateFlow(loadLastGetInfo())
    val lastGetInfo: StateFlow<EiosLastGetInfo> = _lastGetInfo.asStateFlow()

    private val _pingResult = MutableStateFlow<PingResult>(PingResult.Idle)
    val pingResult: StateFlow<PingResult> = _pingResult.asStateFlow()

    private val _externalIp = MutableStateFlow<String?>(prefs.getString(KEY_LAST_EXTERNAL_IP, null))
    val externalIp: StateFlow<String?> = _externalIp.asStateFlow()

    private fun loadLastGetInfo(): EiosLastGetInfo {
        val duration = if (prefs.contains(KEY_LAST_GET_DURATION_MS)) {
            prefs.getLong(KEY_LAST_GET_DURATION_MS, -1L).takeIf { it >= 0 }
        } else null

        val ts = if (prefs.contains(KEY_LAST_GET_TIMESTAMP)) {
            prefs.getLong(KEY_LAST_GET_TIMESTAMP, 0L).takeIf { it > 0 }
        } else null

        val path = prefs.getString(KEY_LAST_GET_PATH, null)

        return EiosLastGetInfo(
            durationMs = duration,
            timestamp = ts,
            path = path
        )
    }

    /**
     * Фиксирует параметры последнего GET-запроса к eios.gukolomna.ru.
     * Сохраняется в SharedPreferences для сохранения между сессиями.
     */
    fun recordEiosGet(durationMs: Long, url: String) {
        val now = System.currentTimeMillis()
        val path = try {
            val uri = android.net.Uri.parse(url)
            uri.path ?: url
        } catch (_: Exception) {
            url
        }

        prefs.edit()
            .putLong(KEY_LAST_GET_DURATION_MS, durationMs)
            .putLong(KEY_LAST_GET_TIMESTAMP, now)
            .putString(KEY_LAST_GET_PATH, path)
            .apply()

        _lastGetInfo.value = EiosLastGetInfo(
            durationMs = durationMs,
            timestamp = now,
            path = path
        )
    }

    /**
     * Замеряет сетевую задержку (TCP ping / handshake RTT) до eios.gukolomna.ru:443.
     */
    suspend fun measureEiosPing(timeoutMs: Int = 4000): PingResult = withContext(Dispatchers.IO) {
        _pingResult.value = PingResult.Measuring
        val host = "eios.gukolomna.ru"
        val port = 443
        val start = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            val latency = System.currentTimeMillis() - start
            val result = PingResult.Success(latency)
            _pingResult.value = result
            result
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: e.message ?: "Сбой соединения"
            val result = PingResult.Error(errorMsg)
            _pingResult.value = result
            result
        }
    }

    /**
     * Запрашивает внешний публичный IP устройства (с которого уходят сетевые запросы).
     */
    suspend fun fetchExternalIp(): String? = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            "https://api.ipify.org",
            "https://icanhazip.com"
        )
        for (url in endpoints) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "NyEIOS-Diagnostics/1.0")
                    .build()
                ipClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val ip = response.body?.string()?.trim()
                        if (!ip.isNullOrEmpty() && ip.length <= 45) {
                            prefs.edit().putString(KEY_LAST_EXTERNAL_IP, ip).apply()
                            _externalIp.value = ip
                            return@withContext ip
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next endpoint
            }
        }
        _externalIp.value
    }

    /**
     * Возвращает локальный IP сетевого интерфейса устройства.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses ?: continue
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return "${addr.hostAddress} (${intf.name})"
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
        return null
    }

    /**
     * Проверяет, активен ли VPN на устройстве.
     */
    @Suppress("DEPRECATION")
    fun isVpnActive(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork
            if (activeNetwork != null) {
                val caps = cm.getNetworkCapabilities(activeNetwork)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                    return true
                }
            }
            val hasVpnNetwork = cm.allNetworks.any { net ->
                cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
            if (hasVpnNetwork) return true

            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return false
            for (iface in interfaces.asSequence()) {
                if (iface.isUp && (iface.name.startsWith("tun") || iface.name.startsWith("ppp") || iface.name.startsWith("wg") || iface.name.startsWith("tap"))) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Возвращает тип активной сети (WI-FI, МОБИЛЬНАЯ СЕТЬ, VPN, НЕТ ПОДКЛЮЧЕНИЯ).
     */
    fun getNetworkTypeName(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return "НЕИЗВЕСТНО"
            val activeNetwork = cm.activeNetwork ?: return "НЕТ ПОДКЛЮЧЕНИЯ"
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "НЕТ ПОДКЛЮЧЕНИЯ"

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WI-FI"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "МОБИЛЬНАЯ СЕТЬ"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> "ПОДКЛЮЧЕНО"
            }
        } catch (_: Exception) {
            "НЕИЗВЕСТНО"
        }
    }

    companion object {
        private const val PREFS_NAME = "nyeios_network_metrics"
        private const val KEY_LAST_GET_DURATION_MS = "last_eios_get_duration_ms"
        private const val KEY_LAST_GET_TIMESTAMP = "last_eios_get_timestamp"
        private const val KEY_LAST_GET_PATH = "last_eios_get_path"
        private const val KEY_LAST_EXTERNAL_IP = "last_external_ip"

        @Volatile
        private var INSTANCE: NetworkMetricsTracker? = null

        fun getInstance(context: Context): NetworkMetricsTracker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkMetricsTracker(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
