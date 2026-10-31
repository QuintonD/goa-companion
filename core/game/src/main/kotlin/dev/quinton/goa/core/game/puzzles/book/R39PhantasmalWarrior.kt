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
import dev.quinton.goa.core.game.puzzles.Moves.pass
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 7, mined from self-play (g3931_r6_BLUE_coins-6; ADR-052, ENCODING.md "Full rounds"). */
object R39PhantasmalWarrior : Puzzle() {
    override val concept = "R39"

    override val known = KnownPlays(
        cards = mapOf(
            "garrus" to listOf("garrus-green-2b", "garrus-gold", "garrus-blue-2a", "garrus-red-2b"),
            "mrak" to listOf("mrak-red-2b", "mrak-green-2a", "mrak-gold", "mrak-blue-2b"),
        ),
        actions = mapOf(
            "garrus" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(-4, 1))),
                2 to listOf(Choice.Action(ActionType.PRIMARY)),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(-2, 2))),
                4 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(2, -2))),
            ),
            "mrak" to mapOf(
                1 to listOf(Choice.Action(ActionType.HOLD)),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(0, -4))),
                3 to listOf(Choice.Action(ActionType.PRIMARY)),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r39",
        number = 7,
        title = "Phantasmal Warrior",
        difficulty = 1,
        theme = "A token placed for the last turn, a hero with no cards left",
        heroes = listOf("silverarrow", "nebkher", "garrus", "mrak"),
        goal = PuzzleGoal.GainCoins(6, Deadline.END_OF_ROUND, "Gain 6 coins by the end of the round."),
        intro = "Round 6. You play Silverarrow and NebKher for Blue. Melee and ranged minions pay 2 coins here, and " +
            "Mrak pays 4 to the" +
            " hero who defeats him and 2 to the other. Phantasmal Warrior can move an Illusion token in range " +
            "next to an enemy hero in range, and attack that hero.",
        nudges = R39_NUDGES.first(),
        nudgesByTurn = R39_NUDGES,
        explanation =
            "This is one way through, and other plans win too: neither defeating Garrus nor defeating Mrak is " +
                "needed for all of them." +
                "\n\n" +
                "Turn 1: Illusionary Force's and Shoot and Scoot's Movement bring NebKher and Silverarrow up. " +
                "Garrus's Light Pilum makes Silverarrow discard Trailblazer." +
                "\n\n" +
                "Turn 2: Garrus attacks Silverarrow at 5, and Warning Shot defends: 3, +2 for items, makes 5 (p.14). " +
                "Mind Grip performs Mrak's Stone Carapace's Movement of 4; here NebKher moves 1 space. Divert " +
                "Attention's Movement takes" +
                " Silverarrow 2 spaces." +
                "\n\n" +
                "Turn 3: Mrak defeats a Blue melee minion. Long Shot attacks Mrak at 4, and Stone Grip holds: 4, +1 " +
                "for the item, makes 5. Diabolical Laughter places an Illusion next to NebKher, and he swaps with it " +
                "twice." +
                "\n\n" +
                "Turn 4: Silverarrow has no cards and passes (p.7). Phantasmal Warrior moves the Illusion next to " +
                "Mrak and attacks him at 4, and he is defeated (p.13): 4 coins for NebKher and 2 for Silverarrow's " +
                "assist.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense",
            "p.7 Play one card",
            "p.19 Tokens",
        ),
        known = known.display(
            mapOf(
                "garrus" to mapOf(
                    1 to "Makes an enemy hero in range discard a card, and moves to the space outlined.",
                    2 to "Attacks a unit in its reach.",
                    3 to "Moves to the space outlined.",
                    4 to "Moves to the space outlined.",
                ),
                "mrak" to mapOf(
                    1 to "Holds.",
                    2 to "Moves to the space outlined.",
                    3 to "Attacks a unit in its reach.",
                    4 to "Holds.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("silverarrow", "nebkher")

    override fun PositionBuilder.setUp() {
        round = 6
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 1)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 3)
        hero("garrus", Team.RED, Hex(-4, 0)) {
            level = 4
            coins = 3
            hand("garrus-gold", "garrus-silver", "garrus-red-2b", "garrus-green-2b", "garrus-blue-2a")
            items("garrus-red-2a", "garrus-green-2a", "garrus-blue-2b")
            deck("garrus-red-1", "garrus-green-1", "garrus-blue-1")
        }
        hero("mrak", Team.RED, Hex(-1, -2)) {
            level = 4
            coins = 4
            hand("mrak-gold", "mrak-silver", "mrak-red-2b", "mrak-green-2a", "mrak-blue-2b")
            items("mrak-red-2a", "mrak-green-2b", "mrak-blue-2a")
            deck("mrak-red-1", "mrak-green-1", "mrak-blue-1")
        }
        hero("silverarrow", Team.BLUE, Hex(-3, 1)) {
            level = 4
            coins = 2
            hand(
                "silverarrow-gold",
                "silverarrow-silver",
                "silverarrow-red-2b",
                "silverarrow-green-2a",
                "silverarrow-blue-2b",
            )
            items("silverarrow-red-2a", "silverarrow-green-2b", "silverarrow-blue-2a")
            deck("silverarrow-red-1", "silverarrow-green-1", "silverarrow-blue-1")
        }
        hero("nebkher", Team.BLUE, Hex(-3, -3)) {
            level = 4
            hand("nebkher-gold", "nebkher-silver", "nebkher-red-2a", "nebkher-green-2b", "nebkher-blue-2b")
            items("nebkher-red-2b", "nebkher-green-2a", "nebkher-blue-2a")
            deck("nebkher-red-1", "nebkher-green-1", "nebkher-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.RANGED, Hex(3, -4))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
    }

    override val solution = listOf(
        card("silverarrow-gold"),
        card("nebkher-green-2b"),
        action(ActionType.MOVEMENT),
        space(-3, 0),
        action(ActionType.MOVEMENT),
        space(-1, -5),
        card("silverarrow-silver"),
        card("silverarrow-green-2a"),
        card("nebkher-gold"),
        card("silverarrow-blue-2b"),
        action(ActionType.PRIMARY),
        option(0),
        action(ActionType.PRIMARY),
        space(-2, -4),
        action(ActionType.MOVEMENT),
        space(-3, -2),
        card("silverarrow-red-2b"),
        card("nebkher-silver"),
        action(ActionType.PRIMARY),
        hero("mrak"),
        action(ActionType.PRIMARY),
        option(1),
        space(-1, -5),
        option(0),
        option(0),
        pass(),
        card("nebkher-red-2a"),
        action(ActionType.PRIMARY),
        space(-1, -4),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("silverarrow-blue-2b"),
                card("nebkher-green-2b"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(-1, -5),
                card("silverarrow-silver"),
                hero("mrak"),
                card("silverarrow-gold"),
                card("nebkher-gold"),
                card("silverarrow-red-2b"),
                action(ActionType.MOVEMENT),
                space(-2, 0),
                action(ActionType.PRIMARY),
                option(1),
                card("silverarrow-green-2a"),
                card("nebkher-red-2a"),
                action(ActionType.MOVEMENT),
                space(2, -8),
                action(ActionType.HOLD),
                pass(),
                card("nebkher-silver"),
                action(ActionType.PRIMARY),
                option(1),
                space(3, -8),
                option(0),
                option(2),
                option(4),
                option(3),
            ),
        ),
    )
}

private val R39_NUDGES = listOf(
    listOf(
        "On this line six coins is Mrak and the assist. What can Garrus cost Silverarrow on the way?",
        "Both of you take your cards' Movement. Garrus's card will make Silverarrow discard.",
        "Play Shoot and Scoot on Silverarrow and Illusionary Force on NebKher, each moving. Discard " +
            "Trailblazer when asked.",
    ),
    listOf(
        "Garrus can attack Silverarrow this turn. NebKher's gold card can use an enemy's card.",
        "Mind Grip performs an action on the card in an enemy hero's previous turn slot: Mrak's card " +
            "gives NebKher Movement 4.",
        "Play Divert Attention on Silverarrow, moving 2 spaces, and Mind Grip on NebKher, moving 1 " +
            "space. Defend with Warning Shot if Garrus attacks.",
    ),
    listOf(
        "Silverarrow's last card is here. NebKher needs an Illusion on the board for next turn.",
        "Diabolical Laughter can place one. Holding it instead is proven to lose.",
        "Play Long Shot on Silverarrow, attacking Mrak, and Diabolical Laughter on NebKher, placing an " +
            "Illusion.",
    ),
    listOf(
        "Silverarrow has no cards and passes. What can NebKher still do?",
        "Phantasmal Warrior moves the Illusion next to Mrak and attacks him at 4. Crack in Reality is " +
            "proven to lose.",
        "Play Phantasmal Warrior on NebKher, attacking Mrak. Silverarrow passes.",
    ),
)
