package dev.quinton.goa.feature.board.play.puzzles

import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Card

/*
 * A full-round puzzle on screen (PRODUCT §10, ADR-052), shared by the phone and the web app: which turn
 * it is and the enemy's known plays, each marked by what has become of it.
 */

/** What has become of one known card. */
enum class KnownCardState {
    /** Already played: it is on the hero's dashboard, or its turn has passed. */
    PLAYED,

    /** This turn's card. */
    NOW,

    /** A later turn's card, still in the hand. */
    COMING,

    /** It left the hand before its turn (spent on a defense, discarded): the hero plays anything it has left. */
    GONE,

    /** The fifth card of the hand, not one of the four it plays: it can still defend with it. */
    KEPT,
}

data class KnownCardUi(
    val turn: Int,
    val cardId: String,
    val card: Card?,
    val plan: String?,
    val state: KnownCardState,
    val spaces: List<Hex> = emptyList(),
    /** Where the plan's target stands now, when it has one on the board. */
    val targetsAt: List<Hex> = emptyList(),
)

data class KnownHeroUi(val heroId: String, val name: String, val cards: List<KnownCardUi>) {
    /** The card this turn asks about, for the line under the strip. */
    val now: KnownCardUi? get() = cards.firstOrNull { it.state == KnownCardState.NOW }

    /** The line under this hero's cards in [turn]: this turn's plan, or that this turn's card is gone. */
    fun lineFor(turn: Int): KnownCardUi? =
        now ?: cards.firstOrNull { it.turn == turn && it.state == KnownCardState.GONE }

    /** The turns after [turn], for "Show later turns": what is still to come, or has already left the hand. */
    fun later(turn: Int): List<KnownCardUi> = cards.filter {
        it.turn > turn && (it.state == KnownCardState.COMING || it.state == KnownCardState.GONE)
    }
}

/** "Turn 2 of 4" and the strip of the enemy's cards. */
data class RoundUi(val turn: Int, val heroes: List<KnownHeroUi>, val laterShown: Boolean = false) {
    val heading: String get() = "Turn $turn of $TURNS"

    /** Where this turn's plans still to resolve go, and the later turns' while shown: outlined on the board. */
    val planned: Set<Hex>
        get() = heroes.flatMap { h ->
            h.now?.let { it.spaces + it.targetsAt }.orEmpty() + if (laterShown) {
                h.later(turn).filter { it.state == KnownCardState.COMING }.flatMap { it.spaces + it.targetsAt }
            } else {
                emptyList()
            }
        }.toSet()

    companion object {
        const val TURNS = 4
    }
}

/** The round's state from [view], or null when [info] is a turn-sized puzzle. */
fun roundUi(
    info: PuzzleInfo,
    view: GameView,
    cards: Map<String, Card>,
    laterShown: Boolean = false,
    name: (String) -> String,
): RoundUi? {
    if (!info.fullRound) return null
    val heroes = info.known.groupBy { it.heroId }.map { (heroId, plays) ->
        val hero = view.heroes.firstOrNull { it.heroId == heroId }
        val resolved = hero?.resolved.orEmpty().mapNotNull { it.cardId }.toSet()
        val discarded = hero?.discard.orEmpty().mapNotNull { it.cardId }.toSet()
        KnownHeroUi(
            heroId,
            name(heroId),
            plays.sortedBy { it.turn }.map { play ->
                val state = when {
                    play.cardId in resolved -> KnownCardState.PLAYED
                    play.cardId in discarded -> KnownCardState.GONE
                    play.turn < view.turn -> KnownCardState.PLAYED
                    play.turn == view.turn -> KnownCardState.NOW
                    else -> KnownCardState.COMING
                }
                val targets = play.targets.mapNotNull { id ->
                    view.minions.firstOrNull { it.id == id }?.at ?: view.pieces.firstOrNull { it.id == id }?.at
                }
                KnownCardUi(play.turn, play.cardId, cards[play.cardId], play.plan, state, play.spaces, targets)
            } + info.keeps.filter { it.heroId == heroId }.map { kept ->
                val state = when (kept.cardId) {
                    in resolved -> KnownCardState.PLAYED
                    in discarded -> KnownCardState.GONE
                    else -> KnownCardState.KEPT
                }
                KnownCardUi(0, kept.cardId, cards[kept.cardId], null, state)
            },
        )
    }
    return RoundUi(view.turn, heroes, laterShown)
}
