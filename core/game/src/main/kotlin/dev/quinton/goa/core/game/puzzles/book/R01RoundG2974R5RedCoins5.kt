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

/** Full round 1, mined from self-play (g2974_r5_RED_coins-5; ADR-052, ENCODING.md "Full rounds"). */
object R01RoundG2974R5RedCoins5 : Puzzle() {
    override val concept = "R01"

    override val known = KnownPlays(
        cards = mapOf(
            "garrus" to listOf("garrus-blue-2b", "garrus-green-2b", "garrus-gold", "garrus-red-2b"),
            "dodger" to listOf("dodger-green-1", "dodger-silver", "dodger-gold", "dodger-red-2a"),
        ),
        actions = mapOf(
            "garrus" to mapOf(
                1 to listOf(Choice.Action(ActionType.HOLD)),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(5, -3))),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(6, -4))),
                4 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(5, -3))),
            ),
            "dodger" to mapOf(
                1 to listOf(Choice.Space(Hex(11, -6)), Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(9, -6))),
                2 to listOf(Choice.Action(ActionType.HOLD)),
                3 to listOf(Choice.Action(ActionType.HOLD)),
                4 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(6, -4))),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r01",
        number = 1,
        title = "Round g2974_r5_RED_coins-5",
        difficulty = 2,
        theme = "Full round",
        heroes = listOf("emmitt", "whisper", "garrus", "dodger"),
        goal = PuzzleGoal.GainCoins(5, Deadline.END_OF_ROUND, "Gain 5 coins by the end of the round."),
        intro = "Placeholder.",
        nudges = R01_NUDGES.first(),
        nudgesByTurn = R01_NUDGES,
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
                "garrus" to mapOf(
                    1 to "Placeholder plan.",
                    2 to "Placeholder plan.",
                    3 to "Placeholder plan.",
                    4 to "Placeholder plan.",
                ),
                "dodger" to mapOf(
                    1 to "Placeholder plan.",
                    2 to "Placeholder plan.",
                    3 to "Placeholder plan.",
                    4 to "Placeholder plan.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("emmitt", "whisper")

    override fun PositionBuilder.setUp() {
        round = 5
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 2)
        hero("emmitt", Team.RED, Hex(6, -10)) {
            level = 5
            hand("emmitt-gold", "emmitt-silver", "emmitt-red-2a", "emmitt-blue-2a", "emmitt-green-3a")
            items("emmitt-red-2b", "emmitt-green-2a", "emmitt-blue-2b", "emmitt-green-3b")
            deck("emmitt-red-1", "emmitt-green-1", "emmitt-blue-1")
        }
        hero("whisper", Team.RED, Hex(6, -5)) {
            level = 4
            coins = 2
            hand("whisper-gold", "whisper-silver", "whisper-red-2b", "whisper-green-2a", "whisper-blue-2b")
            items("whisper-red-2a", "whisper-green-2b", "whisper-blue-2a")
            deck("whisper-red-1", "whisper-green-1", "whisper-blue-1")
        }
        hero("garrus", Team.BLUE, Hex(4, -3)) {
            level = 4
            coins = 1
            hand("garrus-gold", "garrus-silver", "garrus-red-2b", "garrus-green-2b", "garrus-blue-2b")
            items("garrus-red-2a", "garrus-green-2a", "garrus-blue-2a")
            deck("garrus-red-1", "garrus-green-1", "garrus-blue-1")
        }
        hero("dodger", Team.BLUE, null) {
            level = 2
            coins = 2
            hand("dodger-gold", "dodger-silver", "dodger-green-1", "dodger-blue-1", "dodger-red-2a")
            items("dodger-red-2b")
            deck("dodger-red-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -11))
        minion(Team.RED, MinionType.MELEE, Hex(7, -10))
        minion(Team.RED, MinionType.MELEE, Hex(8, -9))
        minion(Team.BLUE, MinionType.RANGED, Hex(9, -9))
        minion(Team.RED, MinionType.HEAVY, Hex(8, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(9, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(11, -8))
        minion(Team.RED, MinionType.RANGED, Hex(6, -7))
        minion(Team.RED, MinionType.MELEE, Hex(7, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -6))
    }

    override val solution = listOf(
        card("emmitt-gold"),
        card("whisper-green-2a"),
        action(ActionType.HOLD),
        action(ActionType.HOLD),
        card("emmitt-green-3a"),
        card("whisper-blue-2b"),
        action(ActionType.MOVEMENT),
        space(7, -7),
        action(ActionType.PRIMARY),
        card("whisper-gold"),
        card("emmitt-silver"),
        card("whisper-silver"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        space(9, -12),
        space(5, -11),
        hero("garrus"),
        card("emmitt-blue-2a"),
        card("whisper-red-2b"),
        hero("emmitt"),
        action(ActionType.MOVEMENT),
        space(8, -12),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "Strength 7's line",
            listOf(
                card("emmitt-green-3a"),
                card("whisper-silver"),
                action(ActionType.HOLD),
                action(ActionType.MOVEMENT),
                space(7, -11),
                card("emmitt-gold"),
                card("whisper-blue-2b"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(6, -6),
                card("emmitt-silver"),
                card("whisper-gold"),
                action(ActionType.PRIMARY),
                space(8, -11),
                space(7, -8),
                hero("dodger"),
                action(ActionType.PRIMARY),
                hero("garrus"),
                card("emmitt-blue-2a"),
                card("whisper-red-2b"),
                hero("whisper"),
                action(ActionType.MOVEMENT),
                space(8, -7),
                action(ActionType.MOVEMENT),
                decline(),
            ),
        ),
    )
}

private val R01_NUDGES = listOf(
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
