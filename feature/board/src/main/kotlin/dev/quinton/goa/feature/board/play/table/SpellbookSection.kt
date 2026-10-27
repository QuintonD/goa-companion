package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.ExtraPlaceView
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.LANDSCAPE_CARD_ASPECT
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin
import dev.quinton.goa.feature.board.ui.cards.isSpell

/*
 * A hero's extra cards at the table (HeroView.extras, ADR-040): Gydion's spellbook. The official spell
 * card images are the content (never their text retyped), landscape as printed, grouped by where they
 * are. Prepare Spells (gydion-silver): "Add all spell cards to the spellbook. Only Gydion can look at
 * the cards in the spellbook. As each spell is cast, remove it from the spellbook faceup."
 */

/** One place of a hero's extra cards as the sheet shows it. [cards] null = facedown to this person. */
@Immutable
internal data class ExtrasGroup(
    val place: String,
    val title: String,
    val count: Int,
    val cards: List<Card>?,
    /** A line under the title: why the cards are hidden, or what puts cards here. */
    val note: String? = null,
)

/**
 * The hero's Ultimate card once unlocked (p.16: it lies faceup on the dashboard, open information),
 * else null: the deck's purple card (Gydion's purple Wish is a spell, not in the deck).
 */
internal fun ultimateCard(table: TableUi, hero: HeroView): Card? =
    if (hero.ultimate) table.hero(hero.heroId).cards.firstOrNull { it.color == CardColor.PURPLE } else null

/** "Spellbook" for spell cards (Gydion), else "Extra cards". */
internal fun extrasTitle(table: TableUi, hero: HeroView): String {
    val any = hero.extras.firstNotNullOfOrNull { place -> place.cardIds?.firstNotNullOfOrNull(table::card) }
        ?: table.cards.values.firstOrNull { it.heroId == hero.heroId && it.isSpell }
    return if (any?.isSpell == true) "Spellbook" else "Extra cards"
}

/**
 * The groups of [hero]'s extra cards, in the engine's order: in the spellbook (named to Gydion's
 * player only; the others see how many), cast (removed faceup, public), not yet added (public; shown
 * only while there are some: the game starts with an empty spellbook, designer on BGG 3491113).
 */
internal fun extrasGroups(table: TableUi, hero: HeroView): List<ExtrasGroup> {
    val name = table.hero(hero.heroId).name
    val mine = hero.heroId in table.mine
    return hero.extras.mapNotNull { place ->
        val cards = place.cardIds?.mapNotNull(table::card)
        when (place.place) {
            SPELLBOOK -> ExtrasGroup(
                place.place,
                title = if (mine) "In your spellbook" else "In the spellbook",
                count = place.count,
                cards = cards,
                note = when {
                    cards == null -> "Facedown: only $name can look at the cards in the spellbook."
                    !mine -> "Open in this puzzle."
                    place.count == 0 -> "Empty until Prepare Spells adds the spell cards."
                    else -> null
                },
            )

            REMOVED -> ExtrasGroup(place.place, "Cast · removed faceup", place.count, cards)

            BOX -> if (place.count == 0) {
                null
            } else {
                ExtrasGroup(
                    place.place,
                    "Not yet added",
                    place.count,
                    cards,
                    note = "Prepare Spells adds every spell card to the spellbook.",
                )
            }

            else -> ExtrasGroup(place.place, placeTitle(place), place.count, cards)
        }
    }
}

private fun placeTitle(place: ExtraPlaceView): String =
    place.place.replace('_', ' ').replaceFirstChar { it.uppercase() }

/**
 * The hero sheet's section for a hero's extra cards (nothing for a hero without any). With
 * [onOpenSheet] (the dedicated Spellbook sheet is one tap away) it is a summary: how many cards in
 * each group, "Open the spellbook" beside the head, so the hero's other cards (the hand) are not
 * buried under 22 spell cards. Without it, each group's cards as landscape thumbnails in a grid; tap
 * one to see it large in the card focus viewer, swiping through its group. [onOpen] opens the viewer;
 * [origin] marks a thumbnail the card grows out of.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExtrasSection(
    table: TableUi,
    hero: HeroView,
    onOpen: (cards: List<Card>, index: Int, title: String) -> Unit,
    modifier: Modifier = Modifier,
    origin: (title: String, card: Card) -> Modifier = { _, _ -> Modifier },
    onOpenSheet: (() -> Unit)? = null,
) {
    if (hero.extras.isEmpty()) return
    val name = table.hero(hero.heroId).name
    val groups = extrasGroups(table, hero)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                extrasTitle(table, hero).uppercase(),
                style = GoaTextStyles.overline,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (onOpenSheet != null) {
                TextButton(onClick = onOpenSheet, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Open the ${extrasTitle(table, hero).lowercase()}")
                }
            }
        }
        if (onOpenSheet != null) {
            ExtrasSummary(groups, onOpenSheet)
        } else {
            groups.forEach { group ->
                GroupBlock(group, "$name · ${group.title}", onOpen = onOpen, origin = origin)
            }
        }
    }
}

/**
 * The groups as counts ("In your spellbook 19 · Cast · removed faceup 3"), with the spellbook's note
 * (facedown to this person, or empty until Prepare Spells); a tap opens the sheet. One TalkBack node.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtrasSummary(groups: List<ExtrasGroup>, onOpenSheet: () -> Unit) {
    val note = groups.firstOrNull { it.place == SPELLBOOK }?.note
    val spoken = (groups.map { "${it.title}: ${it.count}" } + listOfNotNull(note)).joinToString(". ")
    Surface(
        onClick = onOpenSheet,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = spoken
            role = Role.Button
            onClick(label = "Open the spellbook") {
                onOpenSheet()
                true
            }
        },
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                groups.forEach { group ->
                    Column {
                        Text(
                            group.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("${group.count}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One group: its head with the count, the note, then its cards (nothing for a facedown group).
 * [byTier]: the cards (sorted by Tier) under a small head per Tier, as the catalog lists the spells;
 * swiping in the viewer still goes through the whole group. [castWith]: a line under a spell no
 * question marks, e.g. "Cast with Lesser Evocation".
 */
