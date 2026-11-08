package dev.quinton.goa.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** "Steel and bronze" tokens from docs/DESIGN.md (ADR-042). Re-check contrast for any new pair. */
internal object Tokens {
    val DarkBackground = Color(0xFF0B1622)
    val DarkSurface = Color(0xFF1E2327)
    val DarkSurfaceHigh = Color(0xFF2A3136)
    val DarkOnSurface = Color(0xFFE8E2D6)

    // Secondary text: ≥ 7:1 on the slate, the canvas, the raised surface and a steel chip, for a dim table (round 4).
    val DarkOnSurfaceVariant = Color(0xFFBCC6CF)
    val DarkPrimary = Color(0xFFC9A35B)
    val DarkOnPrimary = Color(0xFF0B1622)
    val DarkSecondary = Color(0xFF3FC1B0)
    val DarkTeamRed = Color(0xFFE76C61)
    val DarkTeamBlue = Color(0xFF4F8FE0)

    // Team colours for words (a team's name, an actor heading): lighter in the dark theme, deeper on paper, so a
    // team-coloured word reads as well as the body text beside it (≥ 6:1 on the plate, ≥ 4.5:1 on any ground).
    val DarkTeamRedText = Color(0xFFF29488)
    val DarkTeamBlueText = Color(0xFF8DB9F5)
    val LightTeamRedText = Color(0xFF8E2A21)
    val LightTeamBlueText = Color(0xFF1E4C8F)
    val DarkError = Color(0xFFFF6B6B)
    val DarkUnverified = Color(0xFFE8B04A)
    val DarkMedallion = Color(0xFF07101A)

    // The light theme is the rulebook's page (ADR-053, round 1 of the hill climb): a warm paper canvas, plates of
    // white paper with a steel edge, ink text and bronze accents. Only the timer and the map stay dark.
    val LightBackground = Color(0xFFEDE7DB)
    val LightSurface = Color(0xFFFCFAF4)
    val LightSurfaceHigh = Color(0xFFF1EBDF)
    val LightOnSurface = Color(0xFF221D17)
    val LightOnSurfaceVariant = Color(0xFF4C4438)
    val LightPrimary = Color(0xFF7A5A1E)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightSecondary = Color(0xFF17695E)
    val LightTeamRed = Color(0xFFB0362C)
    val LightTeamBlue = Color(0xFF2C63B0)
    val LightError = Color(0xFFB3261E)
    val LightUnverified = Color(0xFF8A5A00)
    val LightMedallion = Color(0xFFE6DCC8)

    // The rulebook's steel-blue banner (top bars), its copper small-caps headings and the cards'
    // parchment text box (ADR-042). The banner stays dark steel in both themes, as on the page.
    val DarkBannerTop = Color(0xFF26496A)
    val DarkBannerBottom = Color(0xFF152C42)
    val LightBannerTop = Color(0xFF2E5C7E)
    val LightBannerBottom = Color(0xFF1D3E57)
    val BannerHighlight = Color(0xFFA9CBE0)
    val BannerShadow = Color(0xFF0A1622)
    val OnBanner = Color(0xFFF3F6F9)
    val OnBannerVariant = Color(0xFFC9D8E4)
    val DarkHeading = Color(0xFFE39B5F)
    val LightHeading = Color(0xFF9A4A1F)
    val DarkParchment = Color(0xFFD9D2C1)
    val LightParchment = Color(0xFFEEE8DA)
    val OnParchment = Color(0xFF231C14)
    val DarkOnParchmentVariant = Color(0xFF54483A)
    val LightOnParchmentVariant = Color(0xFF5A4E3E)
    val ParchmentInk = Color(0xFF8A3F18)
    val DarkParchmentEdge = Color(0xFF8C826E)
    val LightParchmentEdge = Color(0xFFB9AE98)

    val DarkPlate = Color(0xFF1D2124)
    val DarkPlateWell = Color(0xFF15191B)
    val LightPlate = LightSurface
    val LightPlateWell = Color(0xFFF1ECE2)

    // The rules reader's page: the rulebook's parchment in the light theme; at a dim table (the dark theme) a dark
    // parchment with warm light ink, so opening a passage never flashes a bright page.
    val DarkPage = Color(0xFF221E19)
    val DarkOnPage = Color(0xFFEAE0CC)
    val DarkOnPageVariant = Color(0xFFBDB19A)
    val DarkPageInk = Color(0xFFE39B5F)
    val DarkPageEdge = Color(0xFF51473A)

    // Bronze strokes and words that must hold on cream (symbols, a selection's edge and check, unfilled stars).
    val LightBronzeInk = Color(0xFF6A4A12)

    // De-emphasised but readable (round 5): what is done, past, empty or off yet still informative. The words stay
    // ≥ 5:1 on every ground of their theme (the steel chip included in the dark theme), the edges ≥ 3:1, so a muted
    // state steps back by losing decoration (fill, weight, a thick edge), never by fading its text.
    val DarkTextMuted = Color(0xFFA3ADB6)
    val DarkOutlineMuted = Color(0xFF7C8994)
    val LightTextMuted = Color(0xFF665B4C)
    val LightOutlineMuted = Color(0xFF857A68)
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
    primaryContainer = Color(0xFFF0E1BF),
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
    outline = Tokens.LightPrimary.copy(alpha = 0.4f),
    outlineVariant = Tokens.LightOnSurfaceVariant.copy(alpha = 0.25f),
    error = Tokens.LightError,
    onError = Tokens.LightOnPrimary,
)

