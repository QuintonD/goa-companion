package dev.quinton.goa.feature.board.play.table

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText

// What the hero sheet says (Android HeroState, the web app's HeroDetail), shared. Built only from the
// engine's view (p.4 open information: a facedown card stays a card back, an enemy's hand and tucked
// cards are never named) and the turn track (ADR-041), so it can't show more than the table does.

/** Where a hero is in this turn, as the sheet says it. */
enum class HeroTurnState {
    /** Before the reveal: a card is down, facedown (p.7). */
    PLAYED,

    /** Before the reveal: no card down yet. */
    CHOOSING,

    /** Before the reveal: no cards in hand, so the hero passes (p.7). */
    NO_CARDS,

    DONE,
    NOW,
    NEXT,
    LATER,
    PASSED,
    SKIPPED,
}

/**
 * A hero's card this turn: [cards] as the person may see them ([CardView.cardId] null = facedown to
 * them), [initiative] as it counts after the reveal (items and effects, p.7), [printed] the printed
 * initiative of the first faceup card, [place] the order among the heroes with a card, [action] the
 * action chosen.
 */
data class HeroTurn(
    val state: HeroTurnState,
    val cards: List<CardView>,
    val initiative: Int? = null,
    val printed: Int? = null,
    val place: Int? = null,
    val tied: Boolean = false,
    val action: ActionType? = null,
)

/** [hero]'s card this turn, or null outside the play and resolve phases. */
fun heroTurn(table: TableUi, hero: HeroView): HeroTurn? = when (table.view.phase) {
    GamePhase.PLAY_CARDS -> {
        val played = hero.played
        when {
            played != null -> HeroTurn(HeroTurnState.PLAYED, listOf(played) + hero.alsoPlayed)
            hero.handCount == 0 -> HeroTurn(HeroTurnState.NO_CARDS, emptyList())
            else -> HeroTurn(HeroTurnState.CHOOSING, emptyList())
        }
    }

    GamePhase.RESOLVE -> table.track?.slots?.firstOrNull { it.heroId == hero.heroId }?.let { slot ->
        val first = slot.cards.firstOrNull()?.takeIf { !it.facedown }?.cardId?.let(table::card)
        HeroTurn(
            state = when (slot.state) {
                SlotState.DONE -> HeroTurnState.DONE
                SlotState.NOW -> HeroTurnState.NOW
                SlotState.NEXT -> HeroTurnState.NEXT
                SlotState.LATER -> HeroTurnState.LATER
                SlotState.PASSED -> HeroTurnState.PASSED
                SlotState.SKIPPED -> HeroTurnState.SKIPPED
            },
            cards = slot.cards,
            initiative = slot.initiative,
            printed = first?.initiative,
            place = slot.place,
            tied = slot.tied,
            action = slot.action,
        )
    }

    GamePhase.END_OF_ROUND, GamePhase.GAME_OVER -> null
}

/** The state as the word on the card's band (as on the turn track). */
fun turnWord(state: HeroTurnState): String = when (state) {
    HeroTurnState.PLAYED -> "FACEDOWN"
    HeroTurnState.CHOOSING -> "CHOOSING"
    HeroTurnState.NO_CARDS -> "PASS"
    HeroTurnState.DONE -> "DONE"
    HeroTurnState.NOW -> "NOW"
    HeroTurnState.NEXT -> "NEXT"
    HeroTurnState.LATER -> "LATER"
    HeroTurnState.PASSED -> "PASS"
    HeroTurnState.SKIPPED -> "OUT"
}

