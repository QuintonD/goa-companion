package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C35 "Onslaught's Forced Step". */
object P35OnslaughtsForcedStep : Puzzle() {
    override val concept = "C35"

    override val info = PuzzleInfo(
        id = "p35",
        number = 35,
        title = "Onslaught's Forced Step",
        difficulty = 2,
        theme = "A forced follow-up step",
        heroes = listOf("brogan", "wasp", "xargatha", "swift"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn."),
        intro = "Round 4, turn 4. Brogan has played Onslaught and acts next; he has no cards left in hand. " +
            "Swift has played Snipe (initiative 8); Xargatha has played Threatening Slash (7) and keeps " +
            "Stone Gaze (Defense 5). Both will attack Brogan if they can. Wasp has already acted.",
        nudges = listOf(
            "Every space you can walk to is covered.",
            "Onslaught's follow-up step is not optional, so choose where it takes you.",
            "Onslaught on the minion north-east of Brogan.",
        ),
        explanation = "Onslaught's step ends with \"if able\": failing it never stops the action (p.10, " +
            "p.17 If able), but when it can be made it must be (Brogan FAQ: \"Brogan must move into the " +
            "space where the target was, if he is able to\"). Defeating the minion north-east of Brogan " +
            "carries him into its space: two spaces from Xargatha, whose Threatening Slash reaches only " +
            "adjacent units (p.13), and on none of Swift's straight lines at exactly 4 (Snipe, p.19). The " +
            "minion to his east would pull him into its space, next to Xargatha. Onslaught on Xargatha " +
            "fails: Stone Gaze defends 5, +1 for the Blue minion beside her (p.14), against 4, so the space " +
            "stays occupied and he stays beside her. A Movement step lands on Snipe's line or next to her. " +
            "The lesson: a forced move is still a move you aim. Pick the target for where it leaves you.",
        rules = listOf(
            "p.10 Mandatory steps",
            "p.13 Non-ranged attacks",
            "p.17 If able",
            "p.19 Straight line",
        ),
    )

    override val playerSeats = setOf("brogan")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        hero("brogan", Team.RED, Hex(-2, 0)) {
            played("brogan-gold")
            resolved(1, "brogan-red-1")
            resolved(2, "brogan-green-1")
            resolved(3, "brogan-blue-1")
            discard("brogan-silver")
            hand()
        }
        hero("wasp", Team.RED, Hex(4, -2)) {
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            resolved(4, "wasp-green-1")
            hand("wasp-blue-1")
        }
        hero("xargatha", Team.BLUE, Hex(-2, 1)) {
            played("xargatha-red-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-green-1")
            hand("xargatha-blue-1")
        }
        hero("swift", Team.BLUE, Hex(1, -4)) {
            played("swift-red-1")
            resolved(1, "swift-gold")
            resolved(2, "swift-silver")
            resolved(3, "swift-green-1")
            hand("swift-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(-1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(-1, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "brogan")
    }

    override val solution = listOf(action(ActionType.PRIMARY), unitAt(-1, -1))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Onslaught on the minion to the east: the step lands next to Xargatha",
            listOf(action(ActionType.PRIMARY), unitAt(-1, 0)),
        ),
        WrongTry(
            "Onslaught on Xargatha: she defends, and Brogan stays beside her",
            listOf(action(ActionType.PRIMARY), hero("xargatha")),
        ),
        WrongTry("Step away: every space is on Snipe's line or next to Xargatha", listOf(action(ActionType.MOVEMENT))),
        WrongTry("Hold: Xargatha is adjacent", listOf(action(ActionType.HOLD))),
    )
}
