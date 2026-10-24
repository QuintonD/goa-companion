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

/**
 * CONCEPTS.md C11 "By the Water's Edge" (changed: Dodger stands next to Mrak and next to the rocks at
 * (5,-6), so Seismic Slam has an obvious target and the water's edge is the one that wins).
 */
object P11ByTheWatersEdge : Puzzle() {
    override val concept = "C11"

    override val info = PuzzleInfo(
        id = "p11",
        number = 11,
        title = "By the Water's Edge",
        difficulty = 1,
        theme = "Terrain adjacency, Life counters by tier",
        heroes = listOf("mrak", "tigerclaw", "dodger", "wasp"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 4, turn 4. Mrak has played Seismic Slam and acts next. Dodger (level 1) and Wasp " +
            "(level 4) have no cards left in hand. Red has two Life counters left.",
        nudges = listOf(
            "Red needs to lose two Life counters at once.",
            "Look at what surrounds Wasp on the edge of the map.",
            "Seismic Slam Wasp: the water beside her is terrain.",
        ),
        explanation = "\"Any board space without a full grid outline counts as terrain … including the water " +
            "spaces surrounding the board\" (p.19), so a hero on the edge of the island is next to terrain, just " +
            "like Dodger beside the rocks: every space on the rim touches terrain. Seismic Slam makes the hero " +
            "discard a card or be defeated (card text), so a hero with no cards is simply defeated. Wasp is " +
            "level 4, her lowest card tier is II, so she costs Red both of its Life counters (p.13); Dodger, " +
            "slammed or hit with the secondary Attack, is tier I and costs only one.",
        rules = listOf(
            "p.13 When a hero is defeated",
            "p.19 Terrain",
        ),
    )

    override val playerSeats = setOf("mrak")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 3)
        hero("mrak", Team.BLUE, Hex(3, -6)) {
            played("mrak-red-1")
            resolved(1, "mrak-gold")
            resolved(2, "mrak-silver")
            resolved(3, "mrak-blue-1")
            hand("mrak-green-1")
        }
        hero("tigerclaw", Team.BLUE, Hex(0, -2)) {
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-red-1")
            resolved(4, "tigerclaw-green-1")
            hand("tigerclaw-blue-1")
        }
        hero("dodger", Team.RED, Hex(4, -6)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-1")
            resolved(3, "dodger-silver")
            discard("dodger-blue-1")
            hand()
        }
        hero("wasp", Team.RED, Hex(4, -9)) {
            level = 4
            played("wasp-green-2a")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-2a")
            discard("wasp-blue-2a")
            items("wasp-red-2b", "wasp-blue-2b", "wasp-green-2b")
            hand()
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "mrak")
    }

    override val solution = listOf(action(ActionType.PRIMARY), hero("wasp"))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Seismic Slam on Dodger by the rocks: she costs Red only one counter",
            listOf(action(ActionType.PRIMARY), hero("dodger")),
        ),
        WrongTry(
            "The secondary Attack on the adjacent Dodger: one counter, Red keeps one",
            listOf(action(ActionType.ATTACK)),
        ),
    )
}
