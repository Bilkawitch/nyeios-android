package ru.nya.nyeios.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun NyEIOSTheme(
    content: @Composable () -> Unit
) {
    val isNight = ThemeManager.currentTheme == NierThemeMode.NIGHT

    val colorScheme = darkColorScheme(
        primary = NierDark,
        onPrimary = NierBg,
        primaryContainer = NierPanel,
        onPrimaryContainer = NierDark,
        secondary = NierGreen,
        onSecondary = NierBg,
        secondaryContainer = NierPanelAlt,
        onSecondaryContainer = NierDark,
        tertiary = NierBlue,
        onTertiary = NierBg,
        tertiaryContainer = NierPanel,
        onTertiaryContainer = NierDark,
        background = NierBg,
        onBackground = NierDark,
        surface = NierPanel,
        onSurface = NierDark,
        surfaceVariant = NierPanelAlt,
        onSurfaceVariant = NierDarkSecondary,
        outline = NierBorder,
        outlineVariant = NierBorderLight,
        error = NierRed,
        onError = NierBg
    )

    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = NierBg.toArgb()
                window.navigationBarColor = NierPanel.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isNight
                    isAppearanceLightNavigationBars = !isNight
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NierTypography,
        content = content
    )
}
