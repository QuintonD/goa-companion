package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * The question's map options by letter (sol #2, sol-b #1, sol-b #16): A for the option nearest the hero asked, then by
 * distance, shown on the map in a teal tag (a rounded square, never the round dark chip of a move's step count) and
 * leading the option's row in the panel's list, so a row and a space or figure are matched at a glance. Letters, not
 * numbers: a move's spaces already carry numbers (how many steps away), and one map shouldn't say "2" twice meaning two
 * things. The chosen one turns bronze.
 */

/** Every option on the map of the question, tagged: its spaces by hex and its units and tokens by piece id. */
internal data class OptionTags(val spaces: Map<Hex, String>, val pieces: Map<String, String>) {
    val isEmpty: Boolean get() = spaces.isEmpty() && pieces.isEmpty()

    /** The tag of [choice], when it is one of the question's map options. */
    fun of(choice: Choice): String? = when (choice) {
        is Choice.Space -> spaces[choice.hex]
        is Choice.Unit -> pieces[choice.unitId]
        is Choice.Token -> pieces[choice.tokenId]
        else -> null
    }

    companion object {
        val None = OptionTags(emptyMap(), emptyMap())
    }
}

/**
 * The tags of every map option the question offers: A for the one nearest the hero asked (or whose effect it is), then
 * by distance; ties by the words, then the space. Spaces and pieces are tagged in one run.
 */
internal fun optionTags(table: TableUi): OptionTags {
    val from = (table.subject ?: table.decision?.seat)?.let(table::at)
    val options = table.spaceOptions.values.map { it as Choice to it.hex } +
        table.pieceOptions.values.mapNotNull { choice -> pieceAt(table, choice)?.let { choice to it } }
    if (options.isEmpty()) return OptionTags.None
    val ordered = options.sortedWith(
        compareBy<Pair<Choice, Hex>>(
            { (_, hex) -> from?.let { HexGrid.distance(it, hex) } ?: 0 },
            { (choice, _) -> table.label(choice) },
            { (_, hex) -> hex.r },
            { (_, hex) -> hex.q },
        ),
    )
    val spaces = HashMap<Hex, String>()
    val pieces = HashMap<String, String>()
    ordered.forEachIndexed { i, (choice, _) ->
        val tag = optionTag(i)
        when (choice) {
            is Choice.Space -> spaces[choice.hex] = tag
            is Choice.Unit -> pieces[choice.unitId] = tag
            is Choice.Token -> pieces[choice.tokenId] = tag
            else -> Unit
        }
    }
    return OptionTags(spaces, pieces)
}

private fun pieceAt(table: TableUi, choice: Choice): Hex? = when (choice) {
    is Choice.Unit -> table.at(choice.unitId)
    is Choice.Token -> table.at(choice.tokenId)
    else -> null
}

/**
 * Minions to choose between that read alike (the Minion Battle's "Remove a minion": three Red Melee minions): tagged
 * always, on the map and leading their rows, whether or not the list is open. Empty unless the question offers only
 * minions, at least two, and no space.
 */
internal fun lookAlikeMinions(table: TableUi): Boolean {
    val ids = table.pieceOptions.keys
    if (ids.size < 2 || table.spaceOptions.isNotEmpty()) return false
    return ids.all { id -> table.view.minions.any { it.id == id } }
}

/**
 * The tags the map and the panel show now: every option's while the list is open ([listed]), else only look-alike
 * minions' ([lookAlikeMinions]).
 */
internal fun shownTags(table: TableUi, listed: Boolean): OptionTags = when {
    !table.asking -> OptionTags.None
    listed || lookAlikeMinions(table) -> optionTags(table)
    else -> OptionTags.None
}

/** The [index]th tag (from 0): A … Z, then AA, AB … */
internal fun optionTag(index: Int): String {
    var n = index
    val letters = StringBuilder()
    do {
        letters.insert(0, 'A' + n % LETTERS)
        n = n / LETTERS - 1
    } while (n >= 0)
    return letters.toString()
}

/**
 * An option's tag on the map: a teal rounded square (bronze once chosen) with the letter in dark ink, a white hairline
 * and a dark rim, centred on [center], [half] its half-width. Never round: a move's step counts are plain numerals.
 */
internal fun DrawScope.drawOptionTag(text: TextLayoutResult, center: Offset, half: Float, chosen: Boolean) {
    val w = maxOf(half, text.size.width / 2f + half * TAG_TEXT_PAD)
    val rim = half * 0.18f
    val corner = CornerRadius(half * 0.42f)
    drawRoundRect(
        TagInk,
        topLeft = center - Offset(w + rim, half + rim),
        size = Size((w + rim) * 2f, (half + rim) * 2f),
        cornerRadius = CornerRadius(corner.x + rim),
    )
    drawRoundRect(
        if (chosen) NowBand else NextBand,
        topLeft = center - Offset(w, half),
        size = Size(w * 2f, half * 2f),
        cornerRadius = corner,
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.9f),
        topLeft = center - Offset(w, half),
        size = Size(w * 2f, half * 2f),
        cornerRadius = corner,
        style = Stroke(half * 0.12f),
    )
    drawText(text, color = TagInk, topLeft = center - Offset(text.size.width / 2f, text.size.height / 2f))
}

/** The same tag leading the option's row in the panel. */
@Composable
internal fun OptionTagBadge(tag: String, chosen: Boolean, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val side = scaledIconSize(size, OPTION_TAG_SCALE)
    val shape = RoundedCornerShape(side * 0.3f)
    Box(
        modifier
            .size(side)
            .background(if (chosen) NowBand else NextBand, shape)
            .border(1.5.dp, TagInk.copy(alpha = 0.7f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val scale = minOf(density.fontScale, OPTION_TAG_SCALE)
        val figure = with(density) { ((if (tag.length > 1) TAG_DP_TWO else TAG_DP) * scale).dp.toSp() }
        Text(
            tag,
            style = GoaTextStyles.numeric.copy(fontSize = figure, lineHeight = figure, fontWeight = FontWeight.Bold),
            color = TagInk,
            maxLines = 1,
        )
    }
}

private val TagInk = Color(0xFF0B1622)
private const val LETTERS = 26

/** How far a row's tag grows with the font scale. */
private const val OPTION_TAG_SCALE = 1.4f
private const val TAG_DP = 15f
private const val TAG_DP_TWO = 12f

/** A two-letter tag is wider than tall: this much of the half-height either side of its letters. */
private const val TAG_TEXT_PAD = 0.35f
