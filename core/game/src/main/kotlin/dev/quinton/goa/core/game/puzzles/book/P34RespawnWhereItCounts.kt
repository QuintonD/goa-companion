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
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C34 "Respawn Where It Counts", turned to Blue's side of the map (every space rotated
 * half a turn, (q, r) to (3 - q, -7 - r)); Xargatha stands in for the idle Tigerclaw. Changed in the
 * hardening pass: Bullrush (2 or 3 spaces), and Red's last minion is its heavy.
 */
object P34RespawnWhereItCounts : Puzzle() {
    override val concept = "C34"

    override val info = PuzzleInfo(
        id = "p34",
        number = 34,
        title = "Respawn Where It Counts",
        difficulty = 1,
        theme = "Hero respawn, exact moves, pushing the lane",
        heroes = listOf("brogan", "wasp", "arien", "xargatha"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_TURN, "Push the lane this turn."),
        intro = "Round 3, turn 4. Brogan was defeated earlier this round; he has played Bullrush and may respawn " +
            "before he acts. Red has one minion left in the Battle Zone, its heavy minion. Everyone else has " +
            "acted; Arien keeps Expert Duelist and Xargatha Petrifying Stare.",
        nudges = listOf(
            "You choose where Brogan comes back, and Bullrush can run two spaces or three.",
            "From each spawn point, follow every straight line two and three spaces out. Only one ends beside " +
                "the heavy minion.",
            "Respawn on the southern spawn point, then Bullrush three spaces north-west.",
        ),
        explanation = "A hero off the board may respawn on any empty Spawn point of its Throne Zone before acting " +
            "(p.7). Bullrush's move is mandatory: 2 or 3 spaces in a straight line to a space next to an enemy " +
            "unit, which is then the target (card text; p.11 Movement in the card text); if it can't be made, " +
            "there is no attack. From the northern and the middle spawn point every such line ends next to " +
            "Xargatha, who defends; from the southern one, three spaces north-west end beside the heavy minion, " +
            "while two stop between Arien and Xargatha. With no Red melee minion left the heavy minion is not " +
            "immune (p.13), " +
            "and defeating the last enemy minion in the Battle Zone pushes the lane at once (p.8): a Wave counter " +
            "flips and the Battle Zone moves toward Red. Attacking Arien instead gains nothing: Expert Duelist " +
            "holds 6 against 6. The lesson: where you respawn is part of your move, so pick the spawn point for " +
            "the card you have played, not the closest one.",
        rules = listOf(
            "p.7 Hero Respawn",
            "p.8 Push the Lane, Triggering a Push",
            "p.11 Movement in the card text",
            "p.13 Heavy Minion Immunity",
        ),
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
        hero("arien", Team.RED, Hex(7, -4)) {
            level = 2
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-green-1")
            resolved(4, "arien-red-1")
            hand("arien-blue-2a")
            items("arien-blue-2b")
        }
        hero("xargatha", Team.RED, Hex(7, -3)) {
            level = 2
            resolved(1, "xargatha-red-1")
            resolved(2, "xargatha-green-1")
            resolved(3, "xargatha-silver")
            resolved(4, "xargatha-gold")
            hand("xargatha-blue-2a")
            items("xargatha-blue-2b")
        }
        // Defeated earlier this round: off the board, with this turn's card played.
        hero("brogan", Team.BLUE, null) {
            level = 2
            played("brogan-red-2a")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-blue-1")
            resolved(3, "brogan-green-1")
            hand("brogan-silver")
            items("brogan-red-2b")
        }
        hero("wasp", Team.BLUE, Hex(9, -6)) {
            level = 2
            resolved(1, "wasp-green-1")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-gold")
            resolved(4, "wasp-red-2a")
            hand("wasp-blue-1")
            items("wasp-red-2b")
        }
        heroDefeatedThisRound = true
        minion(Team.RED, MinionType.HEAVY, Hex(7, -5))
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(11, -8))
        minion(Team.BLUE, MinionType.RANGED, Hex(9, -9))
        minion(Team.BLUE, MinionType.HEAVY, Hex(9, -8))
        phase = PositionPhase.Resolving(next = "brogan")
    }

    override val solution = listOf(space(8, -2), action(ActionType.PRIMARY), space(8, -5), unitAt(7, -5))

    override val nudgedMoves = solution.take(3)

    override val wrongTries = listOf(
        WrongTry("Respawn on the closest spawn point: its only line ends next to Xargatha", listOf(space(9, -4))),
        WrongTry("Respawn on the northern spawn point: its only line ends next to Xargatha", listOf(space(11, -6))),
        WrongTry("Stay off the board: the card resolves without an action", listOf(decline())),
        WrongTry(
            "Southern spawn point, but only two spaces: next to the heroes, not the minion",
            listOf(space(8, -2), action(ActionType.PRIMARY), space(8, -4)),
        ),
        WrongTry(
            "Three spaces, then attack Arien: Expert Duelist holds",
            listOf(space(8, -2), action(ActionType.PRIMARY), space(8, -5), hero("arien")),
        ),
    )
}
