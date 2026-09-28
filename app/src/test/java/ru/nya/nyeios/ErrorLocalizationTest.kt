package ru.nya.nyeios

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.ui.common.isAuthRelatedMessage

class ErrorLocalizationTest {

    @Test
    fun `russian auth failures are detected`() {
        assertTrue(isAuthRelatedMessage("Сессия завершена. Требуется авторизация в ЭИОС."))
        assertTrue(isAuthRelatedMessage("Неверный логин или пароль"))
        assertTrue(isAuthRelatedMessage("Пользователь заблокирован и аккаунт недоступен"))
    }

    @Test
    fun `english auth failures are detected`() {
        assertTrue(isAuthRelatedMessage("Session expired. Authorization in EIOS is required."))
        assertTrue(isAuthRelatedMessage("Invalid login or password"))
        assertTrue(isAuthRelatedMessage("EIOS authorization is required to download this file"))
    }

    @Test
    fun `unrelated and blank messages are ignored`() {
        assertFalse(isAuthRelatedMessage(null))
        assertFalse(isAuthRelatedMessage(""))
        assertFalse(isAuthRelatedMessage("   "))
        assertFalse(isAuthRelatedMessage("Server error: HTTP 500"))
        assertFalse(isAuthRelatedMessage("Отсутствует соединение с сервером. Повторите попытку позже"))
    }
}
