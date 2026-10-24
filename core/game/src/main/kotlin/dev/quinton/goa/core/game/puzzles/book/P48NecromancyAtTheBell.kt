package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C48 "Necromancy at the Bell". */
object P48NecromancyAtTheBell : Puzzle() {
    override val concept = "C48"

    override val info = PuzzleInfo(
        id = "p48",
        number = 48,
        title = "Necromancy at the Bell",
        difficulty = 3,
        theme = "End of round order, minion types",
        heroes = listOf("dodger", "xargatha", "tigerclaw", "arien"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_ROUND, "Don't let the lane be pushed this round."),
        intro = "Round 3, turn 4. Dodger has played Necromancy and is the last to act. Xargatha resolved " +
            "Constrict this turn. This wave Red has lost three melee minions, its ranged minion and its " +
            "heavy minion; one melee minion is left.",
        nudges = listOf(
            "Count Red's minions after Xargatha's End of round effect, then play out the Minion Battle.",
            "Constrict only defeats melee minions.",
            "Use Necromancy and bring back the ranged minion.",
        ),
        explanation = "Necromancy brings back a friendly minion removed this wave onto an empty friendly " +
            "spawn point next to Dodger, whatever its type (Dodger FAQ; p.19 Respawn); here that is the " +
            "spawn point beside Xargatha. End of round effects come before the Minion Battle (Constrict's " +
            "reminder; p.17 End of Round): a melee minion there would be defeated, leaving Red one minion " +
            "against three. The team with fewer minions removes the difference, and losing the last one " +
            "pushes the lane (p.8). A ranged or heavy minion is safe from Constrict: Red has two against " +
            "three, removes one (the heavy last) and keeps a minion in the Battle Zone. Moving changes no count.",
        rules = listOf(
            "p.8 Minion Battle, Push the Lane",
            "p.17 End of Round",
            "p.19 Respawn",
            "Dodger FAQ: Necromancy",
        ),
    )

    override val playerSeats = setOf("dodger")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("dodger", Team.RED, Hex(0, -5)) {
            level = 2
            played("dodger-green-2b")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-1")
            hand("dodger-blue-1")
            items("dodger-green-2a")
        }
        hero("xargatha", Team.BLUE, Hex(2, -6)) {
            level = 2
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-red-1")
            resolved(4, "xargatha-green-2b")
            hand("xargatha-blue-1")
            items("xargatha-green-2a")
        }
        hero("tigerclaw", Team.RED, Hex(4, -2)) {
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-red-1")
            resolved(4, "tigerclaw-green-1")
            hand("tigerclaw-blue-1")
        }
        hero("arien", Team.BLUE, Hex(-1, -2)) {
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-red-1")
            resolved(4, "arien-green-1")
            hand("arien-blue-1")
        }
        // Constrict, resolved this turn: "End of round: Defeat an enemy melee minion adjacent to you."
        effect("xargatha", "xargatha-green-2b", Timing.END_OF_ROUND, ActionKind.SKILL)
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "dodger")
    }

    // Necromancy, the ranged minion (on (1,-6)); in the Minion Battle Red removes one of its two.
    override val solution = listOf(action(ActionType.PRIMARY), option(1), unitAt(0, -6))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Respawn a melee minion: Constrict defeats it before the Minion Battle",
            listOf(action(ActionType.PRIMARY), option(0)),
        ),
        WrongTry("Move instead: Red still has one minion against three", listOf(action(ActionType.MOVEMENT))),
    )
}
