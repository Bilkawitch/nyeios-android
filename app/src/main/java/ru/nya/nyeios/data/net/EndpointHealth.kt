package ru.nya.nyeios.data.net

enum class EndpointStatus {
    IDLE,
    PENDING,
    CHECKING,
    OK,
    DEGRADED,
    AUTH_REQUIRED,
    ERROR
}

data class EndpointHealthItem(
    val id: String,
    val name: String,
    val path: String,
    val status: EndpointStatus = EndpointStatus.IDLE,
    val httpCode: Int? = null,
    val latencyMs: Long? = null,
    val message: String? = null,
    val rawLog: String? = null
)
