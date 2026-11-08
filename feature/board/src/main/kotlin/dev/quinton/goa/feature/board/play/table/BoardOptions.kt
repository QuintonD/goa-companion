package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.feature.board.ui.MinionTypeBadge
import dev.quinton.goa.feature.board.ui.PlateButton
import dev.quinton.goa.feature.board.ui.PlateWell

/*
 * How a question is answered on the board (DecisionPanel): spaces, units or tokens are tapped on the map, then
 * confirmed. Every such question can also be answered from a list (sol #2, sol-b #1): the "List" chip beside the hint
 * opens it ([MapList]), each option a 48 dp row led by the letter its space or figure carries on the map ([shownTags]).
 * Look-alike minions (the Minion Battle) are always listed.
 */

/**
 * How a board question is answered: spaces, units or tokens are tapped on the board. Once one is tapped, what it is in
 * full ("Chosen: Lane Zone 3 · 3 spaces from Wasp"): the Confirm button says only what it does (opus #21).
 */
internal fun boardHint(table: TableUi): String =
    boardChoice(table)?.let { "Chosen: ${table.label(it)}" }
        ?: (boardHint(table.spaceOptions.size, table.pieceOptions.size) + longPressHint(table))

/** The space, unit or token tapped on the board and not confirmed yet. */
private fun boardChoice(table: TableUi): Choice? =
    table.selected?.takeIf { it is Choice.Space || it is Choice.Unit || it is Choice.Token }

/**
 * The board Confirm in one line (opus #21): what the tap does and, for a move, how many spaces the engine's path takes
 * ("Move here · 3"); a piece by its letter when look-alikes are lettered, else by name. Where it is goes in the plate.
 */
internal fun confirmLabel(table: TableUi, selected: Choice): String {
    val kind = table.decision?.kind
    return when (selected) {
        is Choice.Space -> {
            val from = (table.subject ?: table.decision?.seat)?.let(table::at)
            val steps = table.pathTo(selected.hex).count { it != from }
            when {
                kind == DecisionKind.RESPAWN -> "Respawn here"
                steps > 0 -> "Move here · $steps"
                else -> "Choose this space"
            }
        }

        is Choice.Unit, is Choice.Token -> {
            val id = if (selected is Choice.Unit) selected.unitId else (selected as Choice.Token).tokenId
            val which = shownTags(table, listed = false).pieces[id] ?: table.pieceName(id)
            if (kind == DecisionKind.REMOVE_MINION) "Remove · $which" else "Choose · $which"
        }

        else -> "Confirm · ${table.label(selected)}"
    }
}

/** [boardHint] for a question no card asks, on the plate itself, with the "List" chip at its end. */
@Composable
internal fun BoardHint(table: TableUi) {
    HintRow(boardHint(table), table, MaterialTheme.typography.bodyMedium, dim = true)
}

/** The hint's words after the tap glyph, and the "List" chip at the row's end ([MapListToggle]). */
@Composable
private fun HintRow(text: String, table: TableUi, style: TextStyle, dim: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.TouchApp,
            null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(scaledIconSize(18.dp, 1.3f)),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text,
            style = style,
            color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        MapListToggle(table, Modifier.padding(start = Spacing.sm))
    }
}

/**
 * "List": the question's map options as rows in the panel and lettered on the map, or back to the map alone. Offered
 * for every question answered on the map, except look-alike minions, which are always listed. A chip at 48 dp, bronze
 * with a tick while the list is open; TalkBack hears what it does and whether it is on.
 */
@Composable
internal fun MapListToggle(table: TableUi, modifier: Modifier = Modifier) {
    if (!table.asking || !hasMapOptions(table) || lookAlikeMinions(table)) return
    val list = LocalMapList.current
    GoaFilterChip(
        selected = list.open,
        onClick = list.toggle,
        modifier = modifier.semantics {
            contentDescription = "List the options"
            stateDescription = if (list.open) "Shown, lettered on the map" else "Map only"
        },
        leadingIcon = {
            Icon(
                if (list.open) Icons.Outlined.CheckCircle else Icons.AutoMirrored.Outlined.FormatListBulleted,
                null,
                modifier = Modifier.size(scaledIconSize(18.dp, 1.3f)),
            )
        },
    ) { Text("List", maxLines = 1) }
}

private fun hasMapOptions(table: TableUi) = table.spaceOptions.isNotEmpty() || table.pieceOptions.isNotEmpty()

/**
 * Confirm, once a space or piece is tapped on the board: one line at 56 dp saying what it does ([confirmLabel]); the
 * plate above says which one it is. TalkBack hears both.
 */
