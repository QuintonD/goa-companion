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
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 2, mined from self-play (g6470_r3_BLUE_push; ADR-052, ENCODING.md "Full rounds"). */
object R02RoundG6470R3BluePush : Puzzle() {
    override val concept = "R02"

    override val known = KnownPlays(
        cards = mapOf(
            "tali" to listOf("tali-green-1", "tali-silver", "tali-red-2b", "tali-gold"),
            "bain" to listOf("bain-red-1", "bain-blue-1", "bain-green-2b", "bain-gold"),
        ),
        actions = mapOf(
            "tali" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -3))),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Decline("Skip")),
                3 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Unit("m1002")),
                4 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -4))),
            ),
            "bain" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(0, -1))),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(1, -1))),
                3 to listOf(Choice.Action(ActionType.HOLD)),
                4 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Decline("Skip")),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r02",
        number = 2,
        title = "Round g6470_r3_BLUE_push",
        difficulty = 2,
        theme = "Full round",
        heroes = listOf("takahide", "swift", "tali", "bain"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_ROUND, "Push the lane this round."),
        intro = "Placeholder.",
        nudges = R02_NUDGES.first(),
        nudgesByTurn = R02_NUDGES,
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
                "tali" to mapOf(
                    1 to "Placeholder plan.",
                    2 to "Placeholder plan.",
                    3 to "Placeholder plan.",
                    4 to "Placeholder plan.",
                ),
                "bain" to mapOf(
                    1 to "Placeholder plan.",
                    2 to "Placeholder plan.",
                    3 to "Placeholder plan.",
                    4 to "Placeholder plan.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("takahide", "swift")

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
        card("takahide-green-1"),
        card("swift-red-1"),
        action(ActionType.HOLD),
        action(ActionType.MOVEMENT),
        space(2, -5),
        card("takahide-silver"),
        card("swift-gold"),
        action(ActionType.MOVEMENT),
        space(3, -9),
        action(ActionType.PRIMARY),
        card("takahide-gold"),
        card("takahide-gold"),
        card("swift-blue-1"),
        action(ActionType.MOVEMENT),
        space(1, -7),
        action(ActionType.PRIMARY),
        card("takahide-gold-extra-1"),
        space(0, 0),
        card("takahide-red-2a"),
        card("swift-green-2a"),
        card("takahide-blue-2b"),
        action(ActionType.MOVEMENT),
        space(0, 1),
        action(ActionType.MOVEMENT),
        space(3, -9),
    )

    override val wrongTries = listOf(
        WrongTry(
            "Strength 7's line",
            listOf(
                card("takahide-red-2a"),
                card("swift-green-2a"),
                action(ActionType.MOVEMENT),
                space(4, -8),
                action(ActionType.MOVEMENT),
                space(2, -8),
                card("takahide-green-1"),
                card("swift-blue-1"),
                action(ActionType.PRIMARY),
                space(5, -8),
                action(ActionType.MOVEMENT),
                space(2, -8),
                card("takahide-blue-2b"),
                card("swift-gold"),
                action(ActionType.PRIMARY),
                decline(),
                action(ActionType.PRIMARY),
                space(5, -7),
                card("takahide-silver"),
                card("swift-red-1"),
                action(ActionType.HOLD),
                action(ActionType.HOLD),
            ),
        ),
    )
}

private val R02_NUDGES = listOf(
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
