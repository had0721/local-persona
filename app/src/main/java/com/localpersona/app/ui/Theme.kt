package com.localpersona.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * 蓝白、克制、工具感：白底 + 一条冷蓝作为唯一强调色，
 * 分隔全部用 1dp 细线而不是厚重的色块。
 */

val Accent = Color(0xFF1A6BFF)
val AccentSoft = Color(0xFFEAF1FF)
val Line = Color(0xFFE3E9F2)
val LineDark = Color(0xFF1E2A3B)
val Ink = Color(0xFF0B1526)
val InkMuted = Color(0xFF5C6E88)

private val Light = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Color(0xFF0A3A8F),
    secondary = Color(0xFF4B5D78),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF5F8FD),
    onSurfaceVariant = InkMuted,
    outline = Line,
    outlineVariant = Color(0xFFEDF1F7),
    error = Color(0xFFC62828),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5B92FF),
    onPrimary = Color(0xFF04101F),
    primaryContainer = Color(0xFF12294A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF93A7C4),
    background = Color(0xFF080C13),
    onBackground = Color(0xFFE7EDF6),
    surface = Color(0xFF0D131C),
    onSurface = Color(0xFFE7EDF6),
    surfaceVariant = Color(0xFF141C28),
    onSurfaceVariant = Color(0xFF9DAEC5),
    outline = LineDark,
    outlineVariant = Color(0xFF16202D),
    error = Color(0xFFFF8A80),
)

private val Typography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(
        fontSize = 10.5.sp,
        letterSpacing = 0.6.sp,
        fontWeight = FontWeight.Medium,
    ),
)

val Mono: FontFamily = FontFamily.Monospace

@Composable
fun PersonaTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = Typography,
        content = content,
    )
}
