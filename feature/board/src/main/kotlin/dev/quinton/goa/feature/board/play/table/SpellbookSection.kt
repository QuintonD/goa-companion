package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
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
 * The hero sheet's section for a hero's extra cards (nothing for a hero without any): each group's
 * cards as landscape thumbnails in a grid; tap one to see it large in the card focus viewer, swiping
 * through its group. [onOpen] opens the viewer; [origin] marks a thumbnail the card grows out of.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExtrasSection(
    table: TableUi,
    hero: HeroView,
    onOpen: (cards: List<Card>, index: Int, title: String) -> Unit,
    modifier: Modifier = Modifier,
    origin: (title: String, card: Card) -> Modifier = { _, _ -> Modifier },
) {
    if (hero.extras.isEmpty()) return
    val name = table.hero(hero.heroId).name
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            extrasTitle(table, hero).uppercase(),
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        extrasGroups(table, hero).forEach { group ->
            val title = "$name · ${group.title}"
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

                    else -> ExtrasGrid(cards, onOpen = { i -> onOpen(cards, i, title) }, origin = { origin(title, it) })
                }
            }
        }
    }
}

/** The hero sheet's spellbook, opening cards in [focus] (keys `"<title>/<card id>"`, as CardRow). */
@Composable
internal fun ExtrasSection(table: TableUi, hero: HeroView, focus: CardFocusState, modifier: Modifier = Modifier) {
    ExtrasSection(
        table,
        hero,
        onOpen = { cards, index, title ->
            focus.open(cards.map { FocusCard(it, key = "$title/${it.id}") }, index, title = title)
        },
        modifier = modifier,
        origin = { title, card -> Modifier.cardFocusOrigin(focus, "$title/${card.id}") },
    )
}

/** Landscape thumbnails filling the width, as many columns as fit at [MIN_THUMB] wide or more. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtrasGrid(cards: List<Card>, onOpen: (Int) -> Unit, origin: (Card) -> Modifier) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = Spacing.sm
        val columns = ((maxWidth + gap) / (MIN_THUMB + gap)).toInt().coerceIn(1, MAX_COLUMNS)
        val width = max(MIN_THUMB_FLOOR, (maxWidth - gap * (columns - 1)) / columns)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            cards.forEachIndexed { i, card -> ExtraThumb(card, width, origin(card)) { onOpen(i) } }
        }
    }
}

@Composable
private fun ExtraThumb(card: Card, width: Dp, origin: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        modifier = origin.clearAndSetSemantics {
            contentDescription = spokenCard(card)
            role = Role.Button
            onClick(label = "Look at the card") {
                onClick()
                true
            }
        },
    ) {
        // TableCard's width is a portrait card's: this one keeps the height of a [width]-wide spell.
        TableCard(card, width = width / LANDSCAPE_CARD_ASPECT * CARD_ASPECT)
    }
}

private const val SPELLBOOK = "spellbook"
private const val REMOVED = "removed"
private const val BOX = "box"
private val MIN_THUMB = 104.dp
private val MIN_THUMB_FLOOR = 72.dp
private const val MAX_COLUMNS = 4
