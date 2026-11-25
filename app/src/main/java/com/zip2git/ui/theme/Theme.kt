package com.zip2git.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** Brand palette, taken from the launcher icon gradient. */
private val BrandBlue = Color(0xFF4C6BFF)
private val BrandIndigo = Color(0xFF3A4FD8)
private val BrandCyan = Color(0xFF22C1DC)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF001158),
    secondary = BrandCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2F4FA),
    onSecondaryContainer = Color(0xFF00202A),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF14161C),
    surface = Color.White,
    onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0xFFEDEFF6),
    onSurfaceVariant = Color(0xFF5A6070),
    outline = Color(0xFFC7CBD9),
    outlineVariant = Color(0xFFE2E5EF),
    error = Color(0xFFC0392B),
    onError = Color.White,
    errorContainer = Color(0xFFFDE5E2),
    onErrorContainer = Color(0xFF6B1208),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2F6BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1D3C8F),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFF5FD6E8),
    onSecondary = Color(0xFF00323B),
    secondaryContainer = Color(0xFF004E5A),
    onSecondaryContainer = Color(0xFFD2F4FA),
    background = Color(0xFF0F131A),
    onBackground = Color(0xFFE7E9F0),
    surface = Color(0xFF1B2130),
    onSurface = Color(0xFFE7E9F0),
    surfaceVariant = Color(0xFF232734),
    onSurfaceVariant = Color(0xFF9AA4B2),
    outline = Color(0xFF3B4152),
    outlineVariant = Color(0xFF2A2F3C),
    error = Color(0xFFFF897D),
    onError = Color(0xFF5A0F06),
    errorContainer = Color(0xFF7A1F14),
    onErrorContainer = Color(0xFFFFDAD4),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)

/**
 * Applies the Zip2Git colour scheme.
 *
 * [darkTheme] is driven by the persisted preference in
 * [com.zip2git.data.local.ThemeStore], not by the system setting, so the in-app
 * toggle wins and survives restarts.
 */
@Composable
fun Zip2GitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}

/** Colour used for the indigo accent on the hero header. */
val HeroIndigo: Color = BrandIndigo