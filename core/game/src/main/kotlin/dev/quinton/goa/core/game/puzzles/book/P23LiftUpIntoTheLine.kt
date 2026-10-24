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
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C23 "Lift Up into the Line" (changed: round 3, everyone level 2; Dodger keeps
 * Littlefinger of Death (Defense 5) next to her melee minion, and Swift is in Lift Up's radius too).
 */
object P23LiftUpIntoTheLine : Puzzle() {
    override val concept = "C23"

    override val info = PuzzleInfo(
        id = "p23",
        number = 23,
        title = "Lift Up into the Line",
        difficulty = 2,
        theme = "Moving units without changing their distance",
        heroes = listOf("wasp", "swift", "dodger", "brogan"),
        goal = PuzzleGoal.DefeatHero("dodger", Deadline.END_OF_TURN, "Defeat Dodger this turn."),
        intro = "Round 3, turn 4. Wasp has played Lift Up and acts next, then Swift with Snipe; Swift has an " +
            "Attack item. Dodger has played Dark Ritual (initiative 3) and keeps Littlefinger of Death " +
            "(Defense 5) in hand. Brogan has already acted.",
        nudges = listOf(
            "Snipe only hits a target exactly four spaces away, in a straight line. Who can Lift Up move?",
            "Lift Up can bring the two heroes into line either way round, two steps each. Count Dodger's " +
                "defense where she ends.",
            "Lift Up Dodger south-west, and once more south-west: away from her minion and into Swift's line.",
        ),
        explanation = "Lift Up moves a unit 1 space without changing its distance from Wasp, and its repeat " +
            "must use the same target (card text; Wasp FAQ \"the distance between you and the target must " +
            "remain unchanged\"; p.10 Repeat). Snipe needs its target at its maximum range and in a straight " +
            "line (p.9, p.19). Two plans line them up: lift Swift two steps south-west, four spaces south-east " +
            "of Dodger, or lift Dodger two steps south-west, four spaces west of Swift. Snipe attacks " +
            "with 5 + 1 for the Attack item (p.16). Where she stands, Dodger's melee minion gives her +1 " +
            "(p.14): 5 + 1 = 6, and equal totals defend. Moved, she leaves it behind, and ends next to the " +
            "Blue heavy minion as well (−1): 4 against 6. Moving the minion away does nothing, as Dodger is " +
            "not in Swift's line. Wasp at 11 acts before Swift at 8, and both before Dodger at 3 (p.7). The " +
            "lesson: a move that keeps one distance changes all the others, guards included.",
        rules = listOf(
            "p.7 Check the initiative",
            "p.9 Range",
            "p.10 Repeat",
            "p.14 Minion defense modifiers",
            "p.16 Items",
            "p.19 Straight line",
        ),
    )

    override val playerSeats = setOf("wasp", "swift")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        hero("wasp", Team.BLUE, Hex(3, -7)) {
            level = 2
            played("wasp-blue-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-red-2b")
            resolved(3, "wasp-silver")
            hand("wasp-green-1")
            items("wasp-red-2a")
        }
        hero("swift", Team.BLUE, Hex(5, -7)) {
            level = 2
            played("swift-red-1")
            resolved(1, "swift-gold")
            resolved(2, "swift-silver")
            resolved(3, "swift-green-2b")
            hand("swift-blue-1")
            items("swift-green-2a")
        }
        hero("dodger", Team.RED, Hex(3, -9)) {
            level = 2
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-blue-2b")
            hand("dodger-red-1")
            items("dodger-blue-2a")
        }
        // Already acted this turn: Onslaught, 11 + 1 for his Initiative item, before Wasp's 11.
        hero("brogan", Team.RED, Hex(0, -2)) {
            level = 2
            resolved(1, "brogan-silver")
            resolved(2, "brogan-red-1")
            resolved(3, "brogan-green-1")
            resolved(4, "brogan-gold")
            hand("brogan-blue-2b")
            items("brogan-blue-2a")
        }
        minion(Team.RED, MinionType.MELEE, Hex(3, -8))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        phase = PositionPhase.Resolving(next = "wasp")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("dodger"),
        space(2, -8),
        yes(),
        space(1, -7),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(5)

    override val wrongTries = listOf(
        WrongTry(
            "Lift Swift into line with Dodger: her melee minion still guards her, 6 against 6",
            listOf(action(ActionType.PRIMARY), hero("swift")),
        ),
        WrongTry(
            "Lift the melee minion away from Dodger: she is still not in Swift's line",
            listOf(action(ActionType.PRIMARY), unitAt(3, -8)),
        ),
        WrongTry(
            "One step only: Dodger is not in Swift's line yet",
            listOf(action(ActionType.PRIMARY), hero("dodger"), space(2, -8), decline()),
        ),
    )
}
