package ru.nya.nyeios

import android.app.Application
import ru.nya.nyeios.data.floormap.FloorMapRepository
import ru.nya.nyeios.ui.language.LanguageManager

/**
 * Точка инициализации процесса приложения.
 *
 * - [LanguageManager]: уведомления о напоминаниях и восстановление будильников поднимают процесс
 *   без `Activity`, поэтому локаль нужно выставлять здесь, а не в `MainActivity`.
 * - [FloorMapRepository]: поэтажные карты читаются из asset-файлов `floor_<n>_map.json`
 *   (плюс PNG-подложка `floor_<n>`), поэтому правка плана больше не требует изменения кода.
 */
class NyEiosApp : Application() {

    override fun onCreate() {
        super.onCreate()
        LanguageManager.init(this)
        LanguageManager.applyProcessLocale()
        FloorMapRepository.initialize(this)
    }
}
