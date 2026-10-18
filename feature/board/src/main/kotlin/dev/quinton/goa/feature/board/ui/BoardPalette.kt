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
    val terrainFacet: Color,
    val terrainEdge: Color,
    val teamRed: Color,
    val teamBlue: Color,
    /** Glyphs drawn on a team-coloured disc. */
    val onTeam: Color,
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
) {
    fun team(team: Team): Color = if (team == Team.RED) teamRed else teamBlue

    fun throne(team: Team): Color = if (team == Team.RED) throneRed else throneBlue
}

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
            terrainFacet = Color(0xFF2B3743),
            terrainEdge = Color(0xFF3B4957),
            teamRed = goa.teamRed,
            teamBlue = goa.teamBlue,
            onTeam = scheme.background,
            movement = scheme.secondary,
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
            terrainFacet = Color(0xFF8C8275),
            terrainEdge = Color(0xFF4E473F),
            teamRed = goa.teamRed,
            teamBlue = goa.teamBlue,
            onTeam = Color.White,
            movement = scheme.secondary,
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
