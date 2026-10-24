package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C39 "Static Barrier". */
object P39StaticBarrier : Puzzle() {
    override val concept = "C39"

    override val info = PuzzleInfo(
        id = "p39",
        number = 39,
        title = "Static Barrier",
        difficulty = 2,
        theme = "Active effects, Block",
        heroes = listOf("wasp", "brogan", "swift", "tigerclaw"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn."),
        intro = "Round 3, turn 3. Wasp has played Static Barrier (13) and acts first. Swift has played " +
            "Snipe (8) and Brogan Mad Dash (7); Tigerclaw (Light-Fingered, 2) is far away. Wasp keeps " +
            "Magnetic Dagger (Defense 2) and Stop Projectiles in her hand.",
        nudges = listOf(
            "Two attacks are coming and you have two cards. One of them stops only one kind of attack.",
            "Mad Dash must end next to you. What if every space around you were an obstacle for Brogan?",
            "Perform Static Barrier, then block Swift's Snipe with Stop Projectiles.",
        ),
        explanation = "Magnetic Dagger's Defense 2 stops neither attack (Snipe 5, Mad Dash 6; p.14). Static " +
            "Barrier makes every space within 2 of Wasp an obstacle while an enemy hero outside that radius acts " +
            "(card text, p.18 Obstacle): Brogan, three spaces away, can't end a Mad Dash next to her, and if he " +
            "can't make that move he can't attack (card text). The Barrier does not stop attacks (Wasp FAQ), so " +
            "Swift's Snipe still comes: Stop Projectiles blocks a ranged attack (p.14 Block), while Magnetic " +
            "Dagger would lose 2 against 5. Holding instead leaves one Block for two attacks, and Mad Dash is not " +
            "ranged.",
        rules = listOf(
            "p.11 Movement in the Card Text",
            "p.14 Defense, Block",
            "p.17 Active Effects",
            "p.18 Obstacle",
            "Wasp FAQ: Static Barrier",
        ),
    )

    override val playerSeats = setOf("wasp")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 3
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("brogan", Team.BLUE, Hex(4, -7)) {
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-blue-1")
            hand("brogan-silver", "brogan-green-1")
        }
        hero("swift", Team.BLUE, Hex(5, -5)) {
            played("swift-red-1")
            resolved(1, "swift-blue-1")
            resolved(2, "swift-green-1")
            hand("swift-gold", "swift-silver")
        }
        hero("tigerclaw", Team.RED, Hex(-1, -2)) {
            played("tigerclaw-green-1")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            hand("tigerclaw-red-1", "tigerclaw-blue-1")
        }
        hero("wasp", Team.RED, Hex(1, -5)) {
            played("wasp-silver")
            resolved(1, "wasp-red-1")
            resolved(2, "wasp-blue-1")
            hand("wasp-gold", "wasp-green-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "wasp")
    }

    override val solution = listOf(action(ActionType.PRIMARY), card("wasp-green-1"))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry("Hold: one Block card cannot answer two attacks", listOf(action(ActionType.HOLD))),
        WrongTry(
            "Trust the Barrier to stop the Snipe and defend with Magnetic Dagger: 2 against 5",
            listOf(action(ActionType.PRIMARY), card("wasp-gold")),
        ),
    )
}
