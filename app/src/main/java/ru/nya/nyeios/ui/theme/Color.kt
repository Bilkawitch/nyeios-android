package ru.nya.nyeios.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// NieR: Automata / YoRHa OS Color Palette
// ==========================================

// Core Backgrounds & Surfaces (Warm Desert Olive / Sand)
val NierBg = Color(0xFFCAC6A8)          // Main application canvas (--bg)
val NierPanel = Color(0xFFC2BE9F)       // Tab bar, Navigation bar, Cards (--panel)
val NierPanelAlt = Color(0xFFD0CCB0)    // Secondary panel, alternating rows (--panel-alt)
val NierSurface = Color(0xFFB5B197)      // Recessed areas, map corridors

// Contrasting Dark Elements (Military Charcoal / Warm Dark)
val NierDark = Color(0xFF3A342B)         // Primary text, heavy borders, solid badges (--dark)
val NierDarkSecondary = Color(0xFF4D4438)// Secondary dark, borders, dots (--dark2)
val NierBorder = Color(0xFF7A7060)       // Standard borders, dividers (--border)
val NierBorderLight = Color(0xFF9A9280)  // Subtle separators, slot borders (--border-lt)

// Selection & Highlights
val NierSelection = Color(0xFF4D4438)    // Selected item fill (--sel)
val NierSelectionText = Color(0xFFCAC6A8)// Text on selected item (--sel-text)
val NierHighlight = Color(0xFF8A8060)    // Muted accent (--hl)
val NierDim = Color(0xFF7A7060)          // Secondary labels, timestamps (--dim)

// Semantic Lesson & Status Accents (NieR Tuned)
val NierBlue = Color(0xFF4A90D9)         // Lecture, primary accent, info badge (--blue)
val NierGreen = Color(0xFF4CAF7A)        // Practice, live status, credit/success (--green)
val NierAmber = Color(0xFFD9A040)        // Lab, warning, diff credit (--amber)
val NierRed = Color(0xFFD95555)          // Exam, error, critical alert (--red)
val NierPurple = Color(0xFF9C6DD9)       // Other disciplines, seminar (--purple)
val NierMagenta = Color(0xFFD946EF)      // Multi-floor navigation connector (--magenta)

// Compatibility Mappings to existing code tokens
val ObsidianBg = NierBg
val ObsidianSurface = NierPanel
val ObsidianCard = NierPanelAlt
val ObsidianCardSelected = NierSelection
val ObsidianBorder = NierBorder
val ObsidianBorderActive = NierDark

val TextPrimary = NierDark
val TextSecondary = NierDarkSecondary
val TextMuted = NierDim

val LectureBlue = NierBlue
val LectureBlueBg = Color(0x334A90D9)

val PracticeGreen = NierGreen
val PracticeGreenBg = Color(0x334CAF7A)

val LabAmber = NierAmber
val LabAmberBg = Color(0x33D9A040)

val ExamRed = NierRed
val ExamRedBg = Color(0x33D95555)

val OtherPurple = NierPurple
val OtherPurpleBg = Color(0x339C6DD9)

val LiveBadgeColor = NierGreen
val LiveGlowBorder = NierGreen
