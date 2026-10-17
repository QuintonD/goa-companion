package dev.quinton.goa.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Display = Cinzel, body/UI = Inter (both OFL, bundled in res/font with licences in
 * third_party/). [GoaFonts] is the single switch point for the font families.
 */
object GoaFonts {
    val display: FontFamily = FontFamily.Serif
    val body: FontFamily = FontFamily.SansSerif
}

private fun display(size: Int, line: Int, weight: FontWeight = FontWeight.SemiBold) =
    TextStyle(fontFamily = GoaFonts.display, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private fun body(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) = TextStyle(
    fontFamily = GoaFonts.body,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

internal val GoaTypography = Typography(
    displayLarge = display(57, 64),
    displayMedium = display(45, 52),
    displaySmall = display(36, 44),
    headlineLarge = display(32, 40),
    headlineMedium = display(28, 36),
    headlineSmall = display(24, 32),
    titleLarge = display(22, 28),
    titleMedium = body(16, 24, FontWeight.SemiBold, 0.15),
    titleSmall = body(14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = body(16, 24, tracking = 0.5),
    bodyMedium = body(14, 20, tracking = 0.25),
    bodySmall = body(12, 16, tracking = 0.4),
    labelLarge = body(14, 20, FontWeight.Medium, 0.1),
    labelMedium = body(12, 16, FontWeight.Medium, 0.5),
    labelSmall = body(11, 16, FontWeight.Medium, 0.5),
)
