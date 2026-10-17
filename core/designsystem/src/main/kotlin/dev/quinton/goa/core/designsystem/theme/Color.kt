package dev.quinton.goa.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** "Sunken bronze" tokens from docs/DESIGN.md. Re-check contrast for any new pair. */
internal object Tokens {
    val DarkBackground = Color(0xFF0B1622)
    val DarkSurface = Color(0xFF122334)
    val DarkSurfaceHigh = Color(0xFF1A2E44)
    val DarkOnSurface = Color(0xFFE8E2D6)
    val DarkOnSurfaceVariant = Color(0xFFA9B4C2)
    val DarkPrimary = Color(0xFFC9A35B)
    val DarkOnPrimary = Color(0xFF0B1622)
    val DarkSecondary = Color(0xFF3FC1B0)
    val DarkTeamRed = Color(0xFFE26459)
    val DarkTeamBlue = Color(0xFF4F8FE0)
    val DarkError = Color(0xFFFF6B6B)
    val DarkUnverified = Color(0xFFE8B04A)
    val DarkMedallion = Color(0xFF07101A)

    val LightBackground = Color(0xFFF4EFE6)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceHigh = Color(0xFFEDE5D6)
    val LightOnSurface = Color(0xFF1A1F26)
    val LightOnSurfaceVariant = Color(0xFF4A5563)
    val LightPrimary = Color(0xFF84662A)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightSecondary = Color(0xFF1B786B)
    val LightTeamRed = Color(0xFFB83A30)
    val LightTeamBlue = Color(0xFF2C63B0)
    val LightError = Color(0xFFB3261E)
    val LightUnverified = Color(0xFF8A5A00)
    val LightMedallion = Color(0xFFE6DCC8)
}

internal val DarkColors = darkColorScheme(
    primary = Tokens.DarkPrimary,
    onPrimary = Tokens.DarkOnPrimary,
    primaryContainer = Color(0xFF3D3018),
    onPrimaryContainer = Color(0xFFF0DDB0),
    secondary = Tokens.DarkSecondary,
    onSecondary = Tokens.DarkBackground,
    secondaryContainer = Color(0xFF123F3B),
    onSecondaryContainer = Color(0xFFBDEFE7),
    tertiary = Tokens.DarkSecondary,
    tertiaryContainer = Color(0xFF123F3B),
    onTertiaryContainer = Color(0xFFBDEFE7),
    errorContainer = Color(0xFF4D1C1C),
    onErrorContainer = Color(0xFFFFD9D6),
    background = Tokens.DarkBackground,
    onBackground = Tokens.DarkOnSurface,
    surface = Tokens.DarkSurface,
    onSurface = Tokens.DarkOnSurface,
    surfaceVariant = Tokens.DarkSurfaceHigh,
    onSurfaceVariant = Tokens.DarkOnSurfaceVariant,
    surfaceContainerLowest = Tokens.DarkBackground,
    surfaceContainerLow = Tokens.DarkSurface,
    surfaceContainer = Tokens.DarkSurface,
    surfaceContainerHigh = Tokens.DarkSurfaceHigh,
    surfaceContainerHighest = Tokens.DarkSurfaceHigh,
    outline = Tokens.DarkPrimary.copy(alpha = 0.3f),
    outlineVariant = Tokens.DarkOnSurfaceVariant.copy(alpha = 0.3f),
    error = Tokens.DarkError,
    onError = Tokens.DarkBackground,
)

internal val LightColors = lightColorScheme(
    primary = Tokens.LightPrimary,
    onPrimary = Tokens.LightOnPrimary,
    primaryContainer = Color(0xFFF1E2C0),
    onPrimaryContainer = Color(0xFF3A2A08),
    secondary = Tokens.LightSecondary,
    onSecondary = Tokens.LightOnPrimary,
    secondaryContainer = Color(0xFFCDEDE7),
    onSecondaryContainer = Color(0xFF0B3B34),
    tertiary = Tokens.LightSecondary,
    tertiaryContainer = Color(0xFFCDEDE7),
    onTertiaryContainer = Color(0xFF0B3B34),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Tokens.LightBackground,
    onBackground = Tokens.LightOnSurface,
    surface = Tokens.LightSurface,
    onSurface = Tokens.LightOnSurface,
    surfaceVariant = Tokens.LightSurfaceHigh,
    onSurfaceVariant = Tokens.LightOnSurfaceVariant,
    surfaceContainerLowest = Tokens.LightBackground,
    surfaceContainerLow = Tokens.LightSurface,
    surfaceContainer = Tokens.LightSurface,
    surfaceContainerHigh = Tokens.LightSurfaceHigh,
    surfaceContainerHighest = Tokens.LightSurfaceHigh,
    outline = Tokens.LightPrimary.copy(alpha = 0.3f),
    outlineVariant = Tokens.LightOnSurfaceVariant.copy(alpha = 0.3f),
    error = Tokens.LightError,
    onError = Tokens.LightOnPrimary,
)

/** Colours outside the Material scheme (team identity, card colours). */
@Immutable
data class GoaExtendedColors(
    val teamRed: Color,
    val teamBlue: Color,
    val cardGold: Color,
    val cardSilver: Color,
    val cardRed: Color,
    val cardGreen: Color,
    val cardBlue: Color,
    val cardPurple: Color,
    val engravedBorder: Color,
    /** "Verified" badge tone (≥ 4.5:1 on background/surface). */
    val verified: Color = Tokens.DarkSecondary,
    /** "Unverified" badge tone (≥ 4.5:1 on background/surface). */
    val unverified: Color = Tokens.DarkUnverified,
    /** Top highlight of frames and medallions (decorative only, never text). */
    val bronzeGlow: Color = Tokens.DarkPrimary.copy(alpha = 0.18f),
    /** Deepest shade behind hero art / sigil medallions (decorative only). */
    val medallion: Color = Tokens.DarkMedallion,
)

internal val DarkExtended = GoaExtendedColors(
    teamRed = Tokens.DarkTeamRed,
    teamBlue = Tokens.DarkTeamBlue,
    cardGold = Color(0xFFD9B44A),
    cardSilver = Color(0xFFB8C2CC),
    cardRed = Color(0xFFE26459),
    cardGreen = Color(0xFF6CC27A),
    cardBlue = Color(0xFF5E9BE6),
    cardPurple = Color(0xFFB18CE0),
    engravedBorder = Tokens.DarkPrimary.copy(alpha = 0.3f),
    verified = Tokens.DarkSecondary,
    unverified = Tokens.DarkUnverified,
    bronzeGlow = Tokens.DarkPrimary.copy(alpha = 0.18f),
    medallion = Tokens.DarkMedallion,
)

internal val LightExtended = GoaExtendedColors(
    teamRed = Tokens.LightTeamRed,
    teamBlue = Tokens.LightTeamBlue,
    cardGold = Color(0xFF8A6A12),
    cardSilver = Color(0xFF56616C),
    cardRed = Color(0xFFB83A30),
    cardGreen = Color(0xFF2E7A3C),
    cardBlue = Color(0xFF2C63B0),
    cardPurple = Color(0xFF6B45A0),
    engravedBorder = Tokens.LightPrimary.copy(alpha = 0.3f),
    verified = Tokens.LightSecondary,
    unverified = Tokens.LightUnverified,
    bronzeGlow = Tokens.LightPrimary.copy(alpha = 0.14f),
    medallion = Tokens.LightMedallion,
)

val LocalGoaColors = staticCompositionLocalOf { DarkExtended }
