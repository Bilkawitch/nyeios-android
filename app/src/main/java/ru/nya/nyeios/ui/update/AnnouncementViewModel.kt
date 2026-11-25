package ru.nya.nyeios.ui.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure state holder for a launch-scoped announcement banner with permanent opt-out.
 * - [remindLater] hides the banner for the current app launch / ViewModel lifetime only.
 * - [dismissForever] persists the opt-out so future launches never show the banner again.
 */
class AnnouncementSessionState(
    isPermanentlyDismissed: () -> Boolean,
    private val savePermanentlyDismissed: () -> Unit
) {
    private val _isVisible = MutableStateFlow(!isPermanentlyDismissed())
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    fun remindLater() {
        _isVisible.value = false
    }

    fun dismissForever() {
        savePermanentlyDismissed()
        _isVisible.value = false
    }
}

class AnnouncementViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val sessionState = AnnouncementSessionState(
        isPermanentlyDismissed = { prefs.getBoolean(KEY_FLOOR_MAP_DISMISSED_FOREVER, false) },
        savePermanentlyDismissed = {
            prefs.edit().putBoolean(KEY_FLOOR_MAP_DISMISSED_FOREVER, true).apply()
        }
    )

    val isVisible: StateFlow<Boolean> = sessionState.isVisible

    fun remindLater() {
        sessionState.remindLater()
    }

    fun dismissForever() {
        sessionState.dismissForever()
    }

    companion object {
        const val PREFS_NAME = "nyeios_announcement_prefs"
        const val KEY_FLOOR_MAP_DISMISSED_FOREVER = "floor_map_announcement_dismissed_forever"
    }
}
