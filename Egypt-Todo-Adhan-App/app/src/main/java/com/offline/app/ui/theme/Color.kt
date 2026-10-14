package com.offline.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Destination:
 * app/src/main/java/com/offline/app/ui/theme/Color.kt
 *
 * Raw color tokens for the "Islamic-modern" design system.
 *
 * The palette is deliberately editorially organised (brand -> surfaces -> text ->
 * state) instead of mirroring Material's role names, so every token below is a
 * plain [Color] with no semantic assumption attached. The Material role mapping
 * happens once, in Theme.kt, via `lightColorScheme` / `darkColorScheme`.
 *
 * Core idea:
 *  - Deep emerald is the identity color (prayer, primary actions).
 *  - Night navy is the dark canvas (calm, readable at night).
 *  - Muted gray / sage provides low-noise surfaces and outlines.
 *  - Gold is a sparing accent (highlights, current prayer, gold leaf motifs).
 *
 * All values are fully opaque ARGB (0xFF......) and need no network or asset.
 */

// =====================================================================
// Primary — deep emerald
// =====================================================================

/** Primary emerald for light surfaces. */
val EmeraldDeep: Color = Color(0xFF0B6E4F)

/** Slightly darker emerald for pressed states on light surfaces. */
val EmeraldDeepPressed: Color = Color(0xFF075039)

/** Bright emerald that keeps AA contrast on the night-navy canvas. */
val EmeraldBright: Color = Color(0xFF78D6B2)

/** Light emerald tint used as the light primary container. */
val EmeraldContainerLight: Color = Color(0xFFB6EBD5)

/** Dark emerald used as the dark primary container. */
val EmeraldContainerDark: Color = Color(0xFF00513A)

/** Text/icon color on the light primary container. */
val OnEmeraldContainerLight: Color = Color(0xFF002116)

/** Text/icon color on the dark primary container. */
val OnEmeraldContainerDark: Color = Color(0xFF9CF3CE)

/** Text/icon color on the dark primary. */
val OnPrimaryDark: Color = Color(0xFF003825)

// =====================================================================
// Secondary — muted emerald / teal
// =====================================================================

/** Secondary for light surfaces. */
val EmeraldMutedLight: Color = Color(0xFF2E6B5A)

/** Light secondary container. */
val EmeraldMutedContainerLight: Color = Color(0xFFB2EFD9)

/** Text/icon color on the light secondary container. */
val OnEmeraldMutedContainerLight: Color = Color(0xFF00201A)

/** Secondary for dark surfaces. */
val EmeraldMutedDark: Color = Color(0xFF97D2BE)

/** Dark secondary container. */
val EmeraldMutedContainerDark: Color = Color(0xFF104E3F)

/** Text/icon color on the dark secondary container. */
val OnEmeraldMutedContainerDark: Color = Color(0xFFB2EFD9)

/** Text/icon color on the dark secondary. */
val OnSecondaryDark: Color = Color(0xFF00382B)

// =====================================================================
// Navy — night canvas
// =====================================================================

/** The dark background / surface color. */
val NavyNight: Color = Color(0xFF0A1622)

/** A slightly lifted navy for elevated dark surfaces. */
val NavyNightElevated: Color = Color(0xFF12202E)

/** Primary text/icon color on the navy canvas. */
val OnNavyNight: Color = Color(0xFFE2E6E1)

/** Inverse surface used in the dark scheme (light chip on dark). */
val NavyInverseSurface: Color = Color(0xFFE2E6E1)

/** Inverse text on the light inverse surface (light scheme). */
val InverseOnSurfaceLight: Color = Color(0xFFECF1EC)

/** Inverse text on the light inverse surface (dark scheme). */
val InverseOnSurfaceDark: Color = Color(0xFF10202C)

// =====================================================================
// Muted gray / sage — surfaces, variants, outlines
// =====================================================================

/** Light surface variant (soft sage). */
val MutedSurfaceVariantLight: Color = Color(0xFFDCE5DF)

/** Text/icon color on the light surface variant. */
val OnMutedSurfaceVariantLight: Color = Color(0xFF404943)

/** Dark surface variant (deep slate). */
val MutedSurfaceVariantDark: Color = Color(0xFF24323E)

/** Text/icon color on the dark surface variant. */
val OnMutedSurfaceVariantDark: Color = Color(0xFFBCC9C3)

/** Outline for light surfaces. */
val OutlineLight: Color = Color(0xFF707972)

/** Outline for dark surfaces. */
val OutlineDark: Color = Color(0xFF86948E)

/** Subtle divider outline for light surfaces. */
val OutlineVariantLight: Color = Color(0xFFC0C9C2)