@Composable
internal fun BoardConfirm(table: TableUi, onAnswer: (Choice) -> Unit, busy: Boolean) {
    val selected = boardChoice(table)
    if (selected != null) {
        val label = confirmLabel(table, selected)
        PlateButton(
            onClick = { onAnswer(selected) },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { contentDescription = "$label, ${table.label(selected)}" },
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            Text(label, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/**
 * The panel at its peek while a space is chosen on the map ([GameTable]): under the header (who decides, the whole
 * instruction), how many spaces it offers in one line ("12 spaces · tap a lit one") with the "List" chip, then only
 * the commit: Confirm naming the choice once one is tapped, and any answer that isn't on the map ("Don't respawn"). The
 * asking card is one drag or tap up; so is the list, which opens the whole panel.
 */
@Composable
fun BoardPeek(table: TableUi, onAnswer: (Choice) -> Unit, busy: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val count = table.spaceOptions.size
        HintRow(
            if (count == 1) "1 space · tap the lit one" else "$count spaces · tap a lit one",
            table,
            MaterialTheme.typography.bodyLarge,
            dim = false,
        )
        // Once one is tapped, which one in full: the Confirm under it says only what it does (opus #21).
        boardChoice(table)?.let {
            Text(
                "Chosen: ${table.label(it)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (boardChoice(table) != null) BoardConfirm(table, onAnswer, busy)
        Choices(table, table.buttonOptions, onAnswer, busy, brief = true)
    }
}

/**
 * When a hero's piece is an option (a tap there answers), how to read about that hero instead
 * (ADR-044): a long press always opens the hero's game state.
 */
internal fun longPressHint(table: TableUi): String {
    val heroOption = table.pieceOptions.keys.any { id ->
        table.heroView(id) != null || table.view.pieces.firstOrNull { it.id == id }?.let(::figureOf) != null
    }
    return if (heroOption) " Long-press a hero for their game state." else ""
}

/**
 * The options in the panel: with the list open (or look-alike minions) every option a row led by its letter, in the
 * letters' order; else units and tokens (and a short list of spaces whose names differ) as chips, a minion as a row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoardChips(table: TableUi, onSelect: (Choice) -> Unit) {
    val selected = boardChoice(table)
    val tags = shownTags(table, LocalMapList.current.open)
    if (!tags.isEmpty) {
        OptionRows(table, tags, selected, onSelect)
        return
    }
    val spaces = table.spaceOptions.values.toList()
    val pieces = table.pieceOptions.values.toList()
    // A chip must say which one it is: spaces whose names repeat ("Red Throne Zone" three times)
    // are only told apart on the board (or in the list).
    val spaceChips = spaces.takeIf { list ->
        list.size <= MAX_SPACE_CHIPS && list.map(table::label).distinct().size == list.size
    }.orEmpty()
    val chips = pieces + spaceChips
    if (chips.isEmpty()) return
    // A lone minion: a row, with the team and type as marks.
    val minion = (chips.singleOrNull() as? Choice.Unit)?.let { c ->
        table.pieces.firstOrNull { it.id == c.unitId } as? MinionPiece
    }
    if (minion != null) {
        val choice = chips.single()
        MinionRow(table.label(choice), minion, choice == selected, tag = null) { onSelect(choice) }
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        chips.forEach { choice -> OptionChip(table.label(choice), choice == selected) { onSelect(choice) } }
    }
}

/**
 * Every map option as a row led by its letter (A first, nearest the hero asked): a minion with its team and type as
 * marks, a space by how far it is ("3 spaces from Wasp") over its Zone, a hero or token by name. Spaces alone go two
 * to a row where they fit. A row is a 48 dp radio, bronze once chosen; TalkBack hears its letter and its words.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionRows(table: TableUi, tags: OptionTags, selected: Choice?, onSelect: (Choice) -> Unit) {
    val options = (table.spaceOptions.values + table.pieceOptions.values)
        .mapNotNull { choice -> tags.of(choice)?.let { choice to it } }
        .sortedWith(compareBy({ it.second.length }, { it.second }))
    val spacesOnly = options.all { it.first is Choice.Space }
    val two = spacesOnly && LocalDensity.current.fontScale < TWO_ROWS_UNTIL_SCALE
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        maxItemsInEachRow = if (two) 2 else 1,
    ) {
        options.forEach { (choice, tag) ->
            val minion = (choice as? Choice.Unit)?.let { c -> table.pieces.firstOrNull { it.id == c.unitId } }
            if (minion is MinionPiece) {
                MinionRow(table.label(choice), minion, choice == selected, tag, Modifier.weight(1f)) {
                    onSelect(choice)
                }
            } else {
                val parts = table.label(choice).split(" · ")
                // A space: how far first, its Zone under it; a piece: its name first, where it stands under it.
                val far = choice is Choice.Space && parts.size > 1
                val first = if (far) parts[1] else parts.first()
                val rest = if (far) listOf(parts.first()) + parts.drop(2) else parts.drop(1)
                NumberedRow(
                    tag = tag,
                    first = first,
                    second = rest.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                    spoken = table.label(choice),
                    selected = choice == selected,
                    modifier = Modifier.weight(1f),
                ) { onSelect(choice) }
            }
        }
    }
}

/** An option row led by its letter (the tag its space or figure carries on the map), as [MinionRow] without marks. */
@Composable
private fun NumberedRow(
    tag: String,
    first: String,
    second: String?,
    spoken: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else PlateWell,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else GoaTheme.colors.engravedBorder,
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Option $tag, $spoken" },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OptionTagBadge(tag, chosen = selected)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f).clearAndSetSemantics {}) {
                Text(first, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                second?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Spaces are listed two to a row below this font scale; one, full width, from it. */
private const val TWO_ROWS_UNTIL_SCALE = 1.5f

/** At most this many spaces whose names differ are offered as chips with the list closed. */
private const val MAX_SPACE_CHIPS = 6

/**
 * One minion to choose, full width: its letter on the map (when several read alike, or the list is open), its team and
 * type as marks, what tells it apart first. Bronze when chosen.
 */
@Composable
private fun MinionRow(
    label: String,
    minion: MinionPiece,
    selected: Boolean,
    tag: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (first, second) = minionRowLines(label)
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else PlateWell,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else GoaTheme.colors.engravedBorder,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(tag?.let { "Option $it" }, label).joinToString(", ")
            },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tag?.let {
                OptionTagBadge(it, chosen = selected)
                Spacer(Modifier.width(Spacing.sm))
            }
            TeamMark(minion.team, markSize = scaledIconSize(14.dp, 1.3f))
            Spacer(Modifier.width(Spacing.xs))
            MinionTypeBadge(minion, size = scaledIconSize(28.dp, 1.3f))
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f).clearAndSetSemantics {}) {
                Text(first, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                second?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    null,
                    modifier = Modifier.size(scaledIconSize(18.dp, 1.3f)),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else PlateWell,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
