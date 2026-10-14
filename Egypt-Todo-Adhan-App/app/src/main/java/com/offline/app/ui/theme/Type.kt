package com.offline.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Destination:
 * app/src/main/java/com/offline/app/ui/theme/Type.kt
 *
 * Typography hierarchy tuned for Arabic (RTL) reading.
 *
 * Two rules drive every value below:
 *
 *  1. `letterSpacing = 0.sp` everywhere. Arabic is a connected, cursive script;
 *     positive tracking (the Material default) visually breaks the letter joins,
 *     so it is removed across the whole scale.
 *
 *  2. Generous line heights (~1.45x-1.6x the font size). Arabic glyphs carry tall
 *     ascenders (alef, lam) and deep descenders (jeem, meem), so tighter leading
 *     clips diacritics and stacked letters.
 *
 * The hierarchy keeps Material 3's role names so it drops straight into
 * `MaterialTheme(typography = AppTypography)`.
 *
 * [AppFontFamily] defaults to the platform font, which renders Arabic correctly on
 * every device. To ship a dedicated Arabic typeface, drop the file into
 * `res/font/` and change the single line that assigns [AppFontFamily] to
 * `FontFamily(Font(R.font.your_arabic_font))`; nothing else in the scale changes.
 */

/** The single font family used by the whole app. */
val AppFontFamily: FontFamily = FontFamily.Default

/**
 * Builds one Arabic-friendly [TextStyle]: the shared family, a zeroed tracking
 * value and an explicitly chosen line height.
 *
 * @param weight     font weight for the role.
 * @param sizeSp     font size in sp.
 * @param lineHeightSp line height in sp (kept generously above the font size).
 */
private fun arabicStyle(
    weight: FontWeight,
    sizeSp: Int,
    lineHeightSp: Int
): TextStyle = TextStyle(
    fontFamily = AppFontFamily,
    fontWeight = weight,
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp,
    letterSpacing = 0.sp
)

/**
 * Full Material 3 typography scale for the app.
 */
val AppTypography: Typography = Typography(

    // ----- Display: hero numerals, large countdown text -----
    displayLarge = arabicStyle(FontWeight.Light, sizeSp = 57, lineHeightSp = 72),
    displayMedium = arabicStyle(FontWeight.Light, sizeSp = 45, lineHeightSp = 58),
    displaySmall = arabicStyle(FontWeight.Normal, sizeSp = 36, lineHeightSp = 48),

    // ----- Headline: screen titles and large section headers -----
    headlineLarge = arabicStyle(FontWeight.Normal, sizeSp = 32, lineHeightSp = 44),
    headlineMedium = arabicStyle(FontWeight.Normal, sizeSp = 28, lineHeightSp = 38),
    headlineSmall = arabicStyle(FontWeight.Medium, sizeSp = 24, lineHeightSp = 34),

    // ----- Title: card headers, list section labels -----
    titleLarge = arabicStyle(FontWeight.Medium, sizeSp = 22, lineHeightSp = 32),
    titleMedium = arabicStyle(FontWeight.Medium, sizeSp = 16, lineHeightSp = 26),
    titleSmall = arabicStyle(FontWeight.Medium, sizeSp = 14, lineHeightSp = 22),

    // ----- Body: paragraphs, task titles, notes -----
    bodyLarge = arabicStyle(FontWeight.Normal, sizeSp = 16, lineHeightSp = 26),
    bodyMedium = arabicStyle(FontWeight.Normal, sizeSp = 14, lineHeightSp = 24),
    bodySmall = arabicStyle(FontWeight.Normal, sizeSp = 12, lineHeightSp = 20),

    // ----- Label: buttons, chips, bottom-navigation items -----
    labelLarge = arabicStyle(FontWeight.Medium, sizeSp = 14, lineHeightSp = 22),
    labelMedium = arabicStyle(FontWeight.Medium, sizeSp = 12, lineHeightSp = 18),
    labelSmall = arabicStyle(FontWeight.Medium, sizeSp = 11, lineHeightSp = 16)
)