@Composable
internal fun GroupBlock(
    group: ExtrasGroup,
    title: String,
    onOpen: (cards: List<Card>, index: Int, title: String) -> Unit,
    origin: (title: String, card: Card) -> Modifier,
    marks: Map<String, SpellMark> = emptyMap(),
    castWith: Map<String, String> = emptyMap(),
    byTier: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "${group.title} · ${group.count}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        group.note?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val cards = group.cards
        when {
            cards == null -> Unit

            cards.isEmpty() && group.place != SPELLBOOK -> Text(
                "None yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            byTier -> TierRunsGrid(
                tierRuns(cards),
                onOpen = { i -> onOpen(cards, i, title) },
                origin = { origin(title, it) },
                marks = marks,
                castWith = castWith,
            )

            else -> ExtrasGrid(
                cards,
                onOpen = { i -> onOpen(cards, i, title) },
                origin = { origin(title, it) },
                marks = marks,
                castWith = castWith,
            )
        }
    }
}

/** A Tier's spells within a group: [start] is the first one's index in the group. */
private data class TierRun(val tier: Int?, val start: Int, val cards: List<Card>)

/** [cards] (already in Tier order) cut where the Tier changes. */
private fun tierRuns(cards: List<Card>): List<TierRun> {
    val runs = mutableListOf<TierRun>()
    cards.forEachIndexed { i, card ->
        val last = runs.lastOrNull()
        if (last != null && last.tier == card.tier) {
            runs[runs.lastIndex] = last.copy(cards = last.cards + card)
        } else {
            runs += TierRun(card.tier, i, listOf(card))
        }
    }
    return runs
}

/** A spell's Tier as the catalog's hero page heads it: Gold (no tier), Tier I–III, Ultimate (Wish). */
internal fun spellTierTitle(tier: Int?): String = when {
    tier == null || tier == 0 -> "Gold (no tier)"
    tier >= ULTIMATE_TIER -> "Ultimate"
    else -> "Tier ${"I".repeat(tier)}"
}

private const val ULTIMATE_TIER = 4

/** The hero sheet's spellbook, opening cards in [focus] (keys `"<title>/<card id>"`, as CardRow). */
@Composable
internal fun ExtrasSection(
    table: TableUi,
    hero: HeroView,
    focus: CardFocusState,
    modifier: Modifier = Modifier,
    onOpenSheet: (() -> Unit)? = null,
) {
    ExtrasSection(
        table,
        hero,
        onOpen = { cards, index, title ->
            focus.open(cards.map { FocusCard(it, key = "$title/${it.id}") }, index, title = title)
        },
        modifier = modifier,
        origin = { title, card -> Modifier.cardFocusOrigin(focus, "$title/${card.id}") },
        onOpenSheet = onOpenSheet,
    )
}

/**
 * Landscape thumbnails filling the width, as many columns as fit at [MIN_THUMB] wide or more (fewer
 * with large text). A card with a [SpellMark] has it under it: offered now (a bronze edge), or why not
 * (dimmed); words and an edge, never colour alone.
 */
@Composable
private fun ExtrasGrid(
    cards: List<Card>,
    onOpen: (Int) -> Unit,
    origin: (Card) -> Modifier,
    marks: Map<String, SpellMark> = emptyMap(),
    castWith: Map<String, String> = emptyMap(),
) {
    GridBox { m -> Cells(cards, m, onOpen, origin, marks, castWith) }
}

