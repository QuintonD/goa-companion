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

/** CONCEPTS.md C43 "Count the Minions". */
object P43CountTheMinions : Puzzle() {
    override val concept = "C43"

    override val info = PuzzleInfo(
        id = "p43",
        number = 43,
        title = "Count the Minions",
        difficulty = 3,
        theme = "Minion Battle arithmetic, End of round effects",
        heroes = listOf("tigerclaw", "xargatha", "arien", "wasp"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_ROUND, "Push the lane by the end of this round."),
        intro = "Round 3, turn 4. Tigerclaw has played Blink Strike (13) and acts next; Xargatha has played " +
            "Constrict (4). Arien has played his last card, Dangerous Current (8), and holds none. Wasp has " +
            "acted. Each team has three minions in the Battle Zone.",
        nudges = listOf(
            "At the end of the round, count minions, not heroes.",
            "Xargatha's Constrict needs its own victim next to her: don't take it from her.",
            "Blink Strike east, through the minion at (2,-5).",
        ),
        explanation = "Arien has no card, and Blink Strike through him would defeat him, but the Minion Battle " +
            "only counts minions (p.8). Blink east through the Blue minion at (2,-5) instead: Blue is down to " +
            "two. Arien can take one Red minion back, but at the end of the round Constrict defeats the Blue " +
            "melee next to Xargatha before the battle (card text; p.17 End of Round). Blue then has only its " +
            "heavy minion against Red's two or three, must remove the difference, heavy last, and loses its last " +
            "minion: the lane is pushed (p.8). Through Arien, Blue keeps a minion after the battle; through the " +
            "minion next to Xargatha, Constrict has nothing left to defeat.",
        rules = listOf("p.8 Minion Battle, Push the Lane", "p.13 Attacking a minion", "p.17 End of Round"),
    )

    override val playerSeats = setOf("tigerclaw", "xargatha")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("tigerclaw", Team.RED, Hex(1, -5)) {
            played("tigerclaw-gold")
            resolved(1, "tigerclaw-red-1")
            resolved(2, "tigerclaw-blue-1")
            resolved(3, "tigerclaw-green-1")
            hand("tigerclaw-silver")
        }
        hero("xargatha", Team.RED, Hex(-1, -4)) {
            level = 2
            played("xargatha-green-2b")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-red-1")
            resolved(3, "xargatha-blue-1")
            hand("xargatha-silver")
            items("xargatha-green-2a")
        }
        hero("arien", Team.BLUE, Hex(1, -6)) {
            played("arien-red-1")
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-green-1")
            discard("arien-blue-1")
            hand()
        }
        hero("wasp", Team.BLUE, Hex(4, -2)) {
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            resolved(4, "wasp-green-1")
            hand("wasp-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -5))
        minion(Team.BLUE, MinionType.MELEE, Hex(0, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "tigerclaw")
    }

    override val solution = listOf(action(ActionType.PRIMARY), space(3, -5), action(ActionType.PRIMARY))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Blink through Arien: he has no card, but Blue keeps a minion after the battle",
            listOf(action(ActionType.PRIMARY), space(1, -7)),
        ),
        WrongTry(
            "Blink through the minion next to Xargatha: Constrict has nothing left to defeat",
            listOf(action(ActionType.PRIMARY), space(-1, -3)),
        ),
        WrongTry("Tigerclaw holds", listOf(action(ActionType.HOLD))),
    )
}
