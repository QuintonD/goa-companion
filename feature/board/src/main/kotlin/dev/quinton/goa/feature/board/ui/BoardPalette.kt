package dev.quinton.goa.feature.board.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.match.Team

/**
 * The board's colours (docs/DESIGN.md *Board*), derived from the theme so the map reads in both
 * themes. Zone fills are muted so pieces and overlays stand out; every overlay also has a pattern
 * or number, never colour alone.
 */
@Immutable
data class BoardPalette(
    val canvas: Color,
    /** The thin line between two spaces. */
    val gap: Color,
    val laneA: Color,
    val laneB: Color,
    val side: Color,
    val throneRed: Color,
    val throneBlue: Color,
    val battle: Color,
    val zoneBorder: Color,
    val battleBorder: Color,
    val terrain: Color,
    /** The water around the map: terrain too (p.19), drawn faintly so the map's edge still reads. */
    val water: Color,
    val waterLine: Color,
    val terrainFacet: Color,
    val terrainEdge: Color,
    val teamRed: Color,
    val teamBlue: Color,
    /** Glyphs drawn on a team-coloured disc. */
    val onTeam: Color,
    /** Movement has its own green, apart from the Battle Zone's and the targets' teal. */
    val movement: Color,
    val onMovement: Color,
    val fastTravel: Color,
    val range: Color,
    val radius: Color,
    val selection: Color,
    val measure: Color,
    val label: Color,
    val labelBackground: Color,
    val token: Color,
    val onToken: Color,
    val halo: Color,
    /** Drawn over a map's painting: every overlay carries a dark halo, so it reads on any ground. */
    val onArt: Boolean = false,
) {
    fun team(team: Team): Color = if (team == Team.RED) teamRed else teamBlue

    fun throne(team: Team): Color = if (team == Team.RED) throneRed else throneBlue
}

/**
 * The colours over a painted map. The painting doesn't change with the theme, so neither do these:
 * each overlay is a light colour on a dark halo, so the 3:1 for graphics holds on sand, jungle, ice and
 * lava alike (the light colour against a dark ground, the halo against a bright one). The vector fills
 * are for the spaces a photo of the mat doesn't show (`BoardMapArt.uncovered`).
 */
val ArtBoardPalette = BoardPalette(
    canvas = Color(0xFF0B1622),
    gap = Color(0xFF0B1622),
    laneA = Color(0xFF1B3046),
    laneB = Color(0xFF213A53),
    side = Color(0xFF17303A),
    throneRed = Color(0xFF57404C),
    throneBlue = Color(0xFF2C4E77),
    battle = Color(0xFF3B6A7C),
    zoneBorder = Color(0xFFC9A35B),
    battleBorder = Color.White,
    terrain = Color(0xFF0A1118),
    water = Color(0xFF0E1D2B),
    waterLine = Color(0xFF1A2F44),
    terrainFacet = Color(0xFF2B3743),
    terrainEdge = Color(0xFF3B4957),
    teamRed = Color(0xFFE26459),
    teamBlue = Color(0xFF6FB6F0),
    onTeam = Color(0xFF0B1622),
    movement = Color(0xFF7BD88F),
    onMovement = Color.White,
    fastTravel = Color(0xFFF0CF8A),
    range = Color(0xFFD3B6FF),
    radius = Color.White,
    selection = Color(0xFF62E6D6),
    measure = Color(0xFFF0CF8A),
    label = Color.White,
    labelBackground = Color(0xDB0B1622),
    token = Color(0xFFD8CBB0),
    onToken = Color(0xFF0B1622),
    halo = Color(0xFF0A0F14),
    onArt = true,
)

/** The ground of an overlay's legend swatch when the map is painted: a space of the mat's sand. */
val ArtSwatchGround = Color(0xFFB08D62)

@Composable
fun boardPalette(): BoardPalette {
    val scheme = MaterialTheme.colorScheme
    val goa = GoaTheme.colors
    return if (GoaTheme.isDark) {
        val base = Color(0xFF1B3046)
        BoardPalette(
            canvas = scheme.background,
            gap = scheme.background,
            laneA = base,
            laneB = Color(0xFF213A53),
            side = Color(0xFF17303A),
            throneRed = goa.teamRed.copy(alpha = 0.30f).compositeOver(base),
            throneBlue = goa.teamBlue.copy(alpha = 0.32f).compositeOver(base),
            battle = scheme.secondary.copy(alpha = 0.16f).compositeOver(Color(0xFF213A53)),
            zoneBorder = scheme.primary.copy(alpha = 0.75f),
            battleBorder = scheme.secondary,
            terrain = Color(0xFF0A1118),
            water = Color(0xFF0E1D2B),
            waterLine = Color(0xFF1A2F44),
            terrainFacet = Color(0xFF2B3743),
            terrainEdge = Color(0xFF3B4957),
            teamRed = goa.teamRed,
            teamBlue = goa.teamBlue,
            onTeam = scheme.background,
            movement = MOVEMENT_DARK,
            onMovement = scheme.onSurface,
            fastTravel = scheme.primary,
            range = goa.cardPurple,
            radius = scheme.onSurface,
            selection = scheme.secondary,
            measure = scheme.primary,
            label = scheme.onSurface,
            labelBackground = scheme.background.copy(alpha = 0.86f),
            token = Color(0xFFD8CBB0),
            onToken = scheme.background,
            halo = scheme.background,
        )
    } else {
        val base = Color(0xFFE6DCC8)
        BoardPalette(
            canvas = scheme.background,
            gap = scheme.background,
            laneA = base,
            laneB = Color(0xFFDCCFB6),
            side = Color(0xFFD3D9CB),
            throneRed = goa.teamRed.copy(alpha = 0.22f).compositeOver(base),
            throneBlue = goa.teamBlue.copy(alpha = 0.22f).compositeOver(base),
            battle = lerp(Color(0xFFDCCFB6), scheme.secondary, 0.16f),
            zoneBorder = scheme.primary,
            battleBorder = scheme.secondary,
            terrain = Color(0xFF6E655A),
            water = Color(0xFFE5E6E0),
            waterLine = Color(0xFFCFD3CA),
            terrainFacet = Color(0xFF8C8275),
            terrainEdge = Color(0xFF4E473F),
            teamRed = goa.teamRed,
            teamBlue = goa.teamBlue,
            onTeam = Color.White,
            movement = MOVEMENT_LIGHT,
            onMovement = scheme.onSurface,
            fastTravel = scheme.primary,
            range = goa.cardPurple,
            radius = scheme.onSurface,
            selection = scheme.secondary,
            measure = scheme.primary,
            label = scheme.onSurface,
            labelBackground = scheme.surface.copy(alpha = 0.9f),
            token = Color(0xFF4A4036),
            onToken = Color(0xFFF4EFE6),
            halo = scheme.surface,
        )
    }
}

/** Movement green (the player palette's Kelp): dark #7BD88F, light #2A7037 (DESIGN.md *Board*). */
private val MOVEMENT_DARK = Color(0xFF7BD88F)
private val MOVEMENT_LIGHT = Color(0xFF2A7037)
