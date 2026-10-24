package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C19 "Smoke on the Line" (changed: round 4, turn 3, everyone level 3, one minion a side;
 * Wasp can shoot Min, and Min must not answer with Poof!, which would carry the Smoke bomb off the line).
 */
object P19SmokeOnTheLine : Puzzle() {
    override val concept = "C19"

    override val info = PuzzleInfo(
        id = "p19",
        number = 19,
        title = "Smoke on the Line",
        difficulty = 1,
        theme = "Tokens that block straight-line targeting",
        heroes = listOf("min", "brogan", "swift", "wasp"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Don't let the lane be pushed, and keep Min alive, this turn."),
        intro = "Round 4, turn 3. Min has played Smoke Bomb and acts next; he keeps Poof! and Death Grenade " +
            "in hand. Wasp has played Charged Boomerang (initiative 10), Swift Snipe (8). The lane has one Wave " +
            "counter left, and each team has one minion in the Battle Zone. Brogan has already acted.",
        nudges = listOf(
            "Who can actually shoot your last minion, and who can shoot you?",
            "The smoke only matters on the line between the shooter and the target, and only while it stays " +
                "where you put it.",
            "Put the Smoke bomb between Swift and your minion, in the empty space two from Swift; if Wasp " +
                "shoots Min, defend with Death Grenade.",
        ),
        explanation = "The Smoke bomb stops enemy heroes targeting any unit when the token stands on the " +
            "straight line between them (card text; p.19 Straight line). Snipe needs a target exactly 4 " +
            "spaces away in a straight line, and of the three spaces between Swift and the minion Min can " +
            "use only the middle one: Brogan stands on one, a piece is placed only into an empty space (p.18 " +
            "Place), and the space next to Swift is beyond the radius of 3 (p.9). Wasp, who acts first, " +
            "cannot be stopped by smoke at all: Charged Boomerang only targets units not in a straight line " +
            "from her, so she shoots Min (Attack 3, +1 for her Attack item, p.16). Poof! blocks any attack, " +
            "but it swaps Min with the Smoke bomb, and the token lands on Min's old space, off Swift's line. " +
            "Death Grenade defends with 4, and equal totals defend (p.14): the smoke stays where it is. If " +
            "Blue's last minion fell, the lane would be pushed at once and the last Wave counter would end " +
            "the game (p.8). A defense can move your own pieces: check what it undoes.",
        rules = listOf(
            "p.8 Triggering a Push",
            "p.9 Radius",
            "p.14 Defense, Block",
            "p.16 Items",
            "p.18 Place",
            "p.19 Straight line, Tokens",
        ),
    )

    override val playerSeats = setOf("min")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 3
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        wavesLeft("lane-1", 1)
        hero("min", Team.BLUE, Hex(0, -2)) {
            level = 3
            played("min-silver")
            resolved(1, "min-gold")
            resolved(2, "min-red-2b")
            hand("min-green-1", "min-blue-2b")
            items("min-red-2a", "min-blue-2a")
        }
        // Already acted this turn: Onslaught, 11 + 2 for his Initiative items, tied with Min's 13.
        hero("brogan", Team.BLUE, Hex(2, -5)) {
            level = 3
            resolved(1, "brogan-silver")
            resolved(2, "brogan-red-2b")
            resolved(3, "brogan-gold")
            hand("brogan-green-1", "brogan-blue-2b")
            items("brogan-red-2a", "brogan-blue-2a")
        }
        hero("swift", Team.RED, Hex(5, -5)) {
            level = 3
            played("swift-red-1")
            resolved(1, "swift-gold")
            resolved(2, "swift-silver")
            hand("swift-green-2b", "swift-blue-2a")
            items("swift-green-2a", "swift-blue-2b")
        }
        hero("wasp", Team.RED, Hex(1, -1)) {
            level = 3
            played("wasp-red-2b")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            hand("wasp-green-1", "wasp-blue-2a")
            items("wasp-red-2a", "wasp-blue-2b")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -5))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "min")
    }

    override val solution = listOf(action(ActionType.PRIMARY), space(3, -5), card("min-blue-2b"))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Smoke behind the minion, not between it and Swift: Snipe still has a clear target",
            listOf(action(ActionType.PRIMARY), space(0, -5)),
        ),
        WrongTry(
            "Block Wasp with Poof!: Min swaps with the Smoke bomb and takes it off Swift's line",
            listOf(action(ActionType.PRIMARY), space(3, -5), card("min-green-1")),
        ),
        WrongTry("Hold: Snipe defeats the minion and the lane is pushed", listOf(action(ActionType.HOLD))),
    )
}
