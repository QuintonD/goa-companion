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
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C03 "Melee First, Then the Heavy". */
object P03MeleeFirstThenTheHeavy : Puzzle() {
    override val concept = "C03"

    override val info = PuzzleInfo(
        id = "p03",
        number = 3,
        title = "Melee First, Then the Heavy",
        difficulty = 1,
        theme = "Heavy minion immunity, the last push",
        heroes = listOf("snorri", "garrus", "xargatha", "tigerclaw"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_TURN, "Push the lane this turn."),
        intro = "Round 4, turn 4. Snorri has played Runic Battleaxe and acts next; his axe rune lies below " +
            "turn slot 4. Xargatha has no cards left in hand; Tigerclaw has already acted. Red's only " +
            "minions are a melee and a heavy minion, and the lane has one Wave counter left.",
        nudges = listOf(
            "One Wave counter left: what ends the game right now?",
            "The heavy minion can't be touched while another Red minion still stands in the Battle Zone.",
            "Hit the melee minion first, then repeat on the heavy one.",
        ),
        explanation = "As long as a friendly non-heavy minion is in the Battle Zone, the heavy minion is " +
            "immune and cannot be targeted (p.13). Defeating the melee minion lifts that, and the axe rune's " +
            "\"may repeat once on an enemy minion\" (a new instance of the attack, p.10) can then take the " +
            "heavy one. When the last enemy minion in the Battle Zone is defeated, the lane is pushed at once " +
            "(p.8); when the last Wave counter goes, the team that won that push wins the game (p.3 Last " +
            "Push). Xargatha with no cards looks like the prize, but she costs Red one Life counter of three, " +
            "and the repeat may then only hit the melee minion: the heavy one survives.",
        rules = listOf(
            "p.3 Last Push",
            "p.8 Push the Lane, Triggering a Push",
            "p.10 Repeat",
            "p.13 Heavy Minion Immunity",
        ),
    )

    override val playerSeats = setOf("snorri")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 2)
        wavesLeft("lane-1", 1)
        hero("snorri", Team.BLUE, Hex(1, -1)) {
            level = 5
            played("snorri-red-3a")
            resolved(1, "snorri-silver")
            resolved(2, "snorri-gold")
            resolved(3, "snorri-green-2a")
            hand("snorri-blue-2a")
            items("snorri-red-2b", "snorri-red-3b", "snorri-green-2b", "snorri-blue-2b")
            marker("marker_rune_anvil", "snorri", persistent = true, data = mapOf("slot" to "1"))
            marker("marker_rune_bird", "snorri", persistent = true, data = mapOf("slot" to "2"))
            marker("marker_rune_horn", "snorri", persistent = true, data = mapOf("slot" to "3"))
            marker("marker_rune_axe", "snorri", persistent = true, data = mapOf("slot" to "4"))
        }
        hero("garrus", Team.BLUE, Hex(3, -6)) {
            resolved(1, "garrus-gold")
            resolved(2, "garrus-silver")
            resolved(3, "garrus-red-1")
            resolved(4, "garrus-green-1")
            hand("garrus-blue-1")
        }
        hero("xargatha", Team.RED, Hex(2, -2)) {
            played("xargatha-green-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-red-1")
            resolved(3, "xargatha-blue-1")
            discard("xargatha-silver")
            hand()
        }
        hero("tigerclaw", Team.RED, Hex(-1, -4)) {
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-red-1")
            resolved(4, "tigerclaw-blue-1")
            hand("tigerclaw-green-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "snorri")
    }

    override val solution = listOf(action(ActionType.PRIMARY), unitAt(0, -1), yes())

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Defeat Xargatha first: one Life counter of three, and the heavy minion stays immune",
            listOf(action(ActionType.PRIMARY), hero("xargatha")),
        ),
    )
}
