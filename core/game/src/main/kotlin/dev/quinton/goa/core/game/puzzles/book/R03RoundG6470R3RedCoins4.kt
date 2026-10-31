package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.puzzles.KnownPlays
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 3, mined from self-play (g6470_r3_RED_coins-4; ADR-052, ENCODING.md "Full rounds"). */
object R03RoundG6470R3RedCoins4 : Puzzle() {
    override val concept = "R03"

    override val known = KnownPlays(
        cards = mapOf(
            "takahide" to listOf("takahide-gold-extra-1", "takahide-blue-2b", "takahide-red-2a", "takahide-silver"),
            "swift" to listOf("swift-silver", "swift-gold", "swift-blue-1", "swift-red-1"),
        ),
        actions = mapOf(
            "takahide" to mapOf(
                1 to listOf(Choice.Action(ActionType.HOLD)),
                2 to listOf(Choice.Action(ActionType.HOLD)),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -8))),
            ),
            "swift" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(3, -6))),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(5, -8)), Choice.Yes, Choice.Space(Hex(3, -6))),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(1, -4))),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r03",
        number = 3,
        title = "Round g6470_r3_RED_coins-4",
        difficulty = 3,
        theme = "Full round",
        heroes = listOf("tali", "bain", "takahide", "swift"),
        goal = PuzzleGoal.GainCoins(4, Deadline.END_OF_ROUND, "Gain 4 coins by the end of the round."),
        intro = "Placeholder.",
        nudges = R03_NUDGES.first(),
        nudgesByTurn = R03_NUDGES,
        explanation = "Turn 1: placeholder." +
            "\n\n" +
            "Turn 2: placeholder." +
            "\n\n" +
            "Turn 3: placeholder." +
            "\n\n" +
            "Turn 4: placeholder.",
        rules = listOf(
            "p.8 Minion Battle",
        ),
        known = known.display(
            mapOf(
                "takahide" to mapOf(
                    1 to "Placeholder plan.",
                    2 to "Placeholder plan.",
                    3 to "Placeholder plan.",
                ),
                "swift" to mapOf(
                    1 to "Placeholder plan.",
                    2 to "Placeholder plan.",
                    3 to "Placeholder plan.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("tali", "bain")

    override fun PositionBuilder.setUp() {
        round = 3
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 3)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        hero("tali", Team.RED, Hex(3, -5)) {
            level = 3
            coins = 2
            hand("tali-gold", "tali-silver", "tali-green-1", "tali-red-2b", "tali-blue-2b")
            items("tali-red-2a", "tali-blue-2a")
            deck("tali-red-1", "tali-blue-1")
        }
        hero("bain", Team.RED, Hex(0, -5)) {
            level = 2
            coins = 2
            hand("bain-gold", "bain-silver", "bain-red-1", "bain-blue-1", "bain-green-2b")
            items("bain-green-2a")
            deck("bain-green-1")
        }
        hero("takahide", Team.BLUE, Hex(3, -7)) {
            level = 3
            coins = 1
            hand("takahide-gold-extra-1", "takahide-silver", "takahide-green-1", "takahide-red-2a", "takahide-blue-2b")
            items("takahide-red-2b", "takahide-blue-2a")
            deck("takahide-gold", "takahide-red-1", "takahide-blue-1")
        }
        hero("swift", Team.BLUE, Hex(3, -8)) {
            level = 2
            coins = 2
            hand("swift-gold", "swift-silver", "swift-red-1", "swift-blue-1", "swift-green-2a")
            items("swift-green-2b")
            deck("swift-green-1")
        }
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
    }

    override val solution = listOf(
        card("tali-blue-2b"),
        card("bain-red-1"),
        action(ActionType.MOVEMENT),
        space(4, -8),
        action(ActionType.MOVEMENT),
        space(1, -9),
        card("tali-gold"),
        card("bain-gold"),
        hero("tali"),
        action(ActionType.PRIMARY),
        action(ActionType.MOVEMENT),
        space(1, -10),
        card("tali-silver"),
        card("bain-silver"),
        hero("tali"),
        action(ActionType.PRIMARY),
        option(0),
        action(ActionType.HOLD),
        card("tali-red-2b"),
        card("bain-blue-1"),
        hero("tali"),
        action(ActionType.PRIMARY),
        option(0),
    )

    override val wrongTries = listOf(
        WrongTry(
            "Strength 7's line",
            listOf(
                card("tali-green-1"),
                card("bain-red-1"),
                action(ActionType.MOVEMENT),
                space(-1, -1),
                action(ActionType.MOVEMENT),
                space(3, -3),
                card("tali-red-2b"),
                card("bain-silver"),
                action(ActionType.PRIMARY),
                action(ActionType.PRIMARY),
                card("tali-silver"),
                card("bain-green-2b"),
                action(ActionType.HOLD),
                action(ActionType.HOLD),
                card("tali-blue-2b"),
                card("bain-gold"),
                action(ActionType.HOLD),
                action(ActionType.MOVEMENT),
                space(0, -2),
            ),
        ),
    )
}

private val R03_NUDGES = listOf(
            listOf(
                "a",
                "b",
                "c",
            ),
            listOf(
                "a",
                "b",
                "c",
            ),
            listOf(
                "a",
                "b",
                "c",
            ),
            listOf(
                "a",
                "b",
                "c",
            ),
)
