package ru.nya.nyeios

import android.app.Application
import ru.nya.nyeios.ui.language.LanguageManager

/**
 * Точка инициализации языка для любого процесса приложения.
 *
 * Уведомления о напоминаниях и восстановление будильников поднимают процесс без
 * Activity, поэтому [LanguageManager.init] из `MainActivity` там не выполняется:
 * `Locale.getDefault()` остался бы системным, и фоновая копия («ДЗ: …») выходила бы
 * на языке устройства, даже если в приложении выбран другой язык. Инициализация
 * здесь гарантирует, что фон совпадает с настройкой языка приложения.
 */
class NyEiosApp : Application() {

    override fun onCreate() {
        super.onCreate()
        LanguageManager.init(this)
        LanguageManager.applyProcessLocale()
    }
}
