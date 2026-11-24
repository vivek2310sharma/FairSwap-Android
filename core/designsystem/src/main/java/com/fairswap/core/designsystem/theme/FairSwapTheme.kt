package com.fairswap.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FairSwapLightColors = lightColorScheme(
    primary = FairSwapPrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F0FE),
    onPrimaryContainer = FairSwapPrimaryNavy,
    secondary = FairSwapDeepGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCFCE7),
    onSecondaryContainer = Color(0xFF034E2A),
    tertiary = FairSwapCyanAccent,
    background = FairSwapMainBackground,
    surface = FairSwapSurface,
    onBackground = FairSwapPrimaryText,
    onSurface = FairSwapPrimaryText,
    surfaceVariant = Color(0xFFEAF0F4),
    onSurfaceVariant = FairSwapSecondaryText,
    outline = FairSwapSubtleBorder,
)

private val FairSwapDarkColors = darkColorScheme(
    primary = Color(0xFF4299FF),
    onPrimary = Color(0xFF001D4A),
    primaryContainer = FairSwapPrimaryNavy,
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = FairSwapBrightGreen,
    onSecondary = Color(0xFF00381A),
    secondaryContainer = FairSwapDeepGreen,
    onSecondaryContainer = Color(0xFFB5F5D0),
    tertiary = FairSwapCyanAccent,
    background = Color(0xFF0B132B),
    surface = Color(0xFF1C2541),
    onBackground = Color(0xFFF5F8FC),
    onSurface = Color(0xFFF5F8FC),
    surfaceVariant = Color(0xFF2B3A55),
    onSurfaceVariant = Color(0xFFC6D2DF),
    outline = Color(0xFF4A5568),
)

@Composable
fun FairSwapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) FairSwapDarkColors else FairSwapLightColors,
        typography = FairSwapTypography,
        content = content,
    )
}
