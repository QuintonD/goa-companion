package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C34 "Respawn Where It Counts", turned to Blue's side of the map (every space rotated
 * half a turn, (q, r) to (3 - q, -7 - r)); Xargatha stands in for the idle Tigerclaw.
 */
object P34RespawnWhereItCounts : Puzzle() {
    override val concept = "C34"

    override val info = PuzzleInfo(
        id = "p34",
        number = 34,
        title = "Respawn Where It Counts",
        difficulty = 2,
        theme = "Hero respawn, exact moves, pushing the lane",
        heroes = listOf("brogan", "wasp", "arien", "xargatha"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_TURN, "Push the lane this turn."),
        intro = "Round 3, turn 4. Brogan was defeated earlier this round; he has played Mad Dash and may " +
            "respawn before he acts. Red has one minion left in the Battle Zone. Everyone else has acted.",
        nudges = listOf(
            "You choose where Brogan comes back.",
            "Mad Dash needs exactly two spaces in a straight line: the closest spawn point isn't the best one.",
            "Respawn on the far spawn point, at the south end of the Throne Zone.",
        ),
        explanation = "A hero off the board may respawn on any empty Spawn point of its Throne Zone before " +
            "acting (p.7). Mad Dash's move is mandatory and exactly two spaces in a straight line to a space " +
            "next to an enemy unit (p.11 Movement in the card text; Brogan FAQ): if it can't be made, there " +
            "is no attack. From the nearest spawn point every two-space line ends too far from Red's minion " +
            "or runs into it; only the far one has a line that ends beside it. Defeating the last enemy " +
            "minion in the Battle Zone pushes the lane at once (p.8): a Wave counter flips and the Battle " +
            "Zone moves toward Red.",
        rules = listOf("p.7 Hero Respawn", "p.8 Push the Lane, Triggering a Push", "p.11 Movement in the card text"),
    )

    override val playerSeats = setOf("brogan")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 2)
        wavesLeft("lane-1", 2)
        battleZone("lane-1", "lane-1-3")
        hero("arien", Team.RED, Hex(5, -4)) {
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-red-1")
            resolved(4, "arien-green-1")
            hand("arien-blue-1")
        }
        hero("xargatha", Team.RED, Hex(6, -7)) {
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-red-1")
            resolved(4, "xargatha-green-1")
            hand("xargatha-blue-1")
        }
        // Defeated earlier this round: off the board, with this turn's card played.
        hero("brogan", Team.BLUE, null) {
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-blue-1")
            resolved(3, "brogan-green-1")
            hand("brogan-silver")
        }
        hero("wasp", Team.BLUE, Hex(9, -6)) {
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            resolved(4, "wasp-green-1")
            hand("wasp-blue-1")
        }
        heroDefeatedThisRound = true
        minion(Team.RED, MinionType.MELEE, Hex(7, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(11, -8))
        minion(Team.BLUE, MinionType.RANGED, Hex(9, -9))
        phase = PositionPhase.Resolving(next = "brogan")
    }

    override val solution = listOf(space(8, -2), action(ActionType.PRIMARY))

    override val wrongTries = listOf(
        WrongTry("Respawn on the closest spawn point: no two-space line ends next to the minion", listOf(space(9, -4))),
        WrongTry("Respawn on the northern spawn point: its lines end too far away", listOf(space(11, -6))),
        WrongTry("Stay off the board: the card resolves without an action", listOf(decline())),
    )
}
