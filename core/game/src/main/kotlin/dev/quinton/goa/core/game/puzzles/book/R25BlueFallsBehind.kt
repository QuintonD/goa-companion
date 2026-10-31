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
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.tokenAt
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 16, mined from self-play (g3816_r7_RED_push; ADR-052, ENCODING.md "Full rounds"). */
object R25BlueFallsBehind : Puzzle() {
    override val concept = "R25"

    override val known = KnownPlays(
        cards = mapOf(
            "min" to listOf("min-green-1", "min-blue-2b", "min-silver", "min-gold"),
            "emmitt" to listOf("emmitt-green-2a", "emmitt-silver", "emmitt-gold", "emmitt-blue-2a"),
        ),
        actions = mapOf(
            "min" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Decline("Don't move")),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -9))),
                3 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(4, -8))),
            ),
            "emmitt" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(0, -7))),
                2 to
                    listOf(
                        Choice.Action(ActionType.PRIMARY),
                        Choice.Space(Hex(1, -7)),
                        Choice.Space(Hex(4, -9)),
                        Choice.Unit("cutter"),
                    ),
                3 to listOf(Choice.Action(ActionType.CLEAR), Choice.Decline("Stop clearing")),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r25",
        number = 16,
        title = "Blue Falls Behind",
        difficulty = 2,
        theme = "Minion Battle arithmetic, swapping with your own minions on this line",
        heroes = listOf("cutter", "razzle", "min", "emmitt"),
        goal = PuzzleGoal.PushLane(
            Deadline.END_OF_ROUND,
            "Push the lane by the end of the round. It is the last Wave counter: the push wins the game.",
        ),
        intro = "Round 7. You play Cutter and Razzle for Red. Razzle has three doubles out, and any one of them can " +
            "perform her action. In the Battle Zone Red has four minions and Blue has four, and none of your " +
            "heroes stands next to a Blue minion yet.",
        nudges = R25_NUDGES.first(),
        nudgesByTurn = R25_NUDGES,
        explanation =
            "This is one way through, and other plans win too: not one of Spectacle, Grappling Bolt or Bold " +
                "Thrust is needed for all of them." +
                "\n\n" +
                "Turn 1: Spectacle is performed by a double, who swaps with the Red ranged minion, and repeated by " +
                "Razzle, who swaps with the Red melee minion: that puts her next to the Blue ranged minion and leaves" +
                " every Red minion alive. Cutter moves 1 space with Ramming Speed." +
                "\n\n" +
                "Turn 2: Stunt Doubles attacks the Blue ranged minion at 2 and defeats it. Walk the Plank's Movement " +
                "takes Cutter 1 space, which lines him up for next turn. Emmitt's Unstable Timeline has you choose " +
                "the Glitch he swaps with." +
                "\n\n" +
                "Turn 3: Crowd Control removes Razzle's doubles. Grappling Bolt pulls Cutter along a straight line to" +
                " the Blue melee minion, an obstacle (p.18)." +
                "\n\n" +
                "Turn 4: Bold Thrust attacks the Blue melee minion next to Cutter at 6 and defeats it. In the Minion " +
                "Battle Red has 4 and Blue 2: Blue removes its last two, Red pushes the lane with the last Wave " +
                "counter, and Red wins the last push (p.3, p.8).",
        rules = listOf(
            "p.8 Minion Battle, Push the Lane",
            "p.3 Last Push",
            "p.18 Obstacle",
            "p.19 Straight line",
        ),
        known = known.display(
            mapOf(
                "min" to mapOf(
                    1 to "Takes its Movement and stays where it is.",
                    2 to "Moves to the space outlined.",
                    3 to "Places a Smoke Bomb on the space outlined.",
                ),
                "emmitt" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Places Glitch tokens on the spaces outlined, and swaps with one: you choose which.",
                    3 to "Starts its Clear action and stops at once.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("cutter", "razzle")

    override fun PositionBuilder.setUp() {
        round = 7
        tieBreaker = Team.BLUE
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 1)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 2)
        piece(
            PieceState(
                "rz57", "razzle_double", "Razzle",
                Hex(
                    0,
                    -1,
                ),
                "razzle", Team.RED, false, true, false, false, false, "razzle", linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "rz58", "razzle_double", "Razzle",
                Hex(
                    1,
                    -2,
                ),
                "razzle", Team.RED, false, true, false, false, false, "razzle", linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "rz59", "razzle_double", "Razzle",
                Hex(
                    1,
                    -1,
                ),
                "razzle", Team.RED, false, true, false, false, false, "razzle", linkedMapOf(),
            ),
        )
        hero("cutter", Team.RED, Hex(-1, -1)) {
            level = 5
            hand("cutter-gold", "cutter-silver", "cutter-red-2a", "cutter-green-2a", "cutter-blue-3a")
            items("cutter-red-2b", "cutter-green-2b", "cutter-blue-2a", "cutter-blue-3b")
            deck("cutter-red-1", "cutter-green-1", "cutter-blue-1")
        }
        hero("razzle", Team.RED, Hex(2, -1)) {
            level = 6
            coins = 2
            hand("razzle-gold", "razzle-silver", "razzle-red-2b", "razzle-green-3b", "razzle-blue-3a")
            items("razzle-red-2a", "razzle-green-2a", "razzle-blue-2b", "razzle-green-3a", "razzle-blue-3b")
            deck("razzle-red-1", "razzle-green-1", "razzle-blue-1")
        }
        hero("min", Team.BLUE, Hex(4, -11)) {
            level = 3
            coins = 3
            hand("min-gold", "min-silver", "min-green-1", "min-red-2b", "min-blue-2b")
            items("min-red-2a", "min-blue-2a")
            deck("min-red-1", "min-blue-1")
        }
        hero("emmitt", Team.BLUE, Hex(0, -6)) {
            level = 4
            coins = 3
            hand("emmitt-gold", "emmitt-silver", "emmitt-red-2a", "emmitt-green-2a", "emmitt-blue-2a")
            items("emmitt-red-2b", "emmitt-green-2b", "emmitt-blue-2b")
            deck("emmitt-red-1", "emmitt-green-1", "emmitt-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.RANGED, Hex(3, -4))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
    }

    override val solution = listOf(
        card("cutter-green-2a"),
        card("razzle-green-3b"),
        action(ActionType.MOVEMENT),
        space(0, -2),
        unitAt(0, -1),
        action(ActionType.PRIMARY),
        unitAt(0, -3),
        unitAt(2, -1),
        card("cutter-gold"),
        card("razzle-gold"),
        hero("razzle"),
        action(ActionType.PRIMARY),
        action(ActionType.MOVEMENT),
        space(-1, -2),
        tokenAt(4, -9),
        card("cutter-silver"),
        card("razzle-silver"),
        hero("razzle"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        space(1, -4),
        card("cutter-red-2a"),
        card("razzle-red-2b"),
        action(ActionType.MOVEMENT),
        space(0, 0),
        action(ActionType.PRIMARY),
        option(1),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("cutter-gold"),
                card("razzle-blue-3a"),
                hero("razzle"),
                hero("razzle"),
                action(ActionType.PRIMARY),
                hero("cutter"),
                unitAt(1, -2),
                space(4, -4),
                action(ActionType.MOVEMENT),
                space(3, -2),
                card("cutter-silver"),
                card("razzle-red-2b"),
                unitAt(4, -4),
                action(ActionType.PRIMARY),
                action(ActionType.PRIMARY),
                space(1, 0),
                tokenAt(4, -9),
                card("cutter-green-2a"),
                card("razzle-green-3b"),
                action(ActionType.MOVEMENT),
                space(3, -3),
                hero("razzle"),
                action(ActionType.HOLD),
                card("cutter-red-2a"),
                card("razzle-gold"),
                unitAt(-1, -1),
                action(ActionType.MOVEMENT),
                space(0, -2),
                action(ActionType.MOVEMENT),
                space(3, -6),
            ),
        ),
    )
}

