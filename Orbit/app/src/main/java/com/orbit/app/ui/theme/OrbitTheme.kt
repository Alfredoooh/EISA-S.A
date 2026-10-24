package com.orbit.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OrbitLightColors = lightColorScheme(
    primary = Color(0xFF2459C4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001A43),
    inversePrimary = Color(0xFFB1C5FF),
    secondary = Color(0xFF315DA8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E2FF),
    onSecondaryContainer = Color(0xFF001A43),
    tertiary = Color(0xFF006B80),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB8ECFF),
    onTertiaryContainer = Color(0xFF001F28),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191B23),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191B23),
    surfaceVariant = Color(0xFFE1E3EC),
    onSurfaceVariant = Color(0xFF444650),
    surfaceTint = Color(0xFF2459C4),
    inverseSurface = Color(0xFF2E3038),
    inverseOnSurface = Color(0xFFF0F0F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF747783),
    outlineVariant = Color(0xFFC4C6D0),
    scrim = Color(0xFF000000),
)

private val OrbitDarkColors = darkColorScheme(
    primary = Color(0xFFB3C8FF),
    onPrimary = Color(0xFF002E6A),
    primaryContainer = Color(0xFF234986),
    onPrimaryContainer = Color(0xFFDCE2FF),
    inversePrimary = Color(0xFF2459C4),
    secondary = Color(0xFFB5C7FF),
    onSecondary = Color(0xFF1A3264),
    secondaryContainer = Color(0xFF344A7E),
    onSecondaryContainer = Color(0xFFDCE2FF),
    tertiary = Color(0xFF8AD5E9),
    onTertiary = Color(0xFF003640),
    tertiaryContainer = Color(0xFF004E5B),
    onTertiaryContainer = Color(0xFFB5EDFF),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF444650),
    onSurfaceVariant = Color(0xFFC4C6D3),
    surfaceTint = Color(0xFFB3C8FF),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3038),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF8E909B),
    outlineVariant = Color(0xFF444650),
    scrim = Color(0xFF000000),
)

@Composable
fun OrbitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) OrbitDarkColors else OrbitLightColors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
