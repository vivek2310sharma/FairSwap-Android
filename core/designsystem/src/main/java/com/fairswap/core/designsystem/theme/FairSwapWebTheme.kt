package com.fairswap.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme

// Web Tailwind Colors mapped
val Emerald500 = Color(0xFF10B981)
val Gray300 = Color(0xFFD1D5DB)

// Web Tailwind Colors mapped for Notice
val Emerald50 = Color(0xFFECFDF5)
val Emerald200 = Color(0xFFA7F3D0)
val Emerald700 = Color(0xFF047857)
val Emerald800 = Color(0xFF065F46)

val Red50 = Color(0xFFFEF2F2)
val Red200 = Color(0xFFFECACA)
val Red700 = Color(0xFFB91C1C)

data class ExtendedColors(
    val warningBg: Color,
    val warningBorder: Color,
    val warningText: Color,
    val errorBg: Color,
    val errorBorder: Color,
    val errorText: Color,
    val successBg: Color,
    val successBorder: Color,
    val successText: Color,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        warningBg = Color.Unspecified, warningBorder = Color.Unspecified, warningText = Color.Unspecified,
        errorBg = Color.Unspecified, errorBorder = Color.Unspecified, errorText = Color.Unspecified,
        successBg = Color.Unspecified, successBorder = Color.Unspecified, successText = Color.Unspecified,
    )
}

val LocalWebThemeMode = staticCompositionLocalOf<Boolean?> { null }

private val WebExtendedLight = ExtendedColors(
    warningBg = NoticeBgLight,
    warningBorder = NoticeBorderLight,
    warningText = NoticeTextLight,
    errorBg = Red50,          // red-50
    errorBorder = Red200,     // red-200
    errorText = Red700,       // red-700
    successBg = Emerald50,    // emerald-50
    successBorder = Emerald200, // emerald-200
    successText = Emerald700, // emerald-700
)

// Since Web doesn't explicitly define dark mode variants for red/emerald notice banners,
// we adapt them to match the dark scheme context or reuse Light colors if acceptable.
// We map native adaptation for dark colors:
private val WebExtendedDark = ExtendedColors(
    warningBg = NoticeBgDark,
    warningBorder = NoticeBorderDark,
    warningText = NoticeTextDark,
    errorBg = ErrorBgDark,
    errorBorder = ErrorBorderDark,
    errorText = ErrorDark,
    successBg = Brand900,
    successBorder = Brand700,
    successText = Brand100,
)

private val WebLightColors = lightColorScheme(
    primary = Color(0xFF073D72),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3F0FE),
    onPrimaryContainer = Color(0xFF062C55),
    secondary = Color(0xFF006B39),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCFCE7),
    onSecondaryContainer = Color(0xFF034E2A),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF101D2B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101D2B),
    surfaceVariant = Color(0xFFEAF0F4),
    onSurfaceVariant = Color(0xFF445363),
    outline = Color(0xFF8A9AAA),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
)

private val WebDarkColors = darkColorScheme(
    primary = Color(0xFFB5D7FF),
    onPrimary = Color(0xFF002F5C),
    primaryContainer = Color(0xFF164F83),
    onPrimaryContainer = Color(0xFFE6F2FF),
    secondary = Color(0xFF60E49B),
    onSecondary = Color(0xFF063A21),
    secondaryContainer = Color(0xFF0D5732),
    onSecondaryContainer = Color(0xFFB5F5D0),
    background = Color(0xFF111923),
    onBackground = Color(0xFFF4F7FA),
    surface = Color(0xFF1A2634),
    onSurface = Color(0xFFF4F7FA),
    surfaceVariant = Color(0xFF263746),
    onSurfaceVariant = Color(0xFFC6D2DF),
    outline = Color(0xFF8996A3),
)

@Composable
fun FairSwapWebTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val explicitMode = darkTheme ?: LocalWebThemeMode.current
    val isDark = explicitMode ?: isSystemInDarkTheme()
    val extendedColors = if (isDark) WebExtendedDark else WebExtendedLight
    
    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
        LocalWebThemeMode provides explicitMode
    ) {
        MaterialTheme(
            colorScheme = if (isDark) WebDarkColors else WebLightColors,
            typography = FairSwapTypography,
            content = content,
        )
    }
}
