package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C30 "Three Obstacles". */
object P30ThreeObstacles : Puzzle() {
    override val concept = "C30"

    override val info = PuzzleInfo(
        id = "p30",
        number = 30,
        title = "Three Obstacles",
        difficulty = 2,
        theme = "Obstacles, a teammate sets it up",
        heroes = listOf("tigerclaw", "brynn", "dodger", "wasp"),
        goal = PuzzleGoal.DefeatHero("dodger", Deadline.END_OF_TURN, "Defeat Dodger this turn."),
        intro = "Round 3, turn 4. Tigerclaw has played Blink Strike (13) and acts next, then Brynn with " +
            "Familiar Ground (12). Dodger has no cards left in hand. Wasp has already acted.",
        nudges = listOf(
            "Brynn can shoot from afar only at a hero hemmed in by three or more obstacles.",
            "Count Dodger's obstacles: the water counts, and so would your own hero.",
            "Blink Strike north-east over the Red minion, landing next to Dodger.",
        ),
        explanation = "Familiar Ground's second bullet targets a hero in range who is adjacent to 3 or more " +
            "obstacles; heroes, minions, tokens and terrain all count, and every hex that is not a space, " +
            "the water around the island included, is terrain (card text; p.18 Obstacle, p.19 Terrain). " +
            "Dodger touches the water and one Red minion: two. Blink Strike must move exactly 2 spaces in a " +
            "straight line through an enemy unit (Tigerclaw FAQ); north-east it lands next to Dodger, and " +
            "Tigerclaw himself becomes her third obstacle. He resolves at 13 before Brynn's 12 (p.7). Shooting " +
            "the minion next to Brynn instead would take one of Dodger's obstacles away.",
        rules = listOf("p.7 Initiative", "p.11 Movement in the card text", "p.18 Obstacle", "p.19 Terrain"),
    )

    override val playerSeats = setOf("tigerclaw", "brynn")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        hero("tigerclaw", Team.BLUE, Hex(2, -6)) {
            played("tigerclaw-gold")
            resolved(1, "tigerclaw-silver")
            resolved(2, "tigerclaw-red-1")
            resolved(3, "tigerclaw-green-1")
            hand("tigerclaw-blue-1")
        }
        hero("brynn", Team.BLUE, Hex(2, -8)) {
            played("brynn-gold")
            resolved(1, "brynn-silver")
            resolved(2, "brynn-red-1")
            resolved(3, "brynn-green-1")
            hand("brynn-blue-1")
        }
        hero("dodger", Team.RED, Hex(4, -9)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-1")
            resolved(3, "dodger-silver")
            discard("dodger-blue-1")
            hand()
        }
        hero("wasp", Team.RED, Hex(0, 0)) {
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            resolved(4, "wasp-green-1")
            hand("wasp-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(3, -9))
        minion(Team.RED, MinionType.MELEE, Hex(3, -7))
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(5, -8))
        phase = PositionPhase.Resolving(next = "tigerclaw")
    }

    override val solution = listOf(action(ActionType.PRIMARY), space(4, -8), action(ActionType.PRIMARY), option(1))

    override val wrongTries = listOf(
        WrongTry(
            "Blink west through the other minion: Dodger keeps only two obstacles",
            listOf(action(ActionType.PRIMARY), space(0, -6)),
        ),
        WrongTry(
            "Brynn shoots the minion next to her: one obstacle fewer",
            listOf(action(ActionType.PRIMARY), space(4, -8), action(ActionType.PRIMARY), option(0)),
        ),
    )
}
