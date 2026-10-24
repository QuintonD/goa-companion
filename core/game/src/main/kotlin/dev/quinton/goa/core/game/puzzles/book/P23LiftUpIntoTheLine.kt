package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C23 "Lift Up into the Line", turned half a turn ((q, r) to (3 - q, -7 - r)) with the
 * teams swapped, so the tempting ring step comes first in the engine's option order.
 */
object P23LiftUpIntoTheLine : Puzzle() {
    override val concept = "C23"

    override val info = PuzzleInfo(
        id = "p23",
        number = 23,
        title = "Lift Up into the Line",
        difficulty = 2,
        theme = "Moving an enemy without changing its distance",
        heroes = listOf("wasp", "swift", "dodger", "brogan"),
        goal = PuzzleGoal.DefeatHero("dodger", Deadline.END_OF_TURN, "Defeat Dodger this turn."),
        intro = "Round 3, turn 4. Wasp has played Lift Up and acts next, then Swift with Snipe. Dodger has " +
            "played Dark Ritual (initiative 3) and has no cards left in hand. Brogan has already acted.",
        nudges = listOf(
            "Snipe only hits a target exactly four spaces away, in a straight line.",
            "Lift Up slides a unit around Wasp without bringing it closer or pushing it away, and it can " +
                "do that twice.",
            "Lift Up Dodger south-east, one step farther from Swift, then repeat it once more.",
        ),
        explanation = "Lift Up moves a unit 1 space without changing its distance from Wasp, and its repeat " +
            "must use the same target (card text; Wasp FAQ \"the distance between you and the target must " +
            "remain unchanged\"; p.10 Repeat). Dodger already stands on Swift's south-east line, two spaces " +
            "away, and she has only two such steps: north-east, from where the only further step leads " +
            "back, and south-east, along Swift's line, from where the repeat goes south-east again. Snipe " +
            "needs its target at its maximum range and in a straight line (p.9, p.19): after two steps " +
            "Dodger is exactly four spaces south-east of Swift, and with no card she cannot defend (p.13). " +
            "One step is not enough: three spaces is not Snipe's maximum range. The lesson: a move that keeps " +
            "a distance to one hero can still change the distance to another. Wasp at 10 acts before Swift " +
            "at 8, and both before Dodger at 3 (p.7).",
        rules = listOf("p.7 Check the initiative", "p.9 Range", "p.10 Repeat", "p.19 Straight line"),
    )

    override val playerSeats = setOf("wasp", "swift")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        hero("wasp", Team.BLUE, Hex(1, -1)) {
            played("wasp-blue-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-red-1")
            resolved(3, "wasp-silver")
            hand("wasp-green-1")
        }
        hero("swift", Team.BLUE, Hex(-1, -3)) {
            played("swift-red-1")
            resolved(1, "swift-gold")
            resolved(2, "swift-silver")
            resolved(3, "swift-green-1")
            hand("swift-blue-1")
        }
        hero("dodger", Team.RED, Hex(-1, -1)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-1")
            discard("dodger-blue-1")
            hand()
        }
        hero("brogan", Team.RED, Hex(4, -7)) {
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-red-1")
            resolved(4, "brogan-blue-1")
            hand("brogan-green-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "wasp")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("dodger"),
        space(-1, 0),
        yes(),
        space(-1, 1),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(3)

    override val wrongTries = listOf(
        WrongTry(
            "Lift Dodger north-east: from there the only further step leads back",
            listOf(action(ActionType.PRIMARY), hero("dodger"), space(0, -2)),
        ),
        WrongTry(
            "One step only: Dodger is three spaces from Swift, not four",
            listOf(action(ActionType.PRIMARY), hero("dodger"), space(-1, 0), decline()),
        ),
    )
}
