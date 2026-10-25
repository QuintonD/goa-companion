package dev.quinton.goa.feature.board.play.table

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.ExtraPlaceView
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.cardKindLabel
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.ui.cards.isSpell

// What the table says about heroes, cards and the turn (Android TableStatus, TurnTrackView, TableCards,
// SpellbookSection; the web app's table), shared.

/**
 * The hero's counters for the number row, without those the extra cards' section already counts
 * (Gydion's "spellbook" and "spellsRemoved" are its groups' counts, ADR-040); "wishCasts" stays.
 */
fun shownCounters(hero: HeroView): Map<String, Int> =
    if (hero.extras.isEmpty()) hero.counters else hero.counters.filterKeys { it !in EXTRAS_COUNTERS }

val EXTRAS_COUNTERS = setOf("spellbook", "spellsRemoved")

/** A card thumbnail's caption: its Turn slot ([slots]) and "Active". */
fun cardCaption(view: CardView, slots: Boolean): String =
    listOfNotNull(view.slot?.takeIf { slots }?.let { "Turn $it" }, "Active".takeIf { view.active }).joinToString(" · ")

/** Before the reveal: the hero has played its card facedown (p.7); which card stays hidden. */
fun cardDown(table: TableUi, hero: HeroView): Boolean =
    table.view.phase == GamePhase.PLAY_CARDS && hero.played != null

/** What TalkBack hears for a hero's chip (full or compact). */
fun spokenChip(table: TableUi, hero: HeroView, place: Int?): String {
    val info = table.hero(hero.heroId)
    val acting = table.view.acting == hero.heroId
    val played = table.card(hero.played?.cardId)?.takeIf { hero.played?.facedown != true }
    return buildList {
        add(info.name)
        add(BoardText.team(hero.team))
        add(table.controllerLabel(hero))
        if (acting) add("acting now")
        place?.let { add("${PlayText.ordinal(it)} in initiative order") }
        (hero.initiative ?: played?.initiative)?.let { add("played initiative $it") }
        add("level ${hero.level}")
        add(BoardText.count(hero.coins, "coin"))
        add("${BoardText.count(hero.handCount, "card")} in hand")
        if (cardDown(table, hero)) add("card played facedown")
        if (hero.at == null) add("off the board")
    }.joinToString(", ")
}

fun stateWord(state: SlotState): String = when (state) {
    SlotState.DONE -> "Done"
    SlotState.NOW -> "Now"
    SlotState.NEXT -> "Next"
    SlotState.LATER -> "Later"
    SlotState.PASSED -> "Passed"
    SlotState.SKIPPED -> "Skipped"
}

/** The action as a word: "Movement", "Fast Travel", the primary as the card's type ("Attack"). */
fun actionWord(action: ActionType, card: Card?): String = when (action) {
    ActionType.PRIMARY -> PlayText.primary(card?.primary)
    ActionType.MOVEMENT -> "Movement"
    ActionType.FAST_TRAVEL -> "Fast Travel"
    ActionType.ATTACK -> "Attack"
    ActionType.CLEAR -> "Clear"
    ActionType.HOLD -> "Hold"
}

/** What TalkBack hears for a slot. */
fun spokenSlot(table: TableUi, slot: TrackSlot): String {
    val hero = table.heroView(slot.heroId)
    val cards = slot.cards.map { view ->
        view.cardId?.takeIf { !view.facedown }?.let(table::card)?.name ?: "a facedown card"
    }
    return buildList {
        slot.place?.let { add("${PlayText.ordinal(it)} to act") }
        add(table.hero(slot.heroId).name)
        add(BoardText.team(slot.team))
        hero?.let { add(table.controllerLabel(it)) }
        add(
            when (slot.state) {
                SlotState.DONE -> "done"
                SlotState.NOW -> "acting now"
                SlotState.NEXT -> "next"
                SlotState.LATER -> "later"
                SlotState.PASSED -> "passed, no card"
                SlotState.SKIPPED -> "its card left the table unresolved"
            },
        )
        slot.action?.let { add("used ${actionWord(it, table.card(slot.cards.firstOrNull()?.cardId))}") }
        if (cards.isNotEmpty()) add(cards.joinToString(" and "))
        slot.initiative?.let { add("initiative $it") }
        if (slot.tied) add("tied on initiative")
        if (slot.defeated) add("defeated this turn")
        if (slot.offBoard && slot.state != SlotState.DONE && slot.cards.isNotEmpty()) {
            add("off the board: may respawn when its card resolves")
        } else if (slot.offBoard) {
            add("off the board")
        }
    }.joinToString(", ")
}

/** "Wasp · Done · Movement · Initiative 10". */
fun slotCaption(table: TableUi, slot: TrackSlot, card: Card): String = listOfNotNull(
    table.hero(slot.heroId).name,
    stateWord(slot.state),
    slot.action?.let { actionWord(it, card) },
    slot.initiative?.let { "Initiative $it" },
).joinToString(" · ")

fun trackKey(heroId: String, cardId: String?) = "track/$heroId/$cardId"

/** What TalkBack reads for a card: name, tier and colour, initiative, values. */
fun spokenCard(card: Card): String = buildList {
    add(card.name)
    add(cardKindLabel(card))
    card.initiative?.let { add("Initiative $it") }
    card.values.attack?.let { add("Attack $it") }
    card.values.defense?.let { add("Defense $it") }
    card.values.movement?.let { add("Movement $it") }
    card.values.range?.let { add("Range $it") }
    card.values.radius?.let { add("Radius $it") }
}.joinToString(", ")

/** Official card scans are 729 × 1026. */
const val CARD_ASPECT = 729f / 1026f

/** One place of a hero's extra cards as the sheet shows it. [cards] null = facedown to this person. */
data class ExtrasGroup(
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
fun ultimateCard(table: TableUi, hero: HeroView): Card? =
    if (hero.ultimate) table.hero(hero.heroId).cards.firstOrNull { it.color == CardColor.PURPLE } else null

/** "Spellbook" for spell cards (Gydion), else "Extra cards". */
fun extrasTitle(table: TableUi, hero: HeroView): String {
    val any = hero.extras.firstNotNullOfOrNull { place -> place.cardIds?.firstNotNullOfOrNull(table::card) }
        ?: table.cards.values.firstOrNull { it.heroId == hero.heroId && it.isSpell }
    return if (any?.isSpell == true) "Spellbook" else "Extra cards"
}

/**
 * The groups of [hero]'s extra cards, in the engine's order: in the spellbook (named to Gydion's
 * player only; the others see how many), cast (removed faceup, public), not yet added (public; shown
 * only while there are some: the game starts with an empty spellbook, designer on BGG 3491113).
 */
fun extrasGroups(table: TableUi, hero: HeroView): List<ExtrasGroup> {
    val name = table.hero(hero.heroId).name
    val mine = hero.heroId in table.mine
    return hero.extras.mapNotNull { place ->
        val cards = place.cardIds?.mapNotNull(table::card)
        when (place.place) {
            EXTRAS_SPELLBOOK -> ExtrasGroup(
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

            EXTRAS_REMOVED -> ExtrasGroup(place.place, "Cast · removed faceup", place.count, cards)

            EXTRAS_BOX -> if (place.count == 0) {
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

fun placeTitle(place: ExtraPlaceView): String =
    place.place.replace('_', ' ').replaceFirstChar { it.uppercase() }

const val EXTRAS_SPELLBOOK = "spellbook"

const val EXTRAS_REMOVED = "removed"

const val EXTRAS_BOX = "box"
