package ru.nya.nyeios.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.nya.nyeios.R

@Composable
fun localizeErrorMessage(message: String?): String {
    if (message.isNullOrBlank()) return ""
    return when {
        message.contains("Не удалось загрузить расписание", ignoreCase = true) -> stringResource(R.string.schedule_load_error)
        message.contains("Не удалось загрузить живую ленту", ignoreCase = true) -> stringResource(R.string.feed_load_error)
        message.contains("Не удалось загрузить успеваемость", ignoreCase = true) -> stringResource(R.string.curriculum_load_error)
        message.contains("Заполните логин", ignoreCase = true) -> stringResource(R.string.login_fill_credentials)
        message.contains("Неверный логин", ignoreCase = true) -> stringResource(R.string.login_invalid_credentials)
        message.contains("Ошибка авторизации", ignoreCase = true) -> stringResource(R.string.login_auth_failed)
        message.contains("Файл еще не скачан", ignoreCase = true) -> stringResource(R.string.file_not_downloaded_yet)
        message.contains("Отсутствует соединение с сервером", ignoreCase = true) ||
            message.contains("No connection to the server", ignoreCase = true) ->
            stringResource(R.string.error_no_server_connection)
        message.contains("Сессия завершена", ignoreCase = true) ||
            message.contains("Session expired", ignoreCase = true) ->
            stringResource(R.string.error_session_expired)
        else -> message
    }
}

/**
 * Auth-related failures can arrive in either language: server-derived text (timetable/feed parsers)
 * stays Russian, while data-layer copy is localized, so both spellings are matched here.
 */
fun isAuthRelatedMessage(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    val lower = message.lowercase()
    return listOf(
        "авториз", "сесси", "аккаунт", "логин", "пароль",
        "authoriz", "session", "account", "login", "password", "sign-in"
    ).any { lower.contains(it) }
}
