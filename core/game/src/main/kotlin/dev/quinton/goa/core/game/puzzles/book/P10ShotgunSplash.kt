package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C10 "Shotgun Splash". */
object P10ShotgunSplash : Puzzle() {
    override val concept = "C10"

    override val info = PuzzleInfo(
        id = "p10",
        number = 10,
        title = "Shotgun Splash",
        difficulty = 1,
        theme = "Before the attack",
        heroes = listOf("swift", "arien", "wasp", "brogan"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 5, turn 4. Swift has played Super-Shotgun and acts next. Wasp (level 4) and " +
            "Brogan (level 3) have no cards left in hand. Red has two Life counters left.",
        nudges = listOf(
            "Which Red hero is worth both Life counters?",
            "Your shot hurts someone standing next to its target before it lands.",
            "Shoot the melee minion beside Wasp.",
        ),
        explanation = "A defeated hero costs its team Life counters equal to its lowest card tier (p.13): " +
            "Wasp at level 4 has only Tier II cards and is worth 2, Brogan at level 3 still has a Tier I " +
            "card and is worth 1. Wasp is three spaces away, beyond Super-Shotgun's Range 2, but the Red " +
            "minion next to her is in range. \"Before the attack\" text is applied first, before the target " +
            "is hit (p.14 order, p.17), so Wasp, adjacent to the target and with no card to discard, is " +
            "defeated and Red loses its last two counters. Shooting Brogan instead takes only one counter; " +
            "here it does not even get that far, because nobody stands next to him for the blast.",
        rules = listOf(
            "p.9 Range",
            "p.13 When a hero is defeated",
            "p.14 Attack and defense, detailed order",
            "p.17 Before the attack",
        ),
    )

    override val playerSeats = setOf("swift")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 2)
        hero("swift", Team.BLUE, Hex(2, -2)) {
            level = 5
            played("swift-red-3b")
            resolved(1, "swift-gold")
            resolved(2, "swift-silver")
            resolved(3, "swift-green-2b")
            hand("swift-blue-2a")
            items("swift-red-2a", "swift-red-3a", "swift-green-2a", "swift-blue-2b")
        }
        hero("arien", Team.BLUE, Hex(0, -5)) {
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-red-1")
            resolved(4, "arien-green-1")
            hand("arien-blue-1")
        }
        hero("wasp", Team.RED, Hex(-1, -2)) {
            level = 4
            played("wasp-green-2a")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-red-2a")
            resolved(3, "wasp-blue-2a")
            discard("wasp-silver")
            hand()
            items("wasp-red-2b", "wasp-green-2b", "wasp-blue-2b")
        }
        hero("brogan", Team.RED, Hex(3, -3)) {
            level = 3
            played("brogan-green-2a")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-red-2a")
            resolved(3, "brogan-blue-1")
            discard("brogan-silver")
            hand()
            items("brogan-red-2b", "brogan-green-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -2))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "swift")
    }

    override val solution = listOf(action(ActionType.PRIMARY), unitAt(0, -2))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Shoot Brogan: nobody stands next to him, and he is worth one counter",
            listOf(action(ActionType.PRIMARY), hero("brogan")),
        ),
        WrongTry("Move towards Wasp: no attack follows", listOf(action(ActionType.MOVEMENT))),
    )
}
