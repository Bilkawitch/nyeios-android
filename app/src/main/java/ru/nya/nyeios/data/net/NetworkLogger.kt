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
    ERROR
}

data class NetworkLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: String,
    val level: NetworkLogLevel,
    val tag: String,
    val message: String,
    val details: String? = null
)

object NetworkLogger {
    private const val MAX_LOGS = 300
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _logs = MutableStateFlow<List<NetworkLogEntry>>(emptyList())
    val logs: StateFlow<List<NetworkLogEntry>> = _logs.asStateFlow()

    @Synchronized
    fun log(level: NetworkLogLevel, tag: String, message: String, details: String? = null) {
        val entry = NetworkLogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message,
            details = details
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

    fun logResponse(code: Int, message: String, url: String, durationMs: Long, details: String? = null) {
        val level = if (code in 200..399) NetworkLogLevel.RESPONSE else NetworkLogLevel.ERROR
        val detailsStr = buildString {
            append("Код: ").append(code).append(" ").append(message).append("\n")
            append("Время ответа: ").append(durationMs).append(" мс\n")
            append("URL: ").append(url).append("\n")
            if (!details.isNullOrEmpty()) {
                append(details)
            }
        }
        log(
            level = level,
            tag = "HTTP $code",
            message = "Сервер ответил $code $message (${durationMs} мс)",
            details = detailsStr.trim()
        )
    }

    fun logError(tag: String, message: String, error: Throwable? = null, durationMs: Long? = null) {
        val details = buildString {
            if (durationMs != null) {
                append("Время до ошибки: ").append(durationMs).append(" мс\n")
            }
            if (error != null) {
                append("Исключение: ").append(error::class.java.simpleName).append(": ").append(error.message).append("\n")
                append("Стек:\n").append(error.stackTraceToString().take(600))
            }
        }
        log(
            level = NetworkLogLevel.ERROR,
            tag = tag,
            message = message,
            details = details.trim().ifEmpty { null }
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
