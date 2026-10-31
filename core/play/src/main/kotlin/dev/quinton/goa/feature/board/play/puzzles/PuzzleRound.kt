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
}

data class KnownCardUi(
    val turn: Int,
    val cardId: String,
    val card: Card?,
    val plan: String?,
    val state: KnownCardState,
    val spaces: List<Hex> = emptyList(),
)

data class KnownHeroUi(val heroId: String, val name: String, val cards: List<KnownCardUi>) {
    /** The card this turn asks about, for the line under the strip. */
    val now: KnownCardUi? get() = cards.firstOrNull { it.state == KnownCardState.NOW }
}

/** "Turn 2 of 4" and the strip of the enemy's cards. */
data class RoundUi(val turn: Int, val heroes: List<KnownHeroUi>) {
    val heading: String get() = "Turn $turn of $TURNS"

    /** Where this turn's plans still to resolve go: outlined on the board. */
    val planned: Set<Hex> get() = heroes.flatMap { it.now?.spaces.orEmpty() }.toSet()

    companion object {
        const val TURNS = 4
    }
}

/** The round's state from [view], or null when [info] is a turn-sized puzzle. */
fun roundUi(info: PuzzleInfo, view: GameView, cards: Map<String, Card>, name: (String) -> String): RoundUi? {
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
                KnownCardUi(play.turn, play.cardId, cards[play.cardId], play.plan, state, play.spaces)
            },
        )
    }
    return RoundUi(view.turn, heroes)
}
