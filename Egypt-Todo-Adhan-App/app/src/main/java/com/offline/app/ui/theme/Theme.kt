package com.offline.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Destination:
 * app/src/main/java/com/offline/app/ui/theme/Theme.kt
 *
 * Material 3 theme wrapper for the 100% offline prayer-times app.
 *
 * It maps the raw tokens from Color.kt and the Arabic-first scale from Type.kt
 * onto Material's role-based [androidx.compose.material3.ColorScheme], then
 * switches cleanly between the light and dark palettes.
 *
 * Design intent:
 *  - Light mode: near-white sage canvas, deep-emerald primary, gold accent.
 *  - Dark mode: night-navy canvas, bright-emerald primary, brighter gold accent.
 *  - System-bar icons are flipped automatically to stay legible on each palette.
 *
 * Dynamic color (Material You) is available but OFF by default, so the brand
 * emerald/gold identity stays consistent on every device.
 */

/**
 * The light palette: a calm sage surface with a deep-emerald identity color and a
 * restrained gold accent.
 */
val LightColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = PureWhite,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = OnEmeraldContainerLight,
    inversePrimary = EmeraldBright,

    secondary = EmeraldMutedLight,
    onSecondary = PureWhite,
    secondaryContainer = EmeraldMutedContainerLight,
    onSecondaryContainer = OnEmeraldMutedContainerLight,

    tertiary = GoldTertiaryLight,
    onTertiary = PureWhite,
    tertiaryContainer = GoldContainerLight,
    onTertiaryContainer = OnGoldContainerLight,

    background = SurfaceLight,
    onBackground = OnSurfaceLight,

    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = MutedSurfaceVariantLight,
    onSurfaceVariant = OnMutedSurfaceVariantLight,
    surfaceTint = EmeraldDeep,

    inverseSurface = NavyNight,
    inverseOnSurface = InverseOnSurfaceLight,

    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,

    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    scrim = ScrimBlack,

    surfaceBright = SurfaceBrightLight,
    surfaceDim = SurfaceDimLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainerLowest = SurfaceContainerLowestLight
)

/**
 * The dark palette: a night-navy canvas with a brightened emerald and gold that
 * keep contrast against the deep background.
 */
val DarkColorScheme = darkColorScheme(
    primary = EmeraldBright,
    onPrimary = OnPrimaryDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = OnEmeraldContainerDark,
    inversePrimary = EmeraldDeep,

    secondary = EmeraldMutedDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = EmeraldMutedContainerDark,
    onSecondaryContainer = OnEmeraldMutedContainerDark,

    tertiary = GoldTertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = GoldContainerDark,
    onTertiaryContainer = OnGoldContainerDark,

    background = NavyNight,
    onBackground = OnNavyNight,

    surface = NavyNight,
    onSurface = OnSurfaceDark,
    surfaceVariant = MutedSurfaceVariantDark,
    onSurfaceVariant = OnMutedSurfaceVariantDark,
    surfaceTint = EmeraldBright,

    inverseSurface = NavyInverseSurface,
    inverseOnSurface = InverseOnSurfaceDark,

    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,

    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    scrim = ScrimBlack,

    surfaceBright = SurfaceBrightDark,
    surfaceDim = SurfaceDimDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainerLowest = SurfaceContainerLowestDark
)

/**
 * Root theme for the app. Wrap every screen in exactly one [OfflineAppTheme].
 *
 * @param darkTheme    when true the night-navy palette is used; defaults to the
 *                     system setting.
 * @param dynamicColor when true and running on Android 12+ (API 31+), Material
 *                     You derives the palette from the user's wallpaper. Defaults
 *                     to false so the emerald/gold identity is always preserved.
 * @param content      the composable content to theme.
 */
@Composable
fun OfflineAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
