package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.puzzles.KnownPlays
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 11, mined from self-play (g8553_r5_RED_coins-8; ADR-052, ENCODING.md "Full rounds"). */
object R37EightCoinsFromWhisper : Puzzle() {
    override val concept = "R37"

    override val known = KnownPlays(
        cards = mapOf(
            "nebkher" to listOf("nebkher-silver", "nebkher-green-2a", "nebkher-blue-2b", "nebkher-red-1"),
            "whisper" to listOf("whisper-blue-2b", "whisper-gold", "whisper-silver", "whisper-red-2b"),
        ),
        actions = mapOf(
            "nebkher" to mapOf(
                1 to listOf(Choice.Action(ActionType.HOLD)),
                2 to listOf(Choice.Action(ActionType.HOLD)),
                3 to
                    listOf(
                        Choice.Action(ActionType.PRIMARY),
                        Choice.Option(0, "The line through (-2,1), your space and (0,1)"),
                    ),
            ),
            "whisper" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY)),
                2 to
                    listOf(
                        Choice.Action(ActionType.PRIMARY),
                        Choice.Option(1, "Target a unit adjacent to you"),
                        Choice.Unit("m1002"),
                    ),
                3 to listOf(Choice.Action(ActionType.PRIMARY)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r37",
        number = 11,
        title = "Eight Coins",
        difficulty = 1,
        theme = "Coins from a defeat and its assist",
        heroes = listOf("trinkets", "widget", "nebkher", "whisper"),
        goal = PuzzleGoal.GainCoins(8, Deadline.END_OF_ROUND, "Gain 8 coins by the end of the round."),
        intro = "Round 5. You play Trinkets and Widget for Red. Melee and ranged minions pay 2 coins here, and " +
            "Whisper pays 4 to the " +
            "hero who defeats her and 2 to the other. Steam Discharge targets a unit in range next to the Turret," +
            " and Whisper is already close to Widget.",
        nudges = R37_NUDGES.first(),
        nudgesByTurn = R37_NUDGES,
        explanation = "This is one way through, and other plans win too: Steam Discharge is proven not to be needed." +
            "\n\n" +
            "Turn 1: Lesser Evil asks Trinkets to choose, and he doesn't discard. Trinkets and Widget both hold." +
            "\n\n" +
            "Turn 2: Whisper defeats a Red melee minion. Rapid Redeployment moves Trinkets 3 spaces and places " +
            "the Turret next to him, and Flaming Breath's Movement takes Widget 2 spaces." +
            "\n\n" +
            "Turn 3: Steam Discharge attacks a Blue melee minion next to the Turret at 6 and defeats it: 2 coins." +
            " Fight as One's Movement takes Widget 1 space." +
            "\n\n" +
            "Turn 4: Diversionary Assault attacks Whisper at 7. Shadow Walk gives 3, +2 for items, -1 for the " +
            "adjacent enemy heavy minion, -1 for the enemy ranged minion in radius 2, makes 3, not enough, and " +
            "she is defeated (p.13): 4 coins for Widget and 2 for Trinkets' assist, 8 in all.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense, Minion Defense Modifiers",
            "p.9 Range and Radius",
        ),
        known = known.display(
            mapOf(
                "nebkher" to mapOf(
                    1 to "Holds.",
                    2 to "Holds.",
                    3 to "Performs its primary action: the line runs along his own row, clear of the fight.",
                ),
                "whisper" to mapOf(
                    1 to "Performs its primary action: an enemy hero in range chooses between discarding a card " +
                        "and letting it defeat a minion.",
                    2 to "Attacks the Red melee minion outlined.",
                    3 to "Performs its primary action.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("trinkets", "widget")

    override fun PositionBuilder.setUp() {
        round = 5
        tieBreaker = Team.BLUE
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        piece(
            PieceState(
                "p23", "trinkets_turret", "Turret",
                Hex(
                    3,
                    -5,
                ),
                "trinkets", null, false, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t24", "pyro", "Pyro",
                Hex(
                    9,
                    -7,
                ),
                "widget", Team.RED, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        hero("trinkets", Team.RED, Hex(5, -7)) {
            level = 4
            coins = 1
            hand("trinkets-gold", "trinkets-silver", "trinkets-red-2b", "trinkets-green-2b", "trinkets-blue-2b")
            items("trinkets-red-2a", "trinkets-green-2a", "trinkets-blue-2a")
            deck("trinkets-red-1", "trinkets-green-1", "trinkets-blue-1")
        }
        hero("widget", Team.RED, Hex(8, -5)) {
            level = 5
            coins = 1
            hand("widget-gold", "widget-silver", "widget-green-2a", "widget-blue-2a", "widget-red-3a")
            items("widget-red-2b", "widget-green-2b", "widget-blue-2b", "widget-red-3b")
            deck("widget-red-1", "widget-green-1", "widget-blue-1")
        }
        hero("nebkher", Team.BLUE, Hex(-1, 1)) {
            level = 3
            coins = 1
            hand("nebkher-gold", "nebkher-silver", "nebkher-red-1", "nebkher-green-2a", "nebkher-blue-2b")
            items("nebkher-green-2b", "nebkher-blue-2a")
            deck("nebkher-green-1", "nebkher-blue-1")
        }
        hero("whisper", Team.BLUE, Hex(7, -8)) {
            level = 4
            coins = 2
            hand("whisper-gold", "whisper-silver", "whisper-red-2b", "whisper-green-2a", "whisper-blue-2b")
            items("whisper-red-2a", "whisper-green-2b", "whisper-blue-2a")
            deck("whisper-red-1", "whisper-green-1", "whisper-blue-1")
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
        minion(Team.BLUE, MinionType.MELEE, Hex(7, -5))
    }

    override val solution = listOf(
        card("trinkets-silver"),
        card("widget-silver"),
        option(1),
        action(ActionType.HOLD),
        action(ActionType.HOLD),
        card("trinkets-gold"),
        card("widget-green-2a"),
        action(ActionType.PRIMARY),
        space(6, -10),
        space(7, -11),
        action(ActionType.MOVEMENT),
        space(8, -7),
        card("trinkets-red-2b"),
        card("widget-gold"),
        action(ActionType.MOVEMENT),
        space(7, -7),
        action(ActionType.PRIMARY),
        yes(),
        card("trinkets-green-2b"),
        card("widget-red-3a"),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("trinkets-green-2b"),
                card("widget-gold"),
                action(ActionType.PRIMARY),
                option(0),
                card("trinkets-gold"),
                action(ActionType.MOVEMENT),
                space(6, -9),
                card("trinkets-silver"),
                card("widget-silver"),
                action(ActionType.PRIMARY),
                option(1),
                option(1),
                space(10, -9),
                space(10, -7),
                action(ActionType.PRIMARY),
                option(2),
                card("trinkets-gold"),
                card("trinkets-gold"),
                card("widget-red-3a"),
                action(ActionType.PRIMARY),
                decline(),
                space(6, -10),
                action(ActionType.PRIMARY),
                space(10, -11),
                card("trinkets-red-2b"),
                card("widget-green-2a"),
                action(ActionType.HOLD),
                action(ActionType.PRIMARY),
            ),
        ),
    )
}

private val R37_NUDGES = listOf(
    listOf(
        "Eight coins is Whisper, her assist and one minion. Nothing needs to attack yet.",
        "Lesser Evil asks you to choose. On this line you don't discard.",
        "Play Salvage Parts on Trinkets and Dragon Bond on Widget, both holding. When Lesser Evil asks, " +
            "don't discard.",
    ),
    listOf(
        "Steam Discharge needs the Turret next to its target. Where is the Blue melee minion?",
        "Rapid Redeployment moves Trinkets up to 3 spaces and places the Turret next to him.",
        "Play Rapid Redeployment on Trinkets, placing the Turret next to him, and Flaming Breath on " +
            "Widget, moving 2 spaces.",
    ),
    listOf(
        "Trinkets collects now, and Widget closes on Whisper.",
        "Steam Discharge attacks at 6. Fight as One's Movement is 1 space.",
        "Play Steam Discharge on Trinkets, attacking the Blue melee minion, and Fight as One on Widget, " +
            "moving 1 space.",
    ),
    listOf(
        "Widget is next to Whisper. Who acts first, and what can her last card do?",
        "Diversionary Assault attacks a unit next to Widget at 7.",
        "Play Diversionary Assault on Widget, attacking Whisper, and Self-Destruct on Trinkets.",
    ),
)
