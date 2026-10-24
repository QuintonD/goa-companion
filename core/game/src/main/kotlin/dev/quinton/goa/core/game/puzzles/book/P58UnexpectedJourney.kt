package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C58 "Unexpected Journey". */
object P58UnexpectedJourney : Puzzle() {
    override val concept = "C58"

    override val info = PuzzleInfo(
        id = "p58",
        number = 58,
        title = "Unexpected Journey",
        difficulty = 3,
        theme = "Swaps, immunity and blocking",
        heroes = listOf("hanu", "wuk", "brogan", "xargatha"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn: neither Hanu nor Wuk may be defeated."),
        intro = "Round 4, turn 4. Hanu has played Unexpected Journey and acts next. Wuk has acted and has " +
            "no cards left, so he can't defend. After Hanu, Brogan acts (Mad Dash, 8 with his Initiative " +
            "item), then Xargatha (Threatening Slash, 7).",
        nudges = listOf(
            "Wuk can't defend: take away every way to reach him.",
            "Hanu is blocking Brogan's charge. If he leaves, someone must take his place.",
            "Swap with Xargatha.",
        ),
        explanation = "A swap is neither movement nor placement: the two pieces just change places (p.19 " +
            "Swap). Xargatha lands on Hanu's space, 2 spaces from both Wuk and Hanu, and Threatening Slash " +
            "only hits adjacent units. She also becomes the wall: Brogan's only charge to Wuk runs 2 " +
            "spaces through that very space, and Mad Dash needs exactly 2 empty spaces in a straight line " +
            "(p.11; Brogan FAQ); his other lines end far from Wuk and Hanu. Swapping with Brogan, walking " +
            "away or holding leaves Xargatha next to Wuk. At the end of the turn the two swap back (card " +
            "text; p.17 End of Turn).",
        rules = listOf(
            "p.11 Movement in the Card Text",
            "p.13 Attacking a Hero",
            "p.17 End of Turn",
            "p.18 Immune, Obstacle",
            "p.19 Swap",
            "Brogan FAQ: Mad Dash",
        ),
    )

    override val playerSeats = setOf("hanu", "wuk")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("hanu", Team.BLUE, Hex(2, -7)) {
            played("hanu-blue-1")
            resolved(1, "hanu-gold")
            resolved(2, "hanu-silver")
            resolved(3, "hanu-red-1")
            hand("hanu-green-1")
        }
        hero("wuk", Team.BLUE, Hex(1, -5)) {
            resolved(1, "wuk-silver")
            resolved(2, "wuk-red-1")
            resolved(3, "wuk-green-1")
            resolved(4, "wuk-gold")
            discard("wuk-blue-1")
            hand()
        }
        hero("brogan", Team.RED, Hex(2, -8)) {
            level = 2
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-green-1")
            hand("brogan-blue-2b")
            items("brogan-blue-2a")
        }
        hero("xargatha", Team.RED, Hex(2, -5)) {
            played("xargatha-red-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-green-1")
            hand("xargatha-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(5, -8))
        phase = PositionPhase.Resolving(next = "hanu")
    }

    override val solution = listOf(action(ActionType.PRIMARY), hero("xargatha"))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Swap with Brogan: Xargatha stays next to Wuk, and Brogan charges from Hanu's space",
            listOf(action(ActionType.PRIMARY), hero("brogan")),
        ),
        WrongTry("Walk away: Xargatha stays next to Wuk", listOf(action(ActionType.MOVEMENT))),
        WrongTry("Hold: Xargatha stays next to Wuk", listOf(action(ActionType.HOLD))),
    )
}
