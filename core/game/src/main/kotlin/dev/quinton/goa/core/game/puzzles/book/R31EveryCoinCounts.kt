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

/** Full round 12, mined from self-play (g2677_r2_BLUE_coins-6; ADR-052, ENCODING.md "Full rounds"). */
object R31EveryCoinCounts : Puzzle() {
    override val concept = "R31"

    override val known = KnownPlays(
        cards = mapOf(
            "swift" to listOf("swift-silver", "swift-green-2a", "swift-gold", "swift-blue-1"),
            "misa" to listOf("misa-gold", "misa-red-1", "misa-blue-1", "misa-silver"),
        ),
        actions = mapOf(
            "swift" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(1, -2))),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -4))),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Decline("Don't move")),
                4 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(2, -3))),
            ),
            "misa" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(4, -5))),
                2 to listOf(Choice.Action(ActionType.PRIMARY)),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(4, -4))),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r31",
        number = 12,
        title = "Every Coin Counts",
        difficulty = 1,
        theme = "Coins from four different places on this line",
        heroes = listOf("dodger", "min", "swift", "misa"),
        goal = PuzzleGoal.GainCoins(6, Deadline.END_OF_ROUND, "Gain 6 coins by the end of the round."),
        intro = "Round 2. You play Dodger and Min for Blue. Melee and ranged minions pay 2 coins here, and Swift " +
            "pays 2 to the hero " +
            "who defeats him and 1 to the other. Dark Ritual gains 1 coin if there are 2 or more empty spawn " +
            "points in its radius in the Battle Zone.",
        nudges = R31_NUDGES.first(),
        nudgesByTurn = R31_NUDGES,
        explanation =
            "This is one way through, and other plans win too: neither Dark Ritual nor defeating Swift is needed " +
                "for all of them." +
                "\n\n" +
                "Turn 1: Poof!'s Movement takes Min 3 spaces. Dodger plays Death Trap." +
                "\n\n" +
                "Turn 2: Fast as Lightning attacks Swift at 3, and on this line Snipe defends: 4, -1 for the enemy " +
                "ranged minion in radius 2, makes 3 (p.14). Dread Razor defeats a Red melee minion: 2 coins. Misa " +
                "defeats the Blue ranged minion." +
                "\n\n" +
                "Turn 3: Dark Ritual gains 1 coin: there are 2 or more empty spawn points in radius. Min places the " +
                "Smoke bomb token." +
                "\n\n" +
                "Turn 4: Swift tries to push Min and can't. Crane Stance attacks Swift at 4, and he is defeated " +
                "(p.13): 2 coins for Min and 1 for Dodger's assist, 6 in all.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense, Minion Defense Modifiers",
            "p.9 Range and Radius",
        ),
        known = known.display(
            mapOf(
                "swift" to mapOf(
                    1 to "Performs its primary action, landing on the space outlined.",
                    2 to "Moves to the space outlined.",
                    3 to "Takes its Movement and stays where it is.",
                    4 to "Performs its primary action, landing on the space outlined.",
                ),
                "misa" to mapOf(
                    1 to "Places itself on the space outlined.",
                    2 to "Attacks a unit in its reach.",
                    3 to "Moves to the space outlined.",
                    4 to "Holds.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("dodger", "min")

    override fun PositionBuilder.setUp() {
        round = 2
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 3)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        hero("swift", Team.RED, Hex(1, -4)) {
            level = 2
            coins = 1
            hand("swift-gold", "swift-silver", "swift-red-1", "swift-blue-1", "swift-green-2a")
            items("swift-green-2b")
            deck("swift-green-1")
        }
        hero("misa", Team.RED, Hex(1, -6)) {
            level = 2
            coins = 1
            hand("misa-gold", "misa-silver", "misa-red-1", "misa-blue-1", "misa-green-2a")
            items("misa-green-2b")
            deck("misa-green-1")
        }
        hero("dodger", Team.BLUE, Hex(1, -7)) {
            level = 2
            coins = 1
            hand("dodger-gold", "dodger-silver", "dodger-green-1", "dodger-blue-1", "dodger-red-2a")
            items("dodger-red-2b")
            deck("dodger-red-1")
        }
        hero("min", Team.BLUE, Hex(-1, 1)) {
            level = 2
            coins = 1
            hand("min-gold", "min-silver", "min-red-1", "min-green-1", "min-blue-2b")
            items("min-blue-2a")
            deck("min-blue-1")
        }
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.BLUE, MinionType.RANGED, Hex(3, -4))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
    }

    override val solution = listOf(
        card("dodger-silver"),
        card("min-green-1"),
        action(ActionType.HOLD),
        action(ActionType.MOVEMENT),
        space(2, -2),
        card("dodger-gold"),
        card("min-gold"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        option(0),
        card("dodger-green-1"),
        card("min-silver"),
        action(ActionType.PRIMARY),
        space(0, 0),
        action(ActionType.PRIMARY),
        card("dodger-blue-1"),
        card("min-red-1"),
        action(ActionType.MOVEMENT),
        space(3, -10),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("dodger-silver"),
                card("min-gold"),
                action(ActionType.PRIMARY),
                action(ActionType.HOLD),
                card("dodger-gold"),
                card("min-blue-2b"),
                action(ActionType.PRIMARY),
                option(1),
                action(ActionType.MOVEMENT),
                space(0, 2),
                card("dodger-blue-1"),
                card("min-silver"),
                action(ActionType.HOLD),
                action(ActionType.MOVEMENT),
                space(-1, -5),
                card("dodger-green-1"),
                card("min-green-1"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(2, 0),
            ),
        ),
    )
}

private val R31_NUDGES = listOf(
    listOf(
        "On this line, add it up: a minion, a hero, an assist, and one coin more.",
        "Min has to be near Swift by next turn. Dodger's first card doesn't need to do much.",
        "Play Death Trap on Dodger and Poof! on Min, moving 3 spaces.",
    ),
    listOf(
        "Each of you can attack now, one at Swift and one at a minion.",
        "Fast as Lightning attacks Swift at 3. Dread Razor's first text attacks a unit next to Dodger.",
        "Play Dread Razor on Dodger, attacking the Red melee minion, and Fast as Lightning on Min, " +
            "attacking Swift.",
    ),
    listOf(
        "The extra coin comes now. Count the empty spawn points around Dodger.",
        "Dark Ritual's primary action pays it: holding it instead is proven to lose.",
        "Play Dark Ritual on Dodger and Smoke Bomb on Min, placing the Smoke bomb token.",
    ),
    listOf(
        "Min finishes Swift. Which card is that?",
        "Crane Stance attacks a unit next to Min at 4.",
        "Play Shield of Decay on Dodger, who moves, and Crane Stance on Min, attacking Swift.",
    ),
)
