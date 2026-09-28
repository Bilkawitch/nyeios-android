package ru.nya.nyeios.ui.theme

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.R

enum class ListDensityMode(val titleResId: Int) {
    COMPACT(R.string.density_compact),
    STANDARD(R.string.density_standard),
    SPACIOUS(R.string.density_spacious)
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

    /** Vertical rhythm multiplier of list rows, driven by [listDensity]. */
    val listSpacingScale: Float
        get() = when (listDensity) {
            ListDensityMode.COMPACT -> 0.75f
            ListDensityMode.STANDARD -> 1f
            ListDensityMode.SPACIOUS -> 1.3f
        }

    /** Scales a vertical list spacing or padding value by the selected [listDensity]. */
    fun listSpace(base: Dp): Dp = base * listSpacingScale

    /**
     * Returns [spec] while the user allows transitions, otherwise an instant snap spec, so
     * "Анимации переходов" really disables movement instead of only hiding it in the settings tab.
     */
    fun <T> gated(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (animationsEnabled) spec else snap()

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

/**
 * An [InfiniteTransition] while the user allows animations, or `null` when they are disabled —
 * callers then fall back to a static value instead of running a decorative loop (halo, blink,
 * spinner, marching chevrons).
 */
@Composable
fun rememberUiInfiniteTransition(label: String): InfiniteTransition? =
    if (UiPreferencesManager.animationsEnabled) rememberInfiniteTransition(label = label) else null

/**
 * An [AnimatedVisibility] that respects "Анимации переходов": when the user turned them off the
 * content appears/disappears instantly, otherwise [enter]/[exit] (the AnimatedVisibility
 * defaults unless overridden) are used.
 */
@Composable
fun UiAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandIn(),
    exit: ExitTransition = shrinkOut() + fadeOut(),
    label: String = "ui_animated_visibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (UiPreferencesManager.animationsEnabled) enter else EnterTransition.None,
        exit = if (UiPreferencesManager.animationsEnabled) exit else ExitTransition.None,
        label = label,
        content = content
    )
}
