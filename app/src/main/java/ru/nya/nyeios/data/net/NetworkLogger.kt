package ru.nya.nyeios.data.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NetworkLogLevel {
    INFO,
    REQUEST,
    RESPONSE,
    SUCCESS,
    WARNING,
    ERROR
}

data class NetworkLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: String,
    val level: NetworkLogLevel,
    val tag: String,
    val message: String,
    val details: String? = null,
    val isDegraded: Boolean = false,
    val rawResponse: String? = null,
    val httpCode: Int? = null
)

object NetworkLogger {
    private const val MAX_LOGS = 300
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _logs = MutableStateFlow<List<NetworkLogEntry>>(emptyList())
    val logs: StateFlow<List<NetworkLogEntry>> = _logs.asStateFlow()

    @Synchronized
    fun log(
        level: NetworkLogLevel,
        tag: String,
        message: String,
        details: String? = null,
        isDegraded: Boolean = false,
        rawResponse: String? = null,
        httpCode: Int? = null
    ) {
        val entry = NetworkLogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message,
            details = details,
            isDegraded = isDegraded,
            rawResponse = rawResponse,
            httpCode = httpCode
        )
        val current = _logs.value.toMutableList()
        current.add(0, entry) // Newest at the top
        if (current.size > MAX_LOGS) {
            _logs.value = current.take(MAX_LOGS)
        } else {
            _logs.value = current
        }
    }

    fun logRequest(method: String, url: String, userAgent: String?, summary: String? = null) {
        val details = buildString {
            append("Метод: ").append(method).append("\n")
            append("URL: ").append(url).append("\n")
            if (!userAgent.isNullOrEmpty()) {
                append("User-Agent: ").append(userAgent).append("\n")
            }
            if (!summary.isNullOrEmpty()) {
                append(summary)
            }
        }
        log(
            level = NetworkLogLevel.REQUEST,
            tag = "HTTP $method",
            message = "Отправлен запрос $method $url",
            details = details.trim()
        )
    }

    fun logResponse(
        code: Int,
        message: String,
        url: String,
        durationMs: Long,
        details: String? = null,
        isDegraded: Boolean = false,
        degradedReason: String? = null,
        rawResponse: String? = null
    ) {
        val level = when {
            isDegraded -> NetworkLogLevel.WARNING
            code in 200..399 -> NetworkLogLevel.RESPONSE
            else -> NetworkLogLevel.ERROR
        }
        val tag = when {
            isDegraded -> "200 ПУСТО"
            else -> "HTTP $code"
        }
        val msg = when {
            isDegraded && !degradedReason.isNullOrBlank() ->
                "Сервер ответил $code $message (${durationMs} мс) — [СБОЙ: $degradedReason]"
            isDegraded ->
                "Сервер ответил $code $message (${durationMs} мс) — [ПУСТАЯ СТРАНИЦА / СБОЙ 1С]"
            else ->
                "Сервер ответил $code $message (${durationMs} мс)"
        }
        val detailsStr = buildString {
            append("Код: ").append(code).append(" ").append(message)
            if (isDegraded) {
                append(" (ПУСТАЯ СТРАНИЦА / СБОЙ СЕРВЕРА)")
            }
            append("\n")
            if (isDegraded && !degradedReason.isNullOrBlank()) {
                append("Диагностика: ").append(degradedReason).append("\n")
            }
            append("Время ответа: ").append(durationMs).append(" мс\n")
            append("URL: ").append(url).append("\n")
            if (!details.isNullOrEmpty()) {
                append(details)
            }
        }
        log(
            level = level,
            tag = tag,
            message = msg,
            details = detailsStr.trim(),
            isDegraded = isDegraded,
            rawResponse = rawResponse,
            httpCode = code
        )
    }

    fun logError(tag: String, message: String, error: Throwable? = null, durationMs: Long? = null, details: String? = null) {
        val detailsStr = buildString {
            if (durationMs != null) {
                append("Время до ошибки: ").append(durationMs).append(" мс\n")
            }
            if (error != null) {
                append("Исключение: ").append(error::class.java.simpleName).append(": ").append(error.message).append("\n")
                append("Стек:\n").append(error.stackTraceToString().take(600))
            }
            if (!details.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n")
                append(details)
            }
        }
        log(
            level = NetworkLogLevel.ERROR,
            tag = tag,
            message = message,
            details = detailsStr.trim().ifEmpty { null }
        )
    }

    fun logInfo(tag: String, message: String, details: String? = null) {
        log(level = NetworkLogLevel.INFO, tag = tag, message = message, details = details)
    }

    fun logSuccess(tag: String, message: String, details: String? = null) {
        log(level = NetworkLogLevel.SUCCESS, tag = tag, message = message, details = details)
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun getAllFormatted(): String {
        return _logs.value.reversed().joinToString("\n\n") { entry ->
            buildString {
                append("[${entry.timestamp}] [${entry.level}] [${entry.tag}] ${entry.message}")
                if (!entry.details.isNullOrEmpty()) {
                    append("\n  ").append(entry.details.replace("\n", "\n  "))
                }
            }
        }
    }
}
