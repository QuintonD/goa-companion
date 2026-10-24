package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C45 "The Lesser Evil". */
object P45TheLesserEvil : Puzzle() {
    override val concept = "C45"

    override val info = PuzzleInfo(
        id = "p45",
        number = 45,
        title = "The Lesser Evil",
        difficulty = 3,
        theme = "The opponent's choice as a fork",
        heroes = listOf("whisper", "tigerclaw", "wasp", "xargatha"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 5, turn 3. Whisper has played Lesser Evil (11) and acts next; Tigerclaw has played Hit " +
            "and Run (9). Red has one Life counter and one minion left, and one Wave counter remains. Xargatha " +
            "(Charm, 5) holds only Petrifying Stare (Defense 6); Wasp (Stop Projectiles, 3) holds Shock and " +
            "Lift Up.",
        nudges = listOf(
            "Red is one Life counter and one minion away from losing.",
            "Make a Red hero choose between those two.",
            "Lesser Evil on Xargatha.",
        ),
        explanation = "With Lesser Evil the enemy hero picks the bullet (p.10 Making Choices), so give her two " +
            "losing ones. Xargatha holds a single card: if she discards it, Tigerclaw's Hit and Run (9) hits her " +
            "with no card to defend (p.14) and Red spends its last Life counter (p.3). If she picks the minion, " +
            "Whisper defeats Red's last minion beside him: the lane is pushed at once (p.8 Triggering a Push) " +
            "and the last Wave counter ends the game (p.3 Last Push). Wasp would just discard one of her two " +
            "cards, and against Petrifying Stare's 6, Tigerclaw's 3 alone never lands.",
        rules = listOf(
            "p.3 Life counters, Last Push",
            "p.8 Triggering a Push",
            "p.10 Making Choices",
            "p.14 Defense",
        ),
    )

    override val playerSeats = setOf("whisper", "tigerclaw")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 3
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 1)
        lifeCounters(Team.BLUE, 2)
        wavesLeft("lane-1", 1)
        hero("whisper", Team.BLUE, Hex(2, -2)) {
            level = 2
            played("whisper-blue-2b")
            resolved(1, "whisper-gold")
            resolved(2, "whisper-red-1")
            hand("whisper-silver", "whisper-green-1")
            items("whisper-blue-2a")
        }
        hero("tigerclaw", Team.BLUE, Hex(-1, -2)) {
            played("tigerclaw-red-1")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            hand("tigerclaw-green-1", "tigerclaw-blue-1")
        }
        hero("wasp", Team.RED, Hex(4, -4)) {
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            hand("wasp-red-1", "wasp-blue-1")
        }
        hero("xargatha", Team.RED, Hex(0, -2)) {
            level = 2
            played("xargatha-green-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-red-1")
            discard("xargatha-silver")
            hand("xargatha-blue-2a")
            items("xargatha-blue-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(3, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "whisper")
    }

    override val solution = listOf(action(ActionType.PRIMARY), hero("xargatha"), action(ActionType.PRIMARY))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Lesser Evil on Wasp: she discards one of her two cards",
            listOf(action(ActionType.PRIMARY), hero("wasp")),
        ),
        WrongTry("Whisper moves instead", listOf(action(ActionType.MOVEMENT))),
    )
}
