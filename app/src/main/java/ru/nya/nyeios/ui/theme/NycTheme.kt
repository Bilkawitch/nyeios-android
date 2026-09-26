package ru.nya.nyeios.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape

// NyC-modern (Night Skeuomorph) tokens from mockup nyeios_redesign.html.
// All helpers return legacy values for YoRHa themes, so old themes are pixel-identical.

val isNycModern: Boolean get() = ThemeManager.currentTheme == NierThemeMode.NYC_MODERN

// Fixed accent tokens (only used on the NyC-modern branch).
val NycCyan = Color(0xFF4ADEDE)
val NycCyanHi = Color(0xFF7FF2F0)
val NycCyanDk = Color(0xFF2FB4B6)

// Mockup geometry (px at 360dp width == dp).
object NycShapes {
    val card = 16.dp
    val well = 14.dp
    val chip = 9.dp
    val button = 11.dp
    val logo = 13.dp
    val avatar = 12.dp
    val pill = 11.dp
    val mini = 9.dp
    val sheetTop = 24.dp
}

// Generic rect: square on YoRHa, softly rounded on NyC-modern.
fun appRectShape(): Shape =
    if (isNycModern) RoundedCornerShape(12.dp) else RoundedCornerShape(0.dp)

// Small chips/badges: 2dp on YoRHa, 10dp on NyC-modern.
fun appSmallShape(): Shape =
    if (isNycModern) RoundedCornerShape(10.dp) else RoundedCornerShape(2.dp)

// Pills: 3dp on YoRHa, 8dp on NyC-modern.
fun appPillShape(): Shape =
    if (isNycModern) RoundedCornerShape(8.dp) else RoundedCornerShape(3.dp)

// Bottom sheets: sharp top on YoRHa, 24dp top on NyC-modern.
fun appSheetShape(): Shape =
    if (isNycModern) RoundedCornerShape(topStart = NycShapes.sheetTop, topEnd = NycShapes.sheetTop)
    else RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)

// Cards: 0dp on YoRHa (legacy NierCard), 16dp on NyC-modern.
fun appCardShape(): Shape =
    if (isNycModern) RoundedCornerShape(NycShapes.card) else RoundedCornerShape(0.dp)

fun appLogoShape(): Shape =
    if (isNycModern) RoundedCornerShape(NycShapes.logo) else RoundedCornerShape(0.dp)

fun appAvatarShape(): Shape =
    if (isNycModern) RoundedCornerShape(NycShapes.avatar) else RoundedCornerShape(0.dp)

fun appChipShape(): Shape =
    if (isNycModern) RoundedCornerShape(NycShapes.chip) else RoundedCornerShape(0.dp)

fun appButtonShape(): Shape =
    if (isNycModern) RoundedCornerShape(NycShapes.button) else RoundedCornerShape(0.dp)

val NycCircle: Shape get() = CircleShape

// Typography approximating the mockup (Manrope sans + JetBrains Mono).
// TODO: bundle manrope_* / jetbrains_mono_* in res/font and point these families at them
// for pixel-exact mockup fidelity; current fallback is system sans/monospace with
// mockup weights and letter spacings.
val NycSansFamily = FontFamily.Default
val NycMonoFamily = FontFamily.Monospace

val NycTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = NycSansFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 25.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = NycSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = NycSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = NycSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp
    ),
    titleSmall = TextStyle(
        fontFamily = NycSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = NycMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = NycMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp
    ),
    bodySmall = TextStyle(
        fontFamily = NycMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        letterSpacing = 0.5.sp
    ),
    labelLarge = TextStyle(
        fontFamily = NycMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        letterSpacing = 1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = NycMonoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 8.sp,
        letterSpacing = 1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = NycMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 8.sp,
        letterSpacing = 1.sp
    )
)

// Selection highlight: NierBlue on YoRHa, mockup cyan on NyC-modern
// (selected day bars, bottom-nav active icons, today marker).
fun selectionAccent(): Color = if (isNycModern) NycCyan else NierBlue

// Theme apply helper: themes update dynamically via Compose state.
fun applyThemeWithRestart(context: Context, mode: NierThemeMode) {
    ThemeManager.setTheme(context, mode)
}