/** Subtle divider outline for dark surfaces. */
val OutlineVariantDark: Color = Color(0xFF404943)

// =====================================================================
// Gold — accent
// =====================================================================

/** Base gold accent. */
val GoldAccent: Color = Color(0xFFC9A227)

/** Brighter gold that reads well on dark surfaces. */
val GoldAccentDark: Color = Color(0xFFE8C766)

/** Tertiary (gold) for light surfaces, darkened for contrast. */
val GoldTertiaryLight: Color = Color(0xFF7A5C00)

/** Dark tertiary (gold). */
val GoldTertiaryDark: Color = Color(0xFFE8C766)

/** Light gold container. */
val GoldContainerLight: Color = Color(0xFFFFE08A)

/** Text/icon color on the light gold container. */
val OnGoldContainerLight: Color = Color(0xFF241A00)

/** Dark gold container. */
val GoldContainerDark: Color = Color(0xFF5C4600)

/** Text/icon color on the dark gold container. */
val OnGoldContainerDark: Color = Color(0xFFFFE08A)

/** Text/icon color on the dark tertiary. */
val OnTertiaryDark: Color = Color(0xFF3F2E00)

// =====================================================================
// Surfaces and text
// =====================================================================

/** Light background / surface (near-white with a faint green cast). */
val SurfaceLight: Color = Color(0xFFF7FAF8)

/** Primary text/icon color on light surfaces. */
val OnSurfaceLight: Color = Color(0xFF191C1A)

/** Primary text/icon color on dark surfaces. */
val OnSurfaceDark: Color = Color(0xFFE3E9E4)

// =====================================================================
// Error / state
// =====================================================================

/** Error color for light surfaces. */
val ErrorLight: Color = Color(0xFFBA1A1A)

/** Text/icon color on the light error. */
val OnErrorLight: Color = Color(0xFFFFFFFF)

/** Light error container. */
val ErrorContainerLight: Color = Color(0xFFFFDAD6)

/** Text/icon color on the light error container. */
val OnErrorContainerLight: Color = Color(0xFF410002)

/** Error color for dark surfaces. */
val ErrorDark: Color = Color(0xFFFFB4AB)

/** Text/icon color on the dark error. */
val OnErrorDark: Color = Color(0xFF690005)

/** Dark error container. */
val ErrorContainerDark: Color = Color(0xFF93000A)

/** Text/icon color on the dark error container. */
val OnErrorContainerDark: Color = Color(0xFFFFDAD6)

// =====================================================================
// Surface containers (Material 3 tonal surface roles)
// =====================================================================

/** Light surface container level 0 (lowest elevation). */
val SurfaceContainerLowestLight: Color = Color(0xFFFFFFFF)

/** Light surface container level 1. */
val SurfaceContainerLowLight: Color = Color(0xFFF1F5F1)

/** Light surface container level 2 (default cards). */
val SurfaceContainerLight: Color = Color(0xFFECF0EC)

/** Light surface container level 3. */
val SurfaceContainerHighLight: Color = Color(0xFFE6EAE6)

/** Light surface container level 4 (menus, sheets). */
val SurfaceContainerHighestLight: Color = Color(0xFFE0E5E0)

/** Light dim surface (for recessed areas). */
val SurfaceDimLight: Color = Color(0xFFD7DCD8)

/** Light bright surface (for raised emphasis). */
val SurfaceBrightLight: Color = Color(0xFFF7FAF8)

/** Dark surface container level 0 (lowest elevation). */
val SurfaceContainerLowestDark: Color = Color(0xFF050E17)

/** Dark surface container level 1. */
val SurfaceContainerLowDark: Color = Color(0xFF12202E)

/** Dark surface container level 2 (default cards). */
val SurfaceContainerDark: Color = Color(0xFF16252F)

/** Dark surface container level 3. */
val SurfaceContainerHighDark: Color = Color(0xFF20303C)

/** Dark surface container level 4 (menus, sheets). */
val SurfaceContainerHighestDark: Color = Color(0xFF2A3B48)

/** Dark dim surface (for recessed areas). */
val SurfaceDimDark: Color = Color(0xFF0A1622)

/** Dark bright surface (for raised emphasis). */
val SurfaceBrightDark: Color = Color(0xFF303D49)

// =====================================================================
// Utility
// =====================================================================

/** Pure white, used for text/icons on strongly saturated brand fills. */
val PureWhite: Color = Color(0xFFFFFFFF)

/** Scrim overlay for modal dialogs and sheets. */
val ScrimBlack: Color = Color(0xFF000000)
