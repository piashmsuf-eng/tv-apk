package com.piashmsu.tvapk.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// "Vibe edition" — neon/holographic palette: electric purple primary,
// cyan secondary, hot-pink tertiary, deep midnight surfaces with a hint
// of indigo. Designed to feel premium and modern under dim lighting.
private val Neon = Color(0xFFA855F7)
private val Cyan = Color(0xFF22D3EE)
private val Pink = Color(0xFFF472B6)
private val Surface0 = Color(0xFF050616)
private val Surface1 = Color(0xFF0B0E22)
private val Surface2 = Color(0xFF161A36)
private val OnSurface = Color(0xFFE7E9FA)

val GradientBackground = Brush.verticalGradient(
    listOf(Color(0xFF12082A), Color(0xFF050616))
)

val GradientHero = Brush.linearGradient(
    listOf(Color(0xFFA855F7), Color(0xFFF472B6), Color(0xFF22D3EE))
)

val GradientGlass = Brush.verticalGradient(
    listOf(Color(0x261D1740), Color(0x1A0B0E22))
)

val GradientAmber = Brush.linearGradient(
    listOf(Color(0xFFFCD34D), Color(0xFFF59E0B))
)

private val DarkScheme = darkColorScheme(
    primary = Neon,
    onPrimary = Color.White,
    secondary = Cyan,
    onSecondary = Color.Black,
    tertiary = Pink,
    onTertiary = Color.White,
    background = Surface0,
    onBackground = OnSurface,
    surface = Surface1,
    onSurface = OnSurface,
    surfaceVariant = Surface2,
    onSurfaceVariant = OnSurface.copy(alpha = 0.85f),
)

private val LightScheme = lightColorScheme(
    primary = Neon,
    secondary = Cyan,
    tertiary = Pink,
)

@Composable
fun TvApkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // keep brand identity by default
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> {
            val context = LocalContext.current
            dynamicDarkColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = colors, typography = TvApkTypography, content = content)
}
