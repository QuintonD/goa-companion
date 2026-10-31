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
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 3, mined from self-play (g9152_r5_BLUE_push; ADR-052, ENCODING.md "Full rounds"). */
object R28DiscardToReach : Puzzle() {
    override val concept = "R28"

    override val known = KnownPlays(
        cards = mapOf(
            "arien" to listOf("arien-gold", "arien-green-2a", "arien-blue-2b", "arien-red-3a"),
            "hanu" to listOf("hanu-gold", "hanu-red-2b", "hanu-silver", "hanu-green-2a"),
        ),
        actions = mapOf(
            "arien" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY)),
                2 to listOf(Choice.Action(ActionType.HOLD)),
                3 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(10, -9))),
                4 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Decline("Skip"), Choice.Yes),
            ),
            "hanu" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(8, -10))),
                2 to listOf(Choice.Action(ActionType.PRIMARY)),
                3 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Unit("brogan")),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r28",
        number = 3,
        title = "Discard to Reach",
        difficulty = 1,
        theme = "Minion Battle arithmetic, a discard that buys range",
        heroes = listOf("garrus", "brogan", "arien", "hanu"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_ROUND, "Push the lane by the end of the round."),
        intro = "Round 5. You play Garrus and Brogan for Blue. In the Battle Zone Blue has five minions and Red has " +
            "three, and you know that Arien and Hanu are going to attack. Throwing Axe targets a unit next to " +
            "Brogan, or, if he discards a card, a unit in range.",
        nudges = R28_NUDGES.first(),
        nudgesByTurn = R28_NUDGES,
        explanation =
            "Turn 1: Hanu's Fight and Flight attacks Brogan at 3, and Onslaught defends: 3, +1 for the item, " +
                "makes 4 (p.14). Arien defeats a Blue melee minion. Garrus and Brogan take their cards' Movement." +
                "\n\n" +
                "Turn 2: Bulwark brings Onslaught back. Angry Strike's Movement takes Garrus 1 space. Hanu defeats a " +
                "Blue melee minion." +
                "\n\n" +
                "Turn 3: Onslaught defeats a Red melee minion at 4. Chilling Howl lets Garrus discard a resolved " +
                "card, Light Pilum here." +
                "\n\n" +
                "Turn 4: Arien defeats the Blue ranged minion and may repeat. Throwing Axe, with Bolster discarded, " +
                "reaches the last Red melee minion in range and defeats it at 3. In the Minion Battle Red has 1 and " +
                "Blue 2: Red removes its heavy, its last minion, and Blue pushes the lane (p.8).",
        rules = listOf(
            "p.8 Minion Battle, Push the Lane",
            "p.14 Defense",
            "p.10 Discard a Card",
            "p.9 Range and Radius",
        ),
        known = known.display(
            mapOf(
                "arien" to mapOf(
                    1 to "Attacks a unit in its reach.",
                    2 to "Holds.",
                    3 to "Performs its primary action on the space outlined.",
                    4 to "Attacks a unit in its reach, and repeats it.",
                ),
                "hanu" to mapOf(
                    1 to "Attacks a unit next to it and, if the target stands, moves to the space outlined.",
                    2 to "Attacks a unit in its reach.",
                    3 to "Sets the Initiative of Brogan's unresolved card to 11.",
                    4 to "Holds.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("garrus", "brogan")

    override fun PositionBuilder.setUp() {
        round = 5
        tieBreaker = Team.BLUE
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 4)
        gameVar("hanu.hurry.arien-red-2a", "440")
        hero("arien", Team.RED, Hex(10, -8)) {
            level = 5
            coins = 1
            hand("arien-gold", "arien-silver", "arien-green-2a", "arien-blue-2b", "arien-red-3a")
            items("arien-red-2b", "arien-green-2b", "arien-blue-2a", "arien-red-3b")
            deck("arien-red-1", "arien-green-1", "arien-blue-1")
        }
        hero("hanu", Team.RED, Hex(5, -7)) {
            level = 4
            coins = 1
            hand("hanu-gold", "hanu-silver", "hanu-red-2b", "hanu-green-2a", "hanu-blue-2a")
            items("hanu-red-2a", "hanu-green-2b", "hanu-blue-2b")
            deck("hanu-red-1", "hanu-green-1", "hanu-blue-1")
        }
        hero("garrus", Team.BLUE, Hex(3, -5)) {
            level = 4
            coins = 2
            hand("garrus-gold", "garrus-silver", "garrus-red-2b", "garrus-green-2b", "garrus-blue-2b")
            items("garrus-red-2a", "garrus-green-2a", "garrus-blue-2a")
            deck("garrus-red-1", "garrus-green-1", "garrus-blue-1")
        }
        hero("brogan", Team.BLUE, Hex(6, -7)) {
            level = 4
            coins = 4
            hand("brogan-gold", "brogan-silver", "brogan-red-2b", "brogan-green-2a", "brogan-blue-2b")
            items("brogan-red-2a", "brogan-green-2b", "brogan-blue-2a")
            deck("brogan-red-1", "brogan-green-1", "brogan-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -11))
        minion(Team.RED, MinionType.MELEE, Hex(7, -10))
        minion(Team.RED, MinionType.MELEE, Hex(8, -9))
        minion(Team.BLUE, MinionType.RANGED, Hex(9, -9))
        minion(Team.RED, MinionType.HEAVY, Hex(8, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(9, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(11, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(7, -5))
    }

    override val solution = listOf(
        card("garrus-green-2b"),
        card("brogan-blue-2b"),
        card("brogan-gold"),
        action(ActionType.MOVEMENT),
        space(6, -9),
        action(ActionType.MOVEMENT),
        space(3, -7),
        card("garrus-gold"),
        card("brogan-silver"),
        action(ActionType.PRIMARY),
        card("brogan-gold"),
        action(ActionType.MOVEMENT),
        space(3, -8),
        card("garrus-silver"),
        card("brogan-gold"),
        action(ActionType.PRIMARY),
        card("garrus-green-2b"),
        action(ActionType.PRIMARY),
        card("garrus-red-2b"),
        card("brogan-red-2b"),
        hero("garrus"),
        action(ActionType.MOVEMENT),
        space(2, -11),
        action(ActionType.PRIMARY),
        option(1),
        card("brogan-green-2a"),
        unitAt(8, -9),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("garrus-green-2b"),
                card("brogan-green-2a"),
                card("brogan-silver"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(5, -5),
                card("garrus-blue-2b"),
                card("brogan-gold"),
                action(ActionType.HOLD),
                action(ActionType.MOVEMENT),
                space(7, -8),
                card("garrus-gold"),
                card("brogan-blue-2b"),
                action(ActionType.MOVEMENT),
                space(6, -8),
                action(ActionType.PRIMARY),
                card("garrus-red-2b"),
                card("brogan-red-2b"),
                hero("garrus"),
                action(ActionType.MOVEMENT),
                space(6, -9),
                action(ActionType.PRIMARY),
            ),
        ),
    )
}

private val R28_NUDGES = listOf(
    listOf(
        "Count the battle. Their attacks will cost Blue minions. How many Red minions must fall?",
        "Brogan is in Hanu's reach. If Hanu attacks him, Brogan can defend with a card he can get back " +
            "next turn.",
        "Play Light Pilum on Garrus, moving, and Shield Bash on Brogan, moving. If Hanu attacks Brogan, " +
            "defend with Onslaught.",
    ),
    listOf(
        "Which of Brogan's cards brings a discarded card back?",
        "Bulwark lets him retrieve a discarded card.",
        "Play Angry Strike on Garrus, moving 1 space, and Bulwark on Brogan, retrieving Onslaught.",
    ),
    listOf(
        "One Red minion falls to Brogan now.",
        "Onslaught attacks a Red melee minion at 4. Holding it instead is proven to lose.",
        "Play Chilling Howl on Garrus and Onslaught on Brogan, attacking a Red melee minion.",
    ),
    listOf(
        "One more Red minion, and it isn't next to Brogan: Throwing Axe's first text is proven to lose.",
        "Its second text needs a discard. Skipping the discard loses, and so does choosing Hanu.",
        "Play Blunt Force on Garrus, moving, and Throwing Axe on Brogan, discarding Bolster and attacking" +
            " the Red melee minion.",
    ),
)
