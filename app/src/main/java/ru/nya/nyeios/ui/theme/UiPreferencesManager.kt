package ru.nya.nyeios.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ListDensityMode(val title: String) {
    COMPACT("КОМПАКТНО"),
    STANDARD("СТАНДАРТ"),
    SPACIOUS("ПРОСТОРНО")
}

object UiPreferencesManager {
    private const val PREFS_NAME = "nyeios_ui_prefs"
    private const val KEY_LARGE_FONT = "pref_large_font"
    private const val KEY_ANIMATIONS = "pref_animations"
    private const val KEY_THICK_BORDERS = "pref_thick_borders"
    private const val KEY_SHOW_SECONDS = "pref_show_seconds"
    private const val KEY_LIST_DENSITY = "pref_list_density"

    var largeScheduleFont: Boolean by mutableStateOf(false)
        private set

    var animationsEnabled: Boolean by mutableStateOf(true)
        private set

    var thickBorders: Boolean by mutableStateOf(false)
        private set

    var showSecondsInSync: Boolean by mutableStateOf(false)
        private set

    var listDensity: ListDensityMode by mutableStateOf(ListDensityMode.STANDARD)
        private set

    val borderWidth: Dp
        get() = if (thickBorders) 2.dp else 1.dp

    val scheduleFontDeltaSp: Int
        get() = if (largeScheduleFont) 2 else 0

    val scheduleFontDelta: TextUnit
        get() = (if (largeScheduleFont) 2 else 0).sp

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        largeScheduleFont = prefs.getBoolean(KEY_LARGE_FONT, false)
        animationsEnabled = prefs.getBoolean(KEY_ANIMATIONS, true)
        thickBorders = prefs.getBoolean(KEY_THICK_BORDERS, false)
        showSecondsInSync = prefs.getBoolean(KEY_SHOW_SECONDS, false)
        val densityName = prefs.getString(KEY_LIST_DENSITY, ListDensityMode.STANDARD.name)
        listDensity = try {
            ListDensityMode.valueOf(densityName ?: ListDensityMode.STANDARD.name)
        } catch (_: Exception) {
            ListDensityMode.STANDARD
        }
    }

    fun setLargeScheduleFont(context: Context, value: Boolean) {
        largeScheduleFont = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LARGE_FONT, value)
            .apply()
    }

    fun setAnimationsEnabled(context: Context, value: Boolean) {
        animationsEnabled = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ANIMATIONS, value)
            .apply()
    }

    fun setThickBorders(context: Context, value: Boolean) {
        thickBorders = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_THICK_BORDERS, value)
            .apply()
    }

    fun setShowSecondsInSync(context: Context, value: Boolean) {
        showSecondsInSync = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_SECONDS, value)
            .apply()
    }

    fun setListDensity(context: Context, value: ListDensityMode) {
        listDensity = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIST_DENSITY, value.name)
            .apply()
    }
}