/** The lines beside the card: what the state means, the action, the initiative, the order. */
fun turnLines(table: TableUi, hero: HeroView, turn: HeroTurn): List<String> {
    val first = turn.cards.firstOrNull()
    val card = first?.takeIf { !it.facedown }?.cardId?.let(table::card)
    val own = hero.heroId in table.mine
    return buildList {
        add(
            when (turn.state) {
                HeroTurnState.PLAYED -> when {
                    own -> "Played facedown: the others see it at the reveal (p.7)."
                    card != null -> "Played facedown; open in this puzzle."
                    else -> "Played facedown: revealed when everyone has played (p.7)."
                }

                HeroTurnState.CHOOSING -> if (own) {
                    "No card played yet: choose one from your hand."
                } else {
                    "No card played yet."
                }

                HeroTurnState.NO_CARDS -> "No cards in hand: passes this turn (p.7)."

                HeroTurnState.DONE -> "Resolved: on the Turn ${table.view.turn} slot."

                HeroTurnState.NOW -> "Resolving this card now."

                HeroTurnState.NEXT -> "Acts next."

                HeroTurnState.LATER -> "Acts later."

                HeroTurnState.PASSED -> "Passed: no card this turn (p.7)."

                HeroTurnState.SKIPPED -> "The card left the table unresolved (p.10)."
            },
        )
        turn.action?.let { add("Chose ${actionWord(it, card)}.") }
        turn.initiative?.let { counted ->
            val printed = turn.printed?.takeIf { it != counted }?.let { " · printed $it" }.orEmpty()
            add("Initiative $counted$printed")
        }
        val upcoming = turn.state == HeroTurnState.NEXT || turn.state == HeroTurnState.LATER
        turn.place?.takeIf { upcoming }?.let { add("${PlayText.ordinal(it)} to act this turn") }
        if (turn.tied) add("Tied on initiative: the coin or the team decides (p.7).")
    }
}

/** Where the hero is: its Zone, or off the board with when it may come back. */
fun heroWhere(table: TableUi, hero: HeroView): String {
    val at = hero.at ?: return "Off the board: may respawn in their Throne Zone before acting on their next card (p.7)."
    return "On the board · ${BoardText.zone(table.map, table.geometry.zoneOf(at))}"
}

/** The item icons grouped by stat, in the engine's (stat) order: "+1 Defense ×2". */
fun itemGroups(stats: List<Stat>): List<Pair<Stat, Int>> =
    stats.groupingBy { it }.eachCount().entries.sortedBy { it.key.ordinal }.map { it.key to it.value }

fun itemText(stat: Stat, count: Int): String =
    "+1 ${PlayText.stat(stat)}" + if (count > 1) " ×$count" else ""

/** One of the dashboard's four Turn slots: its cards, the state on their band, none for an empty slot. */
data class DashboardSlot(
    val turn: Int,
    val cards: List<CardView>,
    val word: String? = null,
    val now: Boolean = false,
    val next: Boolean = false,
    val stepped: Boolean = false,
)

/**
 * This round on the dashboard (p.7): each turn's resolved card on its slot (a card resolved facedown stays
 * a back), this turn's card with its state; Emmitt's second card shares the slot.
 */
fun dashboardSlots(table: TableUi, hero: HeroView): List<DashboardSlot> {
    val turn = heroTurn(table, hero)
    return (1..TURNS_IN_ROUND).map { t ->
        val resolved = hero.resolved.filter { it.slot == t }
        val current = turn?.takeIf { t == table.view.turn }
        val active = resolved.any { it.active }
        when {
            current != null && current.state != HeroTurnState.DONE -> {
                val played = current.cards.map { it.cardId }
                DashboardSlot(
                    t,
                    current.cards + resolved.filter { it.cardId !in played },
                    turnWord(current.state),
                    now = current.state == HeroTurnState.NOW,
                    next = current.state == HeroTurnState.NEXT,
                    stepped = current.state == HeroTurnState.SKIPPED,
                )
            }

            resolved.isNotEmpty() || current != null -> DashboardSlot(
                t,
                resolved.ifEmpty { current?.cards.orEmpty() },
                if (active) "ACTIVE" else "DONE",
                next = active,
                stepped = !active,
            )

            else -> DashboardSlot(t, emptyList())
        }
    }
}

const val TURNS_IN_ROUND = 4
