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

/** CONCEPTS.md C30 "Three Obstacles" (changed: Dodger is hemmed in already; her spawn-point bonus is the target). */
object P30ThreeObstacles : Puzzle() {
    override val concept = "C30"

    override val info = PuzzleInfo(
        id = "p30",
        number = 30,
        title = "Three Obstacles",
        difficulty = 2,
        theme = "Obstacles, spawn points, a teammate sets it up",
        heroes = listOf("tigerclaw", "brynn", "dodger", "wasp"),
        goal = PuzzleGoal.DefeatHero("dodger", Deadline.END_OF_TURN, "Defeat Dodger this turn."),
        intro = "Round 3, turn 4. Tigerclaw has played Blink Strike (13) and acts next, then Brynn with Familiar " +
            "Ground (12); Brynn has two Attack items. Dodger has played Dark Ritual and keeps Shield of Decay. Wasp " +
            "has yet to act.",
        nudges = listOf(
            "Count Dodger's obstacles first, then her Defense against Brynn's Attack 4.",
            "Shield of Decay adds 2 while two or more spawn points within 2 of Dodger are empty, and a hero " +
                "standing on a spawn point fills it.",
            "Blink Strike west, through the minion and onto the spawn point.",
        ),
        explanation = "Familiar Ground's second bullet targets a hero in range who is adjacent to 3 or more " +
            "obstacles; heroes, minions, tokens and terrain all count (card text; p.18 Obstacle, p.19 Terrain). " +
            "Dodger already has three: the two terrain hexes beside her and the Blue minion. Her Defense is what " +
            "saves her: Shield of Decay is 3, +2 because two spawn points within 2 of her are empty (card text), " +
            "-1 for the adjacent Blue melee minion (p.14), so 4 against Brynn's 2 + 2 (items) = 4, and an equal " +
            "total defends. Blink Strike moves Tigerclaw exactly 2 spaces in a straight line through an enemy " +
            "unit, which he then attacks (Tigerclaw FAQ). West, through the minion, he lands on one of those " +
            "spawn points: only one is left empty, the bonus is gone, and Dodger defends at 2. North-west lands " +
            "him next to her, a fourth obstacle that changes nothing; north-east changes nothing near her. He " +
            "resolves at 13, before Brynn's 12 (p.7). The lesson: once a hero is hemmed in, count what else " +
            "her Defense adds, and stand where it hurts.",
        rules = listOf(
            "p.7 Initiative",
            "p.11 Movement in the card text",
            "p.14 Minion defense modifiers",
            "p.18 Obstacle",
            "p.19 Terrain",
        ),
    )

    override val playerSeats = setOf("tigerclaw", "brynn")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("tigerclaw", Team.BLUE, Hex(3, -6)) {
            level = 2
            played("tigerclaw-gold")
            resolved(1, "tigerclaw-silver")
            resolved(2, "tigerclaw-red-1")
            resolved(3, "tigerclaw-green-1")
            hand("tigerclaw-blue-2a")
            items("tigerclaw-blue-2b")
        }
        hero("brynn", Team.BLUE, Hex(5, -9)) {
            level = 3
            played("brynn-gold")
            resolved(1, "brynn-silver")
            resolved(2, "brynn-red-1")
            resolved(3, "brynn-green-2a")
            hand("brynn-blue-2a")
            items("brynn-green-2b", "brynn-blue-2b")
        }
        hero("dodger", Team.RED, Hex(2, -8)) {
            level = 2
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-2b")
            resolved(3, "dodger-silver")
            hand("dodger-blue-1")
            items("dodger-red-2a")
        }
        hero("wasp", Team.RED, Hex(-2, 1)) {
            level = 2
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-2a")
            hand("wasp-blue-1")
            items("wasp-red-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(2, -6))
        minion(Team.RED, MinionType.MELEE, Hex(3, -7))
        minion(Team.RED, MinionType.MELEE, Hex(4, -7))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -7))
        minion(Team.BLUE, MinionType.HEAVY, Hex(3, -1))
        phase = PositionPhase.Resolving(next = "tigerclaw")
    }

    override val solution = listOf(action(ActionType.PRIMARY), space(1, -6), action(ActionType.PRIMARY))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Blink north-west, next to Dodger: a fourth obstacle, but she still defends 4 against 4",
            listOf(action(ActionType.PRIMARY), space(3, -8)),
        ),
        WrongTry(
            "Blink north-east: nothing near Dodger changes",
            listOf(action(ActionType.PRIMARY), space(5, -8)),
        ),
        WrongTry("Tigerclaw moves instead of striking", listOf(action(ActionType.MOVEMENT))),
    )
}
