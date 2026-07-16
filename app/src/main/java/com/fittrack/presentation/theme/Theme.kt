package com.fittrack.presentation.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// -- Colour palette ----------------------------------------------------------
val Primary      = Color(0xFFE50000)   // Neon green-teal
val PrimaryDark  = Color(0xFF00B377)
val Secondary    = Color(0xFF0D9488)
val Accent       = Color(0xFFFF6B35)   // Orange for calories/highlights
val Background   = Color(0xFF0A0A0F)   // Near-black
val Surface      = Color(0xFF131320)
val SurfaceVar   = Color(0xFF1C1C2E)
val OnBackground = Color(0xFFF0F0F5)
val OnSurface    = Color(0xFFE0E0EF)
val OnPrimary    = Color(0xFF001A0E)

// Muscle group colours
val MuscleChest       = Color(0xFFFF4D4D)
val MuscleBack        = Color(0xFF4D9FFF)
val MuscleShoulders   = Color(0xFFFFD700)
val MuscleArms        = Color(0xFF9B59B6)
val MuscleCore        = Color(0xFFFF8C00)
val MuscleLegs        = Color(0xFF2ECC71)
val MuscleGlutes      = Color(0xFFE91E63)
val MuscleCalves      = Color(0xFF00BCD4)
val MuscleInactive    = Color(0xFF2A2A3A)

private val DarkColorScheme = darkColorScheme(
    primary            = Primary,
    onPrimary          = OnPrimary,
    primaryContainer   = PrimaryDark,
    secondary          = Secondary,
    tertiary           = Accent,
    background         = Background,
    surface            = Surface,
    surfaceVariant     = SurfaceVar,
    onBackground       = OnBackground,
    onSurface          = OnSurface,
    onSurfaceVariant   = Color(0xFFAAAAAF),
    outline            = Color(0xFF3A3A4A),
    error              = Color(0xFFFF5252),
)

@Composable
fun FitTrackTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography   = Typography,
        content      = content
    )
}
