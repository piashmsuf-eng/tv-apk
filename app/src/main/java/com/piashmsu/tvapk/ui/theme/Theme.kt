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
import com.piashmsu.tvapk.data.ThemePalette

// Default Vibe palette — neon/holographic: electric purple primary, cyan
// secondary, hot-pink tertiary, deep midnight surfaces with a hint of
// indigo. Designed to feel premium and modern under dim lighting.
private val Neon = Color(0xFFA855F7)
private val Cyan = Color(0xFF22D3EE)
private val Pink = Color(0xFFF472B6)
private val Surface0 = Color(0xFF050616)
private val Surface1 = Color(0xFF0B0E22)
private val Surface2 = Color(0xFF161A36)
private val OnSurface = Color(0xFFE7E9FA)

val GradientHero = Brush.linearGradient(
    listOf(Color(0xFFA855F7), Color(0xFFF472B6), Color(0xFF22D3EE))
)

val GradientGlass = Brush.verticalGradient(
    listOf(Color(0x261D1740), Color(0x1A0B0E22))
)

val GradientAmber = Brush.linearGradient(
    listOf(Color(0xFFFCD34D), Color(0xFFF59E0B))
)

/**
 * Per-palette gradient and color choices. Each variant has the same
 * structure as the default scheme but swaps primaries / surfaces so the
 * whole UI feels distinct without needing per-screen overrides.
 */
data class TvApkPaletteSpec(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface0: Color,
    val surface1: Color,
    val surface2: Color,
    val gradient: Brush,
)

private val DefaultSpec = TvApkPaletteSpec(
    primary = Neon,
    secondary = Cyan,
    tertiary = Pink,
    surface0 = Surface0,
    surface1 = Surface1,
    surface2 = Surface2,
    gradient = Brush.verticalGradient(listOf(Color(0xFF12082A), Color(0xFF050616))),
)

private val AmoledSpec = TvApkPaletteSpec(
    primary = Color(0xFFE5E7EB),
    secondary = Color(0xFF94A3B8),
    tertiary = Color(0xFFCBD5F5),
    surface0 = Color(0xFF000000),
    surface1 = Color(0xFF050505),
    surface2 = Color(0xFF0A0A0A),
    gradient = Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF000000))),
)

private val SunsetSpec = TvApkPaletteSpec(
    primary = Color(0xFFFB923C),
    secondary = Color(0xFFFACC15),
    tertiary = Color(0xFFEF4444),
    surface0 = Color(0xFF21130A),
    surface1 = Color(0xFF2D1A0F),
    surface2 = Color(0xFF3A2316),
    gradient = Brush.verticalGradient(listOf(Color(0xFF3D1B0B), Color(0xFF1B0A04))),
)

private val OceanSpec = TvApkPaletteSpec(
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFF22D3EE),
    tertiary = Color(0xFF60A5FA),
    surface0 = Color(0xFF02101A),
    surface1 = Color(0xFF051F2C),
    surface2 = Color(0xFF073241),
    gradient = Brush.verticalGradient(listOf(Color(0xFF062F44), Color(0xFF02101A))),
)

private fun specFor(palette: ThemePalette): TvApkPaletteSpec = when (palette) {
    ThemePalette.AmoledBlack -> AmoledSpec
    ThemePalette.SunsetOrange -> SunsetSpec
    ThemePalette.OceanBlue -> OceanSpec
    else -> DefaultSpec
}

private fun darkSchemeFor(spec: TvApkPaletteSpec) = darkColorScheme(
    primary = spec.primary,
    onPrimary = Color.White,
    secondary = spec.secondary,
    onSecondary = Color.Black,
    tertiary = spec.tertiary,
    onTertiary = Color.White,
    background = spec.surface0,
    onBackground = OnSurface,
    surface = spec.surface1,
    onSurface = OnSurface,
    surfaceVariant = spec.surface2,
    onSurfaceVariant = OnSurface.copy(alpha = 0.85f),
)

private val LightScheme = lightColorScheme(
    primary = Neon,
    secondary = Cyan,
    tertiary = Pink,
)

/** Composition-local override for the per-palette gradient background. */
val GradientBackground: Brush
    get() = currentGradient ?: DefaultSpec.gradient

@Volatile
private var currentGradient: Brush? = null

@Composable
fun TvApkTheme(
    palette: ThemePalette = ThemePalette.DefaultVibe,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // keep brand identity by default
    content: @Composable () -> Unit,
) {
    val spec = specFor(palette)
    currentGradient = spec.gradient
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicDarkColorScheme(context)
        }
        darkTheme -> darkSchemeFor(spec)
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
