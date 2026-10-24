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
        difficulty = 2,
        theme = "Swaps, immunity and blocking",
        heroes = listOf("hanu", "wuk", "xargatha", "brogan"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn: neither Hanu nor Wuk may be defeated."),
        intro = "Round 4, turn 4. Hanu has played Unexpected Journey (11 with his Initiative item) and acts " +
            "next. Then Xargatha acts (Long Thrust, 9), then Brogan (Mad Dash, 8): both have an Initiative " +
            "item. Wuk has already acted; his hand is empty.",
        nudges = listOf(
            "Long Thrust's range is 1, plus 1 for each enemy unit next to Xargatha. Who is next to her now?",
            "Swapping two pieces that stand side by side leaves them side by side.",
            "Swap with Brogan.",
        ),
        explanation = "Wuk has no card, so any attack that reaches him defeats him (p.13). There are two " +
            "threats. From where he stands, Brogan's Mad Dash runs two spaces to a space next to Wuk (p.11). " +
            "Xargatha's Long Thrust has Range 1, +1 for each enemy unit adjacent to her (card text): 2 with " +
            "Hanu beside her, and Wuk is two spaces away. Swapping with Xargatha (p.19 Swap) fixes neither: " +
            "she and Hanu stay side by side, Wuk is still two from her, and Brogan still runs. Walking away " +
            "shortens her range but leaves Brogan's runs open, or puts Hanu next to her again. Swap with " +
            "Brogan: Hanu lands on his space, away from Xargatha, whose range drops to 1, and Brogan, now on " +
            "Hanu's old space, has no run that ends next to Wuk or Hanu. He is immune this turn, and at the end " +
            "of the turn the two swap back (card text; p.17 End of Turn). One swap can take away two threats.",
        rules = listOf(
            "p.9 Range",
            "p.11 Movement in the Card Text",
            "p.13 Attacking a Hero",
            "p.17 End of Turn",
            "p.19 Swap",
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
        hero("hanu", Team.BLUE, Hex(0, -4)) {
            level = 2
            played("hanu-blue-1")
            resolved(1, "hanu-gold")
            resolved(2, "hanu-silver")
            resolved(3, "hanu-green-1")
            hand("hanu-red-2b")
            items("hanu-red-2a")
        }
        hero("wuk", Team.BLUE, Hex(0, -6)) {
            level = 2
            resolved(1, "wuk-silver")
            resolved(2, "wuk-red-1")
            resolved(3, "wuk-green-2a")
            resolved(4, "wuk-gold")
            discard("wuk-blue-1")
            hand()
            items("wuk-green-2b")
        }
        hero("xargatha", Team.RED, Hex(1, -5)) {
            level = 2
            played("xargatha-red-2b")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-green-1")
            hand("xargatha-blue-1")
            items("xargatha-red-2a")
        }
        hero("brogan", Team.RED, Hex(-1, -4)) {
            level = 2
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-green-1")
            hand("brogan-blue-2b")
            items("brogan-blue-2a")
        }
        minion(Team.RED, MinionType.MELEE, Hex(-1, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -6))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "hanu")
    }

    override val solution = listOf(action(ActionType.PRIMARY), hero("brogan"))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Swap with Xargatha: she stays next to Hanu, and from his old space Wuk is still in her range 2",
            listOf(action(ActionType.PRIMARY), hero("xargatha")),
        ),
        WrongTry(
            "Walk away: Brogan's runs stay open, or Hanu ends beside Xargatha again",
            listOf(action(ActionType.MOVEMENT)),
        ),
        WrongTry("Hold: Xargatha's range 2 reaches Wuk", listOf(action(ActionType.HOLD))),
    )
}
