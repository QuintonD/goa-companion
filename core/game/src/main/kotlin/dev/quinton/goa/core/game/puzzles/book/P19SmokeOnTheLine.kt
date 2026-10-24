package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C19 "Smoke on the Line". */
object P19SmokeOnTheLine : Puzzle() {
    override val concept = "C19"

    override val info = PuzzleInfo(
        id = "p19",
        number = 19,
        title = "Smoke on the Line",
        difficulty = 1,
        theme = "Tokens that block straight-line targeting",
        heroes = listOf("min", "brogan", "swift", "wasp"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Don't let the lane be pushed this turn."),
        intro = "Round 5, turn 4. Min has played Smoke Bomb and acts next. Wasp has played Charged " +
            "Boomerang (initiative 10) and Swift Snipe (8); each will try to defeat Blue's last minion in " +
            "the Battle Zone. Blue has one Wave counter left. Brogan has already acted.",
        nudges = listOf(
            "Who can actually shoot your last minion?",
            "The smoke only matters on the line between the shooter and the target.",
            "Put the Smoke bomb at (3,-5), between Swift and your minion.",
        ),
        explanation = "The Smoke bomb stops enemy heroes targeting any unit when the token stands on the " +
            "straight line between them (card text; p.19 Straight line). Snipe needs a target exactly 4 " +
            "spaces away in a straight line, and the only space of that line Min can reach is (3,-5): " +
            "(2,-5) is taken, and a piece is placed only into an empty space (p.18 Place), and (4,-5) is " +
            "beyond the radius of 3 (p.9). Wasp is no threat at all: Charged Boomerang can only target a " +
            "unit not in a straight line from her, and the minion is in one. If Blue's last minion in the " +
            "Battle Zone fell, the lane would be pushed at once and the last Wave counter would end the " +
            "game (p.8).",
        rules = listOf("p.8 Triggering a Push", "p.9 Radius", "p.18 Place", "p.19 Straight line, Tokens"),
    )

    override val playerSeats = setOf("min")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 2)
        wavesLeft("lane-1", 1)
        hero("min", Team.BLUE, Hex(0, -2)) {
            played("min-silver")
            resolved(1, "min-gold")
            resolved(2, "min-red-1")
            resolved(3, "min-blue-1")
            hand("min-green-1")
        }
        hero("brogan", Team.BLUE, Hex(-2, 1)) {
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-red-1")
            resolved(4, "brogan-green-1")
            hand("brogan-blue-1")
        }
        hero("swift", Team.RED, Hex(5, -5)) {
            played("swift-red-1")
            resolved(1, "swift-gold")
            resolved(2, "swift-silver")
            resolved(3, "swift-green-1")
            hand("swift-blue-1")
        }
        hero("wasp", Team.RED, Hex(1, -7)) {
            level = 2
            played("wasp-red-2b")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-green-1")
            hand("wasp-blue-1")
            items("wasp-red-2a")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -5))
        minion(Team.RED, MinionType.MELEE, Hex(2, -5))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "min")
    }

    override val solution = listOf(action(ActionType.PRIMARY), space(3, -5))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Smoke next to the minion, off Swift's line: Snipe still has a clear target",
            listOf(action(ActionType.PRIMARY), space(0, -5)),
        ),
        WrongTry("Hold: Snipe defeats the minion and the lane is pushed", listOf(action(ActionType.HOLD))),
    )
}
