package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C44 "A Fistful of Coins". */
object P44AFistfulOfCoins : Puzzle() {
    override val concept = "C44"

    override val info = PuzzleInfo(
        id = "p44",
        number = 44,
        title = "A Fistful of Coins",
        difficulty = 3,
        theme = "The opponent's own choice",
        heroes = listOf("cutter", "swift", "wasp", "dodger"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 5, turn 4. Both teams have two Life counters. Cutter (A Fistful of Coins, 12) acts " +
            "next with 10 coins, then Swift (Snipe, 8). Wasp (level 4) and Dodger (level 1) have played their " +
            "last cards and have none in hand.",
        nudges = listOf(
            "Give the enemy a choice where both answers lose.",
            "Count what each Red hero is worth in Life counters.",
            "A Fistful of Coins on Wasp; if she lets you place her, put her four spaces from Swift " +
                "in a straight line.",
        ),
        explanation = "When a card makes an enemy hero choose, that hero picks (p.10 Making Choices), so offer " +
            "only losing answers. If Wasp lets Cutter take 3 coins, Cutter has 13 and wins alone (card text). If " +
            "she lets Cutter place her, Cutter puts her on the space just south-east of herself, exactly four " +
            "spaces south-west of Swift in a straight line, Snipe's maximum range (p.18 Place, p.19 Straight " +
            "Line); with no cards she cannot defend the Snipe (p.13), and her lowest card Tier is II, so Red " +
            "spends its last two Life counters (p.13). Dodger is the trap: she also chooses to be placed and " +
            "falls to the Snipe, but her Tier I cards cost Red only one counter. The other Snipe space in " +
            "Cutter's radius, four spaces due west of Swift, is taken by Blue's own minion.",
        rules = listOf(
            "p.10 Making Choices",
            "p.13 When a Hero is Defeated",
            "p.18 Place",
            "p.19 Straight Line",
        ),
    )

    override val playerSeats = setOf("cutter", "swift")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 2)
        hero("cutter", Team.BLUE, Hex(1, -2)) {
            level = 7
            coins = 10
            played("cutter-blue-3b")
            resolved(1, "cutter-gold")
            resolved(2, "cutter-red-3b")
            resolved(3, "cutter-green-3a")
            hand("cutter-silver")
            items(
                "cutter-red-2b",
                "cutter-green-2b",
                "cutter-green-3b",
                "cutter-blue-2b",
                "cutter-red-3a",
                "cutter-blue-3a",
            )
        }
        hero("swift", Team.BLUE, Hex(5, -5)) {
            played("swift-red-1")
            resolved(1, "swift-gold")
            resolved(2, "swift-silver")
            resolved(3, "swift-blue-1")
            hand("swift-green-1")
        }
        hero("wasp", Team.RED, Hex(0, -2)) {
            level = 4
            played("wasp-green-2a")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-2a")
            discard("wasp-blue-2b")
            hand()
            items("wasp-red-2b", "wasp-green-2b", "wasp-blue-2a")
        }
        hero("dodger", Team.RED, Hex(3, -3)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-1")
            discard("dodger-blue-1")
            hand()
        }
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -5))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "cutter")
    }

    // Wasp chooses to be placed (the coins lose at once); Snipe's only target is not asked.
    override val solution = listOf(action(ActionType.PRIMARY), hero("wasp"), space(1, -1), action(ActionType.PRIMARY))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Fistful on Dodger: she dies to the Snipe, but a level 1 hero costs one Life counter",
            listOf(action(ActionType.PRIMARY), hero("dodger")),
        ),
        WrongTry(
            "Place Wasp off Swift's lines: Snipe needs exactly four spaces in a straight line",
            listOf(action(ActionType.PRIMARY), hero("wasp"), space(2, -5)),
        ),
    )
}
