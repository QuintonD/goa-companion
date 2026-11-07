package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.model.board.Hex

/*
 * Minions to choose between that read alike (the Minion Battle's "Remove a minion": three Red Melee minions): each
 * gets a number, nearest the hero asked first, shown on the map beside the figure and leading its row in the panel, so
 * a row and a figure are matched at a glance. Teal is "you can choose"; the chosen one turns bronze.
 */

/**
 * The numbers, by minion id: 1 for the nearest to the hero asked (or whose effect it is), then by distance; ties by
 * the row's words. Empty unless the question offers only minions, at least two, and no space.
 */
internal fun minionNumbers(table: TableUi): Map<String, Int> {
    val ids = table.pieceOptions.keys
    if (ids.size < 2 || table.spaceOptions.isNotEmpty()) return emptyMap()
    val minions = ids.mapNotNull { id -> table.view.minions.firstOrNull { it.id == id } }
    if (minions.size != ids.size) return emptyMap()
    val from = (table.subject ?: table.decision?.seat)?.let(table::at)
    return minions
        .sortedWith(
            compareBy(
                { minion -> from?.let { HexGrid.distance(it, minion.at) } ?: 0 },
                { minion -> table.label(Choice.Unit(minion.id)) },
            ),
        )
        .mapIndexed { i, minion -> minion.id to i + 1 }
        .toMap()
}

/**
 * Spaces to choose that stay crowded on the map (sol #1, sol-b #2): each gets a number, nearest the hero asked first
 * (ties by the words, then the space), shown on the map in the space and leading its row in the panel, as the Minion
 * Battle's minions are. Empty unless the question offers between two and [MAX_LISTED] spaces.
 */
internal fun spaceNumbers(table: TableUi): Map<Hex, Int> {
    val hexes = table.spaceOptions.keys
    if (hexes.size < 2 || hexes.size > MAX_LISTED) return emptyMap()
    val from = (table.subject ?: table.decision?.seat)?.let(table::at)
    return hexes
        .sortedWith(
            compareBy<Hex>(
                { hex -> from?.let { HexGrid.distance(it, hex) } ?: 0 },
                { hex -> table.label(Choice.Space(hex)) },
                { it.r },
                { it.q },
            ),
        )
        .mapIndexed { i, hex -> hex to i + 1 }
        .toMap()
}

/** A minion's number on the map: teal (you can choose) with dark figures, bronze once chosen; a dark rim. */
internal fun DrawScope.drawOptionNumber(text: TextLayoutResult, center: Offset, radius: Float, chosen: Boolean) {
    drawCircle(OptionInk, radius = radius * 1.18f, center = center)
    drawCircle(if (chosen) NowBand else NextBand, radius = radius, center = center)
    drawCircle(Color.White.copy(alpha = 0.85f), radius = radius, center = center, style = Stroke(radius * 0.12f))
    drawText(text, color = OptionInk, topLeft = center - Offset(text.size.width / 2f, text.size.height / 2f))
}

/** The same number leading the minion's row in the panel. */
@Composable
internal fun OptionNumberBadge(number: Int, chosen: Boolean, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val side = scaledIconSize(size, OPTION_NUMBER_SCALE)
    Box(
        modifier
            .size(side)
            .background(if (chosen) NowBand else NextBand, CircleShape)
            .border(1.5.dp, OptionInk.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val figure = with(density) { (NUMBER_DP * minOf(density.fontScale, OPTION_NUMBER_SCALE)).dp.toSp() }
        Text(
            "$number",
            style = GoaTextStyles.numeric.copy(fontSize = figure, lineHeight = figure, fontWeight = FontWeight.Bold),
            color = OptionInk,
        )
    }
}

private val OptionInk = Color(0xFF0B1622)

/** At most this many spaces are listed by number; more are left to the map (and its tap to zoom). */
internal const val MAX_LISTED = 12

/** How far a row's number grows with the font scale. */
private const val OPTION_NUMBER_SCALE = 1.4f
private const val NUMBER_DP = 15f