/**
 * A group's Tiers, each headed ("Tier I · 3"), on one grid: a Tier with fewer spells than the grid has
 * columns shares its row with the next ones that fit (re-check: in landscape a Tier of three on a row of
 * its own left a third of the width empty); a longer one starts on a row of its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TierRunsGrid(
    runs: List<TierRun>,
    onOpen: (Int) -> Unit,
    origin: (Card) -> Modifier,
    marks: Map<String, SpellMark>,
    castWith: Map<String, String>,
) {
    val density = LocalDensity.current
    GridBox { m ->
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(m.gap),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            runs.forEach { run ->
                val span = minOf(run.cards.size, m.columns)
                val width = with(density) { (m.cellPx * span + m.gapPx * (span - 1)).toDp() }
                Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        "${spellTierTitle(run.tier)} · ${run.cards.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs).semantics { heading() },
                    )
                    Cells(run.cards, m, { i -> onOpen(run.start + i) }, origin, marks, castWith)
                }
            }
        }
    }
}

/**
 * The grid's measure in [GridBox]'s width: [columns] cells [cell] wide, [cellPx] whole pixels (a row of
 * columns rounded up one by one would be a pixel too wide and wrap its last card), [gap] between.
 */
private class GridMeasure(val columns: Int, val cell: Dp, val cellPx: Int, val gap: Dp, val gapPx: Int)

@Composable
private fun GridBox(content: @Composable (GridMeasure) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = Spacing.sm
        val columns = gridColumns(maxWidth, density.fontScale)
        val gapPx = with(density) { gap.roundToPx() }
        val cellPx = ((constraints.maxWidth - gapPx * (columns - 1)) / columns).coerceAtLeast(1)
        val cell = max(MIN_THUMB_FLOOR, with(density) { cellPx.toDp() })
        content(GridMeasure(columns, cell, cellPx, gap, gapPx))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongParameterList")
private fun Cells(
    cards: List<Card>,
    m: GridMeasure,
    onOpen: (Int) -> Unit,
    origin: (Card) -> Modifier,
    marks: Map<String, SpellMark>,
    castWith: Map<String, String>,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(m.gap), verticalArrangement = Arrangement.spacedBy(m.gap)) {
        cards.forEachIndexed { i, card ->
            ExtraThumb(card, m.cell, origin(card), marks[card.id], castWith[card.id]) { onOpen(i) }
        }
    }
}

/**
 * How many spell cards side by side in [width]: each at least [MIN_THUMB] wide, more with larger text
 * (up to [MAX_WIDTH_SCALE] times, so the caption under a card keeps room), at most [MAX_COLUMNS].
 */
internal fun gridColumns(width: Dp, fontScale: Float): Int {
    val scale = fontScale.coerceIn(1f, MAX_WIDTH_SCALE)
    return ((width + Spacing.sm) / (MIN_THUMB * scale + Spacing.sm)).toInt().coerceIn(1, MAX_COLUMNS)
}

@Composable
private fun ExtraThumb(card: Card, width: Dp, origin: Modifier, mark: SpellMark?, hint: String?, onClick: () -> Unit) {
    // A question's mark says more than the hand's line: one or the other under a card.
    val line = mark?.text ?: hint
    Column(
        Modifier.width(width).clearAndSetSemantics {
            contentDescription = listOfNotNull(spokenCard(card), line).joinToString(". ")
            role = Role.Button
            onClick(label = "Look at the card") {
                onClick()
                true
            }
        },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(6.dp),
            border = if (mark?.offered == true) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, GoaTheme.colors.engravedBorder)
            },
            modifier = origin.alpha(if (mark != null && !mark.offered) DIMMED else 1f),
        ) {
            // TableCard's width is a portrait card's: this one keeps the height of a [width]-wide spell.
            TableCard(card, width = width / LANDSCAPE_CARD_ASPECT * CARD_ASPECT)
        }
        when {
            mark != null -> ThumbLine(
                mark.short,
                weight = if (mark.offered) FontWeight.SemiBold else null,
                color = MaterialTheme.colorScheme.run { if (mark.offered) primary else onSurfaceVariant },
            )

            hint != null -> ThumbLine(hint, weight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * The line under a spell card, two lines at most and never cut off: a long card name ("Cast with
 * Elementary Evocation") steps the text down until it fits (Clip: an ellipsis would count as fitting).
 */
@Composable
private fun ThumbLine(text: String, weight: FontWeight?, color: Color) {
    val style = MaterialTheme.typography.labelSmall
    BasicText(
        text,
        style = style.copy(fontWeight = weight, color = color),
        maxLines = 2,
        overflow = TextOverflow.Clip,
        autoSize = TextAutoSize.StepBased(minFontSize = THUMB_LINE_MIN, maxFontSize = style.fontSize),
    )
}

internal const val SPELLBOOK = "spellbook"
internal const val REMOVED = "removed"
internal const val BOX = "box"
private const val DIMMED = 0.5f
private const val MAX_WIDTH_SCALE = 1.6f
private val MIN_THUMB = 104.dp
private val MIN_THUMB_FLOOR = 72.dp
private val THUMB_LINE_MIN = 8.sp
private const val MAX_COLUMNS = 6
