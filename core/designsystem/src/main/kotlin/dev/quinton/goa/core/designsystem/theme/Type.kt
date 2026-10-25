package dev.quinton.goa.core.designsystem.theme

import androidx.annotation.RawRes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.R

/**
 * Display = Cinzel (the rulebook's engraved caps; its lower case are small caps, like the rulebook's
 * headings), body/UI = Inter, printed = Crimson Pro (an open Minion look-alike for text the game
 * prints: card text, rulebook excerpts). All OFL-1.1, static instances in `res/font`, licences in
 * `third_party/fonts/` and shown in About via [GoaFontLicenses]. [GoaFonts] is the single switch
 * point for the font families.
 */
object GoaFonts {
    val display: FontFamily = FontFamily(
        Font(R.font.cinzel_regular, FontWeight.Normal),
        Font(R.font.cinzel_semibold, FontWeight.SemiBold),
        Font(R.font.cinzel_bold, FontWeight.Bold),
    )
    val body: FontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )
    val printed: FontFamily = FontFamily(
        Font(R.font.crimson_regular, FontWeight.Normal),
        Font(R.font.crimson_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.crimson_semibold, FontWeight.SemiBold),
        // Bold keywords (`**This turn:**`) use the semibold cut: Crimson's bold is too heavy small.
        Font(R.font.crimson_semibold, FontWeight.Bold),
    )
}

/** A bundled third-party font and its licence text (`res/raw`), for the About screen. */
data class FontLicense(val family: String, val copyright: String, val license: String, @param:RawRes val textRes: Int)

val GoaFontLicenses: List<FontLicense> = listOf(
    FontLicense(
        family = "Cinzel",
        copyright = "Copyright 2020 The Cinzel Project Authors",
        license = "SIL Open Font License 1.1",
        textRes = R.raw.license_ofl_cinzel,
    ),
    FontLicense(
        family = "Inter",
        copyright = "Copyright 2020 The Inter Project Authors",
        license = "SIL Open Font License 1.1",
        textRes = R.raw.license_ofl_inter,
    ),
    FontLicense(
        family = "Crimson Pro",
        copyright = "Copyright 2018 The Crimson Pro Project Authors",
        license = "SIL Open Font License 1.1",
        textRes = R.raw.license_ofl_crimson,
    ),
)

private fun display(size: Int, line: Int, weight: FontWeight = FontWeight.SemiBold, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = GoaFonts.display,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

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
    titleLarge = display(22, 28, tracking = 0.5),
    titleMedium = body(16, 24, FontWeight.SemiBold, 0.15),
    titleSmall = body(14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = body(16, 24, tracking = 0.5),
    bodyMedium = body(14, 20, tracking = 0.25),
    bodySmall = body(12, 16, tracking = 0.4),
    labelLarge = body(14, 20, FontWeight.Medium, 0.1),
    labelMedium = body(12, 16, FontWeight.Medium, 0.5),
    labelSmall = body(11, 16, FontWeight.Medium, 0.5),
)

/**
 * Styles beyond the Material type scale. Access via `GoaTextStyles.timerDigits` etc.
 * All numeric styles use tabular figures (`tnum`) so counting digits never jitter.
 */
object GoaTextStyles {
    private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

    /** Giant countdown on the timer ring (Inter SemiBold, tabular). Scale with `fontSize` as needed. */
    val timerDigits: TextStyle = body(96, 104, FontWeight.SemiBold, -2.0).copy(
        fontFeatureSettings = "tnum, lnum",
        lineHeightStyle = Centered,
    )

    /** Smaller tabular figures: phase durations, chips, list trailing numbers. */
    val timerDigitsSmall: TextStyle = body(32, 40, FontWeight.Medium, -0.5).copy(
        fontFeatureSettings = "tnum, lnum",
        lineHeightStyle = Centered,
    )

    /** Inline tabular numbers inside body copy (ratings, win rates, durations). */
    val numeric: TextStyle = body(14, 20, FontWeight.Medium, 0.0).copy(fontFeatureSettings = "tnum, lnum")

    /** Small engraved caps for section overlines ("ROSTER", "OWNED PRODUCTS"). */
    val overline: TextStyle = display(12, 16, FontWeight.Bold, 1.5)

    /**
     * Section heads as the rulebook sets them: Cinzel in title case, whose lower case are small
     * caps ("Place the Board"). Coloured with `GoaTheme.colors.heading` (copper).
     */
    val sectionHead: TextStyle = display(16, 22, FontWeight.Bold, 0.6)

    /** Top-bar titles on the steel banner (Cinzel, engraved caps). */
    val bannerTitle: TextStyle = display(21, 28, FontWeight.Bold, 0.8)

    /**
     * Text the game prints (card text, rulebook excerpts), in Crimson Pro: slightly larger than
     * Inter body text, as a serif needs at the same reading distance.
     */
    val printed: TextStyle = TextStyle(
        fontFamily = GoaFonts.printed,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.1.sp,
    )

    /** [printed] for denser places (tiles, sheets). */
    val printedSmall: TextStyle = printed.copy(fontSize = 16.sp, lineHeight = 22.sp)
}

/** Alias for the brief: `GoaTypography.TimerDigits`-style access from features. */
val TimerDigits: TextStyle get() = GoaTextStyles.timerDigits