private val R25_NUDGES = listOf(
    listOf(
        "Count the battle. How many Blue minions must be gone for Blue to lose its last two?",
        "Spectacle swaps one of you with a minion in range, and another of you may repeat it on a " +
            "different minion.",
        "Play Ramming Speed on Cutter, moving, and Spectacle on Razzle: a double swaps with the Red " +
            "ranged minion and Razzle herself with the Red melee minion.",
    ),
    listOf(
        "Razzle now stands next to the Blue ranged minion.",
        "Stunt Doubles attacks at 2, which is enough for a minion. Cutter only has to step into line.",
        "Play Walk the Plank on Cutter, who moves 1 space, and Stunt Doubles on Razzle, attacking the " +
            "Blue ranged minion.",
    ),
    listOf(
        "Cutter's way to the other Blue minion is a hook, and it needs an obstacle in a straight line. A " +
            "unit is an obstacle.",
        "Grappling Bolt has two targets here. Hooking onto the far one is proven to lose.",
        "Play Grappling Bolt on Cutter, hooking onto the Blue melee minion, and Crowd Control on Razzle.",
    ),
    listOf(
        "Cutter stands next to the Blue melee minion. Which text of Bold Thrust reaches it?",
        "Bold Thrust's text for a unit next to you is the one: its other text is proven to lose. " +
            "Broadside isn't enough.",
        "Play Bold Thrust on Cutter, attacking the Blue melee minion, and Rummage on Razzle, who moves.",
    ),
)
