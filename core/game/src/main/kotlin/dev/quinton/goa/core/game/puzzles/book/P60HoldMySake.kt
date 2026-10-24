package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C60 "Hold My Saké". */
object P60HoldMySake : Puzzle() {
    override val concept = "C60"

    override val info = PuzzleInfo(
        id = "p60",
        number = 60,
        title = "Hold My Saké",
        difficulty = 3,
        theme = "Card states: a discarded card comes back",
        heroes = listOf("takahide", "brogan", "dodger", "wasp"),
        goal = PuzzleGoal.DefeatHero("dodger", Deadline.END_OF_TURN, "Defeat Dodger this turn."),
        intro = "Round 4, turn 3. Takahide has played Hold my Saké and acts next. Brogan has played Shield; " +
            "he keeps Onslaught in hand, and Mad Dash is in his discard. Dodger has played Dark Ritual " +
            "(initiative 3) and has no cards left.",
        nudges = listOf(
            "Brogan's best card for this is already in his discard pile.",
            "Hold my Saké reaches into the discard too.",
            "Hit the minion with Hold my Saké, then let Brogan swap Shield for Mad Dash from his discard.",
        ),
        explanation = "When two cards are swapped, all their states are swapped (p.10 Swap a Card): the " +
            "discarded Mad Dash becomes Brogan's played, unresolved card and Shield goes to the discard. Mad " +
            "Dash then resolves at its own Initiative, 7, before Dodger's 3 (p.7): Brogan charges 2 spaces " +
            "in a straight line to a space next to Dodger and attacks (p.11). She has no card, so she is " +
            "defeated (p.13). Onslaught from his hand would act sooner (11), but it only hits an adjacent " +
            "unit, and Dodger is 2 spaces away; keeping Shield does nothing to her. A discarded card is not " +
            "gone for the round: a swap can put it back into play.",
        rules = listOf(
            "p.7 Check the Initiative",
            "p.10 Swap a Card",
            "p.11 Movement in the Card Text",
            "p.13 Attacking a Hero",
        ),
    )

    override val playerSeats = setOf("takahide", "brogan")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 3
        tieBreaker = Team.RED
        clearMinions()
        hero("takahide", Team.BLUE, Hex(0, -4)) {
            level = 5
            played("takahide-red-3a")
            resolved(1, "takahide-gold")
            resolved(2, "takahide-silver")
            hand("takahide-green-2b", "takahide-blue-2b")
            items("takahide-red-2b", "takahide-red-3b", "takahide-green-2a", "takahide-blue-2a")
        }
        hero("brogan", Team.BLUE, Hex(1, -2)) {
            played("brogan-green-1")
            resolved(1, "brogan-silver")
            resolved(2, "brogan-blue-1")
            hand("brogan-gold")
            discard("brogan-red-1")
        }
        hero("dodger", Team.RED, Hex(3, -3)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            discard("dodger-red-1", "dodger-blue-1")
            hand()
        }
        hero("wasp", Team.RED, Hex(4, -7)) {
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            hand("wasp-red-1", "wasp-blue-1")
        }
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.RED, MinionType.MELEE, Hex(4, -4))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "takahide")
    }

    // Hold my Saké's only target (the Red ranged minion) is not asked; ending on (3,-2) neither is Dodger.
    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("brogan"),
        card("brogan-red-1"),
        action(ActionType.PRIMARY),
        space(3, -2),
    )

    override val nudgedMoves = solution.take(3)

    override val wrongTries = listOf(
        WrongTry(
            "Swap in Onslaught: it acts at 11 but only hits adjacent units",
            listOf(action(ActionType.PRIMARY), hero("brogan"), card("brogan-gold")),
        ),
        WrongTry(
            "No swap: Shield does nothing to Dodger",
            listOf(action(ActionType.PRIMARY), hero("brogan"), decline()),
        ),
    )
}
