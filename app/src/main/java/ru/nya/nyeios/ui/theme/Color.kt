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

object NierPaletteBlack {
    // Pure AMOLED Black: 100% black backgrounds, contrast elements in current palette dark charcoal (#3A342B)
    val bg = Color(0xFF000000)          // 100% pure black OLED canvas
    val panel = Color(0xFF000000)       // Pure black tab bar & navigation bar
    val panelAlt = Color(0xFF0A0A08)    // Pure black cards with subtle separation
    val surface = Color(0xFF000000)      // Pure black recessed surface
    val dark = Color(0xFFCAC6A8)         // Text in warm sand for high contrast readability
    val darkSecondary = Color(0xFFAFA990)// Secondary sand text
    val border = Color(0xFF3A342B)       // Contrast borders using the dark military charcoal from current palette!
    val borderLight = Color(0xFF2C2720)  // Subtle dark charcoal dividers
    val selection = Color(0xFF3A342B)    // Contrast selection block using dark charcoal from current palette
    val selectionText = Color(0xFFCAC6A8)// Sand text on dark charcoal selection
    val highlight = Color(0xFF4D4438)    // Contrast highlight
    val dim = Color(0xFF756E5D)          // Muted labels
    val blue = Color(0xFF64B5F6)         // Lecture accent
    val green = Color(0xFF66BB6A)        // Practice accent
    val amber = Color(0xFFFFB74D)        // Lab accent
    val red = Color(0xFFEF5350)          // Exam accent
    val purple = Color(0xFFBA68C8)       // Discipline accent
    val magenta = Color(0xFFF06292)      // Floor navigation accent
}

// Dynamic properties reflecting active theme
val NierBg: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.bg
    NierThemeMode.NIGHT -> NierPaletteNight.bg
    NierThemeMode.BLACK -> NierPaletteBlack.bg
}

val NierPanel: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.panel
    NierThemeMode.NIGHT -> NierPaletteNight.panel
    NierThemeMode.BLACK -> NierPaletteBlack.panel
}

val NierPanelAlt: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.panelAlt
    NierThemeMode.NIGHT -> NierPaletteNight.panelAlt
    NierThemeMode.BLACK -> NierPaletteBlack.panelAlt
}

val NierSurface: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.surface
    NierThemeMode.NIGHT -> NierPaletteNight.surface
    NierThemeMode.BLACK -> NierPaletteBlack.surface
}

val NierDark: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.dark
    NierThemeMode.NIGHT -> NierPaletteNight.dark
    NierThemeMode.BLACK -> NierPaletteBlack.dark
}

val NierDarkSecondary: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.darkSecondary
    NierThemeMode.NIGHT -> NierPaletteNight.darkSecondary
    NierThemeMode.BLACK -> NierPaletteBlack.darkSecondary
}

val NierBorder: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.border
    NierThemeMode.NIGHT -> NierPaletteNight.border
    NierThemeMode.BLACK -> NierPaletteBlack.border
}

val NierBorderLight: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.borderLight
    NierThemeMode.NIGHT -> NierPaletteNight.borderLight
    NierThemeMode.BLACK -> NierPaletteBlack.borderLight
}

val NierSelection: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.selection
    NierThemeMode.NIGHT -> NierPaletteNight.selection
    NierThemeMode.BLACK -> NierPaletteBlack.selection
}

val NierSelectionText: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.selectionText
    NierThemeMode.NIGHT -> NierPaletteNight.selectionText
    NierThemeMode.BLACK -> NierPaletteBlack.selectionText
}

val NierHighlight: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.highlight
    NierThemeMode.NIGHT -> NierPaletteNight.highlight
    NierThemeMode.BLACK -> NierPaletteBlack.highlight
}

val NierDim: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.dim
    NierThemeMode.NIGHT -> NierPaletteNight.dim
    NierThemeMode.BLACK -> NierPaletteBlack.dim
}

val NierBlue: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.blue
    NierThemeMode.NIGHT -> NierPaletteNight.blue
    NierThemeMode.BLACK -> NierPaletteBlack.blue
}

val NierGreen: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.green
    NierThemeMode.NIGHT -> NierPaletteNight.green
    NierThemeMode.BLACK -> NierPaletteBlack.green
}

val NierAmber: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.amber
    NierThemeMode.NIGHT -> NierPaletteNight.amber
    NierThemeMode.BLACK -> NierPaletteBlack.amber
}

val NierRed: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.red
    NierThemeMode.NIGHT -> NierPaletteNight.red
    NierThemeMode.BLACK -> NierPaletteBlack.red
}

val NierPurple: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.purple
    NierThemeMode.NIGHT -> NierPaletteNight.purple
    NierThemeMode.BLACK -> NierPaletteBlack.purple
}

val NierMagenta: Color get() = when (ThemeManager.currentTheme) {
    NierThemeMode.REGULAR -> NierPaletteRegular.magenta
    NierThemeMode.NIGHT -> NierPaletteNight.magenta
    NierThemeMode.BLACK -> NierPaletteBlack.magenta
}

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
