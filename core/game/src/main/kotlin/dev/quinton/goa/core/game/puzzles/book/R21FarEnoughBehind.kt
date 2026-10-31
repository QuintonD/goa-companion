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
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 20, mined from self-play (g2850_r6_BLUE_push; ADR-052, ENCODING.md "Full rounds"). */
object R21FarEnoughBehind : Puzzle() {
    override val concept = "R21"

    override val known = KnownPlays(
        cards = mapOf(
            "swift" to listOf("swift-blue-2a", "swift-gold", "swift-green-2a", "swift-red-3b"),
            "razzle" to listOf("razzle-green-2b", "razzle-silver", "razzle-red-3b", "razzle-gold"),
        ),
        actions = mapOf(
            "swift" to mapOf(
                1 to
                    listOf(
                        Choice.Action(ActionType.PRIMARY),
                        Choice.Space(Hex(6, -6)),
                        Choice.Unit("ignatia"),
                        Choice.Number(1),
                    ),
                2 to listOf(Choice.Action(ActionType.HOLD)),
                3 to listOf(Choice.Action(ActionType.PRIMARY)),
            ),
            "razzle" to mapOf(
                1 to listOf(Choice.Unit("rz22"), Choice.Action(ActionType.HOLD)),
                2 to listOf(Choice.Unit("razzle"), Choice.Action(ActionType.PRIMARY)),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(9, -12))),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r21",
        number = 20,
        title = "Far Enough Behind",
        difficulty = 2,
        theme = "Minion Battle arithmetic, Tie Breaker text",
        heroes = listOf("ignatia", "garrus", "swift", "razzle"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_ROUND, "Push the lane by the end of the round."),
        intro = "Round 6. You play Ignatia and Garrus for Blue. Ignatia's cards change with the Tie Breaker coin, " +
            "which flips when a tied card resolves. The Battle Zone is Lane Zone 3. Win the Minion Battle by " +
            "enough and Red loses its last minion there (p.8).",
        nudges = R21_NUDGES.first(),
        nudgesByTurn = R21_NUDGES,
        explanation =
            "This is one way through, and other plans win too; Equilibrium is proven not to be needed. What they " +
                "share is the count: Red has to end the round far enough behind." +
                "\n\n" +
                "Turn 1: Swift lands beside you and pushes Ignatia, as planned. Chase's Movement takes Garrus 4 " +
                "spaces, next to the Red melee and ranged minions; Ignatia walks 3 spaces to Lane Zone 2 with " +
                "Spontaneous Immolation's Movement." +
                "\n\n" +
                "Turn 2: Razzle takes her doubles off the board with Crowd Control. Angry Strike defeats a Red melee " +
                "minion. Ignatia plays Equilibrium: this round, each time she performs a primary action she may apply" +
                " either coin's text. It does nothing yet." +
                "\n\n" +
                "Turn 3: Chilling Howl lets Garrus discard Chase, a card already resolved; discarding Angry Strike or" +
                " nothing wins as well, and Pin Down then takes a card from his hand, Light Pilum here. Path of Ashes" +
                " takes Ignatia 2 spaces with its blue text, leaving Magma behind her, and her tie flips the coin to " +
                "orange." +
                "\n\n" +
                "Turn 4: Form Up! moves Ignatia 1 space. With the coin on orange, Crack of Doom would only reach a " +
                "unit at maximum range, which is proven to lose here, but Equilibrium lets her apply the blue text: " +
                "she attacks the Red ranged minion next to her for 5 and defeats it. In the Minion Battle Red has 2 " +
                "and Blue 4, Red removes 2, its last two, and Blue pushes the lane (p.8).",
        rules = listOf(
            "p.8 Minion Battle, Push the Lane",
            "p.5 Tie Breaker coin",
            "p.7 Tied Initiative",
            "p.17 Active Effects",
            "p.10 Discard a Card",
        ),
        known = known.display(
            mapOf(
                "swift" to mapOf(
                    1 to "Lands on the space outlined and pushes Ignatia 1 space.",
                    2 to "Holds.",
                    3 to "Makes Garrus discard a card.",
                ),
                "razzle" to mapOf(
                    1 to "Holds.",
                    2 to "Removes her three doubles.",
                    3 to "Moves to the space outlined.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("ignatia", "garrus")

    override fun PositionBuilder.setUp() {
        round = 6
        tieBreaker = Team.BLUE
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        piece(
            PieceState(
                "rz21", "razzle_double", "Razzle",
                Hex(
                    3,
                    -9,
                ),
                "razzle", Team.RED, false, true, false, false, false, "razzle", linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "rz22", "razzle_double", "Razzle",
                Hex(
                    7,
                    -11,
                ),
                "razzle", Team.RED, false, true, false, false, false, "razzle", linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "rz38", "razzle_double", "Razzle",
                Hex(
                    3,
                    -8,
                ),
                "razzle", Team.RED, false, true, false, false, false, "razzle", linkedMapOf(),
            ),
        )
        hero("swift", Team.RED, Hex(6, -4)) {
            level = 5
            coins = 1
            hand("swift-gold", "swift-silver", "swift-green-2a", "swift-blue-2a", "swift-red-3b")
            items("swift-red-2b", "swift-green-2b", "swift-blue-2b", "swift-red-3a")
            deck("swift-red-1", "swift-green-1", "swift-blue-1")
        }
        hero("razzle", Team.RED, Hex(6, -7)) {
            level = 5
            hand("razzle-gold", "razzle-silver", "razzle-green-2b", "razzle-blue-2b", "razzle-red-3b")
            items("razzle-red-2a", "razzle-green-2a", "razzle-blue-2a", "razzle-red-3a")
            deck("razzle-red-1", "razzle-green-1", "razzle-blue-1")
        }
        hero("ignatia", Team.BLUE, Hex(5, -5)) {
            level = 3
            coins = 2
            hand("ignatia-gold", "ignatia-silver", "ignatia-green-1", "ignatia-red-2b", "ignatia-blue-2a")
            items("ignatia-red-2a", "ignatia-blue-2b")
            deck("ignatia-red-1", "ignatia-blue-1")
        }
        hero("garrus", Team.BLUE, Hex(6, -5)) {
            level = 4
            coins = 3
            hand("garrus-gold", "garrus-silver", "garrus-red-2a", "garrus-green-2b", "garrus-blue-2b")
            items("garrus-red-2b", "garrus-green-2a", "garrus-blue-2a")
            deck("garrus-red-1", "garrus-green-1", "garrus-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(7, -10))
        minion(Team.RED, MinionType.MELEE, Hex(8, -9))
        minion(Team.BLUE, MinionType.RANGED, Hex(9, -9))
        minion(Team.RED, MinionType.HEAVY, Hex(8, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(9, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(11, -8))
        minion(Team.RED, MinionType.RANGED, Hex(6, -9))
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -6))
    }

    override val solution = listOf(
        card("ignatia-blue-2a"),
        card("garrus-red-2a"),
        hero("garrus"),
        action(ActionType.MOVEMENT),
        space(7, -9),
        action(ActionType.MOVEMENT),
        space(4, -7),
        card("ignatia-silver"),
        card("garrus-gold"),
        action(ActionType.PRIMARY),
        unitAt(7, -10),
        action(ActionType.PRIMARY),
        card("ignatia-green-1"),
        card("garrus-silver"),
        action(ActionType.PRIMARY),
        card("garrus-red-2a"),
        action(ActionType.PRIMARY),
        option(0),
        space(4, -9),
        card("garrus-green-2b"),
        card("ignatia-red-2b"),
        card("garrus-blue-2b"),
        action(ActionType.PRIMARY),
        decline(),
        action(ActionType.PRIMARY),
        option(0),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("ignatia-green-1"),
                card("garrus-blue-2b"),
                action(ActionType.PRIMARY),
                space(7, -6),
                decline(),
                action(ActionType.PRIMARY),
                space(6, -4),
                space(7, -5),
                card("ignatia-red-2b"),
                card("garrus-gold"),
                action(ActionType.PRIMARY),
                action(ActionType.HOLD),
                card("ignatia-blue-2a"),
                card("garrus-green-2b"),
                action(ActionType.MOVEMENT),
                space(5, -7),
                action(ActionType.PRIMARY),
                space(5, -5),
                card("ignatia-gold"),
                card("garrus-red-2a"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(6, -8),
            ),
        ),
    )
}

private val R21_NUDGES = listOf(
    listOf(
        "Count the minions in the Battle Zone. How many must go before the battle takes the rest?",
        "Garrus can reach the Red minions now. Ignatia needs to be somewhere else for a while.",
        "Play Spontaneous Immolation on Ignatia and Chase on Garrus; Garrus goes first and runs 4 spaces " +
            "next to the Red melee and ranged minions.",
    ),
    listOf(
        "One Red minion falls to Garrus this turn. Ignatia's card this turn isn't for this turn.",
        "Equilibrium lasts the whole round: each time, you may apply either coin's text.",
        "Play Equilibrium on Ignatia and Angry Strike on Garrus, and attack a Red melee minion with Angry" +
            " Strike.",
    ),
    listOf(
        "Swift is going to make Garrus discard, whatever you do. Don't let it be Form Up!: that loses.",
        "Chilling Howl lets him discard a resolved card first, if you like: it makes no difference here. " +
            "Ignatia still has a way to go.",
        "Play Path of Ashes on Ignatia and Chilling Howl on Garrus. Ignatia moves 2 spaces in a straight " +
            "line with the Blue text.",
    ),
    listOf(
        "The coin shows orange now. Crack of Doom's orange text reaches only a unit at maximum range. Is " +
            "that what you want?",
        "Equilibrium lets Ignatia use the blue text anyway: a unit next to her. Garrus can bring her next" +
            " to it first.",
        "Play Crack of Doom on Ignatia and Form Up! on Garrus. Form Up! moves Ignatia once, then she " +
            "attacks the Red ranged minion with the blue text.",
    ),
)
