package dev.quinton.goa.feature.board.map

import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import kotlin.math.abs

/*
 * A miniature stands on its space and rises past it, over the space behind: a Heavy's head is more than a
 * hex above its own centre. The size is the mock-up's, the same on the phone and the web; what is drawn
 * there is what a player aims at, so a tap on it has to mean the figure.
 */

/** Mock-up 01 on a hex of radius 20: a figure 31 px tall (the Heavy 38), its foot 12 px below the centre. */
const val FIGURE_HEIGHT = 1.55f
const val HEAVY_HEIGHT = 2.0f
const val FIGURE_FOOT = 0.6f

/** A figure's body either side of its centre, in hex radii: its torso, not the widest weapon. */
private const val BODY_HALF_WIDTH = 0.6f

/** A miniature on the map. */
data class StandingFigure(val at: Hex, val heavy: Boolean)

/** The miniatures among [pieces]: a board in the miniature style draws its minions so. */
fun standingFigures(pieces: List<BoardPiece>): List<StandingFigure> = pieces.mapNotNull { piece ->
    val at = piece.at
    if (piece is MinionPiece && at != null) StandingFigure(at, piece.type == MinionType.HEAVY) else null
}

/**
 * The space of the figure whose body rises to [point] (map units), the one in front when several do: figures
 * nearer the bottom of the screen stand in front. Null when no figure's body is there. Only its part above the
 * space's centre counts: the rest of a figure is on its own hex, which a tap finds without this.
 */
fun figureSpaceAt(point: MapPoint, figures: List<StandingFigure>, center: (Hex) -> MapPoint): Hex? =
    figures.filter { figure ->
        val c = center(figure.at)
        val top = (if (figure.heavy) HEAVY_HEIGHT else FIGURE_HEIGHT) - FIGURE_FOOT
        abs(point.x - c.x) <= BODY_HALF_WIDTH && point.y <= c.y && c.y - point.y <= top
    }.maxByOrNull { center(it.at).y }?.at
