package ru.nya.nyeios.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// NieR: Automata / YoRHa OS Color Palette
// ==========================================

// Base Palettes
object NierPaletteRegular {
    val bg = Color(0xFFCAC6A8)          // Main application canvas (Desert olive / sand)
    val panel = Color(0xFFC2BE9F)       // Tab bar, Navigation bar, Cards
    val panelAlt = Color(0xFFD0CCB0)    // Secondary panel, alternating rows
    val surface = Color(0xFFB5B197)      // Recessed areas, map corridors
    val dark = Color(0xFF3A342B)         // Primary text, heavy borders, solid badges
    val darkSecondary = Color(0xFF4D4438)// Secondary dark, borders, dots
    val border = Color(0xFF7A7060)       // Standard borders, dividers
    val borderLight = Color(0xFF9A9280)  // Subtle separators, slot borders
    val selection = Color(0xFF4D4438)    // Selected item fill
    val selectionText = Color(0xFFCAC6A8)// Text on selected item
    val highlight = Color(0xFF8A8060)    // Muted accent
    val dim = Color(0xFF7A7060)          // Secondary labels, timestamps
    val blue = Color(0xFF4A90D9)         // Lecture, primary accent, info badge
    val green = Color(0xFF4CAF7A)        // Practice, live status, credit/success
    val amber = Color(0xFFD9A040)        // Lab, warning, diff credit
    val red = Color(0xFFD95555)          // Exam, error, critical alert
    val purple = Color(0xFF9C6DD9)       // Other disciplines, seminar
    val magenta = Color(0xFFD946EF)      // Multi-floor navigation connector
}

object NierPaletteNight {
    // Inverted YoRHa OS: Dark background canvas, warm sand text & panels
    val bg = Color(0xFF24201A)          // Main dark terminal canvas
    val panel = Color(0xFF2E2922)       // Dark tab bar, navigation bar
    val panelAlt = Color(0xFF383229)    // Dark secondary card panel
    val surface = Color(0xFF1E1A15)      // Deep recessed areas
    val dark = Color(0xFFCAC6A8)         // Swapped: Sand primary text, heavy borders, badges
    val darkSecondary = Color(0xFFB8B396)// Swapped: Sand secondary text
    val border = Color(0xFF5A5244)       // Dark divider borders
    val borderLight = Color(0xFF443D32)  // Subtle dark borders
    val selection = Color(0xFFCAC6A8)    // Swapped: Sand selection bar
    val selectionText = Color(0xFF24201A)// Swapped: Dark text on selection
    val highlight = Color(0xFF6E6452)    // Dark highlight
    val dim = Color(0xFF8F8876)          // Muted warm sand-grey for timestamps
    val blue = Color(0xFF64B5F6)         // Lecture accent tuned for dark background
    val green = Color(0xFF66BB6A)        // Practice accent tuned for dark background
    val amber = Color(0xFFFFB74D)        // Lab warning accent tuned for dark background
    val red = Color(0xFFEF5350)          // Exam alert accent tuned for dark background
    val purple = Color(0xFFBA68C8)       // Discipline accent tuned for dark background
    val magenta = Color(0xFFF06292)      // Floor navigation accent tuned for dark background
}

// Dynamic properties reflecting active theme
val NierBg: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.bg else NierPaletteRegular.bg
val NierPanel: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.panel else NierPaletteRegular.panel
val NierPanelAlt: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.panelAlt else NierPaletteRegular.panelAlt
val NierSurface: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.surface else NierPaletteRegular.surface

val NierDark: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.dark else NierPaletteRegular.dark
val NierDarkSecondary: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.darkSecondary else NierPaletteRegular.darkSecondary
val NierBorder: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.border else NierPaletteRegular.border
val NierBorderLight: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.borderLight else NierPaletteRegular.borderLight

val NierSelection: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.selection else NierPaletteRegular.selection
val NierSelectionText: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.selectionText else NierPaletteRegular.selectionText
val NierHighlight: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.highlight else NierPaletteRegular.highlight
val NierDim: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.dim else NierPaletteRegular.dim

val NierBlue: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.blue else NierPaletteRegular.blue
val NierGreen: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.green else NierPaletteRegular.green
val NierAmber: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.amber else NierPaletteRegular.amber
val NierRed: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.red else NierPaletteRegular.red
val NierPurple: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.purple else NierPaletteRegular.purple
val NierMagenta: Color get() = if (ThemeManager.currentTheme == NierThemeMode.NIGHT) NierPaletteNight.magenta else NierPaletteRegular.magenta

// Compatibility Mappings
val ObsidianBg: Color get() = NierBg
val ObsidianSurface: Color get() = NierPanel
val ObsidianCard: Color get() = NierPanelAlt
val ObsidianCardSelected: Color get() = NierSelection
val ObsidianBorder: Color get() = NierBorder
val ObsidianBorderActive: Color get() = NierDark

val TextPrimary: Color get() = NierDark
val TextSecondary: Color get() = NierDarkSecondary
val TextMuted: Color get() = NierDim

val LectureBlue: Color get() = NierBlue
val LectureBlueBg: Color get() = NierBlue.copy(alpha = 0.2f)

val PracticeGreen: Color get() = NierGreen
val PracticeGreenBg: Color get() = NierGreen.copy(alpha = 0.2f)

val LabAmber: Color get() = NierAmber
val LabAmberBg: Color get() = NierAmber.copy(alpha = 0.2f)

val ExamRed: Color get() = NierRed
val ExamRedBg: Color get() = NierRed.copy(alpha = 0.2f)

val OtherPurple: Color get() = NierPurple
val OtherPurpleBg: Color get() = NierPurple.copy(alpha = 0.2f)

val LiveBadgeColor: Color get() = NierGreen
val LiveGlowBorder: Color get() = NierGreen
