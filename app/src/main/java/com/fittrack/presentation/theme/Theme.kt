package com.fittrack.presentation.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// -- Paleta -------------------------------------------------------------
// Base gráfite/carvão com um verde-esmeralda contido como cor de assinatura
// e um dourado suave como acento — combinação premium, sem apelo "neon".
val Primary        = Color(0xFF2FBE8F)   // Esmeralda contido (ação principal)
val PrimaryDark    = Color(0xFF1C8F6B)
val PrimaryMuted   = Color(0xFF1B3A32)   // fundo de badges/tints sobre Primary
val Secondary      = Color(0xFF7C8CF8)   // Indigo suave (dados secundários)
val Accent         = Color(0xFFCFA96A)   // Dourado fosco (destaques/calorias)

val Background     = Color(0xFF0B0D10)   // Grafite quase preto
val Surface        = Color(0xFF14171C)
val SurfaceVar     = Color(0xFF1B1F26)
val SurfaceElevated= Color(0xFF222630)   // cartões "flutuantes" (modais, destaque)
val OutlineSubtle  = Color(0xFF2A2E37)   // bordas discretas em cartões

val OnBackground   = Color(0xFFEDEEF1)
val OnSurface      = Color(0xFFE3E5EA)
val OnSurfaceMuted  = Color(0xFF9AA0AC)
val OnPrimary      = Color(0xFF04140F)

// Cores por grupo muscular — levemente dessaturadas para manter a
// elegância do tema escuro (mantém a distinção, perde o "neon").
val MuscleChest       = Color(0xFFE0625B)
val MuscleBack        = Color(0xFF5C8FD6)
val MuscleShoulders   = Color(0xFFD8B64C)
val MuscleArms        = Color(0xFF9670BE)
val MuscleCore        = Color(0xFFD98A47)
val MuscleLegs        = Color(0xFF4FAE7C)
val MuscleGlutes      = Color(0xFFCB6690)
val MuscleCalves      = Color(0xFF3FA8AE)
val MuscleInactive    = Color(0xFF2A2A3A)

private val DarkColorScheme = darkColorScheme(
    primary            = Primary,
    onPrimary          = OnPrimary,
    primaryContainer   = PrimaryMuted,
    onPrimaryContainer = Primary,
    secondary          = Secondary,
    tertiary           = Accent,
    background         = Background,
    surface            = Surface,
    surfaceVariant     = SurfaceVar,
    surfaceTint        = Color.Transparent,
    onBackground       = OnBackground,
    onSurface          = OnSurface,
    onSurfaceVariant   = OnSurfaceMuted,
    outline            = OutlineSubtle,
    outlineVariant     = Color(0xFF20232B),
    error              = Color(0xFFE0665F),
)

@Composable
fun FitTrackTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Background.toArgb()
            window.navigationBarColor = Background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography   = Typography,
        content      = content
    )
}
