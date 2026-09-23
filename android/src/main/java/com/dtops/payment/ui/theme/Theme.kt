package com.dtops.payment.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    secondary = NavyBlue,
    tertiary = LightGray,
    background = NavyBlue,
    surface = DarkGray,
    onPrimary = SurfaceWhite,
    onSecondary = SurfaceWhite,
    onTertiary = NavyBlue,
    onBackground = SurfaceWhite,
    onSurface = SurfaceWhite,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = NavyBlue,
    secondary = EmeraldGreen,
    tertiary = DarkGray,
    background = LightGray,
    surface = SurfaceWhite,
    onPrimary = SurfaceWhite,
    onSecondary = SurfaceWhite,
    onTertiary = SurfaceWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = ErrorRed
)

@Composable
fun DtopsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