/** Colours outside the Material scheme (team identity, card colours). */
@Immutable
data class GoaExtendedColors(
    /** A team's colour for marks, accent bars, rings and fills (≥ 3:1 on every ground of its theme). */
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
    /** Steel banner (top bars) gradient, top to bottom; [onBanner] text ≥ 5.7:1 on both stops. */
    val bannerTop: Color = Tokens.DarkBannerTop,
    val bannerBottom: Color = Tokens.DarkBannerBottom,
    /** Bright steel edge along the banner's top, and its dark lower edge (decorative). */
    val bannerHighlight: Color = Tokens.BannerHighlight,
    val bannerShadow: Color = Tokens.BannerShadow,
    val onBanner: Color = Tokens.OnBanner,
    /** Banner icons and secondary text (≥ 4.9:1 on both themes' banner). */
    val onBannerVariant: Color = Tokens.OnBannerVariant,
    /** Copper section headings, the rulebook's small-caps heads (≥ 4.9:1 on background, surface, surfaceHigh). */
    val heading: Color = Tokens.DarkHeading,
    /** The cards' parchment text box: printed text (card text, rulebook excerpts) sits on it. */
    val parchment: Color = Tokens.DarkParchment,
    val onParchment: Color = Tokens.OnParchment,
    /** Reminder text on parchment (≥ 5.8:1). */
    val onParchmentVariant: Color = Tokens.DarkOnParchmentVariant,
    /** Inline game icons on parchment (≥ 4.9:1). */
    val parchmentInk: Color = Tokens.ParchmentInk,
    val parchmentEdge: Color = Tokens.DarkParchmentEdge,
    /** A plate's flat tone (the slate, or the light theme's paper): fields and fades that sit on a plate. */
    val plate: Color = Tokens.DarkPlate,
    /** A recess on a plate: a field, a well, a quiet group. */
    val plateWell: Color = Tokens.DarkPlateWell,
    /** Selected: the bronze outline and the tint under a picked choice (`SelectablePlate`). */
    val selection: Color = Tokens.DarkPrimary,
    val selectionFill: Color = Tokens.DarkPrimary.copy(alpha = 0.2f),
    /** A secondary glyph that still carries meaning (an unfilled complexity star): ≥ 3:1 on its plate and canvas. */
    val glyphMuted: Color = Color(0xFF8A949C),
    /**
     * Bronze for a stroke, symbol or small word that carries meaning (a complexity star, a selection's check, a
     * turn diamond): the theme's bronze in the dark theme, a deeper one on cream (≥ 4.5:1 on paper and canvas).
     */
    val bronzeInk: Color = Tokens.DarkPrimary,
    /**
     * The rules reader's page (a quoted passage): parchment when light, a dark parchment when dark. Its words
     * ([onPage], [onPageVariant]) and the printed heads ([pageInk]) are ≥ 4.5:1 on it; [pageEdge] is its rules.
     */
    val page: Color = Tokens.DarkPage,
    val onPage: Color = Tokens.DarkOnPage,
    val onPageVariant: Color = Tokens.DarkOnPageVariant,
    val pageInk: Color = Tokens.DarkPageInk,
    val pageEdge: Color = Tokens.DarkPageEdge,
    /**
     * A team's colour for words (`teamTextColor`): a team's name or a heading that names who acts. ≥ 6:1 on the
     * plate and ≥ 4.5:1 on every ground of its theme, as legible as the body text; [teamRed]/[teamBlue] stay for marks.
     */
    val teamRedText: Color = Tokens.DarkTeamRedText,
    val teamBlueText: Color = Tokens.DarkTeamBlueText,
    /**
     * Words of a de-emphasised state (a done turn, a past draft step, an empty slot's number, a card name under a
     * done hero): ≥ 4.5:1 on every ground of its theme, so it steps back from the body text without fading. Never
     * draw muted text with alpha (DESIGN.md "Muted states").
     */
    val textMuted: Color = Tokens.DarkTextMuted,
    /**
     * The edge of a de-emphasised thing that still has to be seen (an empty slot's dashed outline, a past step's
     * hairline, a done cell's frame): ≥ 3:1 on every ground of its theme.
     */
    val outlineMuted: Color = Tokens.DarkOutlineMuted,
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
    bannerTop = Tokens.DarkBannerTop,
    bannerBottom = Tokens.DarkBannerBottom,
    heading = Tokens.DarkHeading,
    parchment = Tokens.DarkParchment,
    onParchmentVariant = Tokens.DarkOnParchmentVariant,
    parchmentEdge = Tokens.DarkParchmentEdge,
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
    bannerTop = Tokens.LightBannerTop,
    bannerBottom = Tokens.LightBannerBottom,
    heading = Tokens.LightHeading,
    parchment = Tokens.LightParchment,
    onParchmentVariant = Tokens.LightOnParchmentVariant,
    parchmentEdge = Tokens.LightParchmentEdge,
    plate = Tokens.LightPlate,
    plateWell = Tokens.LightPlateWell,
    selection = Tokens.LightPrimary,
    selectionFill = Tokens.LightPrimary.copy(alpha = 0.16f),
    glyphMuted = Color(0xFF7A7062),
    bronzeInk = Tokens.LightBronzeInk,
    page = Tokens.LightParchment,
    onPage = Tokens.OnParchment,
    onPageVariant = Tokens.LightOnParchmentVariant,
    pageInk = Tokens.ParchmentInk,
    pageEdge = Tokens.LightParchmentEdge,
    teamRedText = Tokens.LightTeamRedText,
    teamBlueText = Tokens.LightTeamBlueText,
    textMuted = Tokens.LightTextMuted,
    outlineMuted = Tokens.LightOutlineMuted,
)

val LocalGoaColors = staticCompositionLocalOf { DarkExtended }
