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

/** Full round 19, mined from self-play (g3816_r7_RED_win; ADR-052, ENCODING.md "Full rounds"). */
object R19EveryBodyCounts : Puzzle() {
    override val concept = "R19"

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
        id = "r19",
        number = 19,
        title = "Every Body Counts",
        difficulty = 3,
        theme = "Razzle's doubles moving minions, the last Wave counter",
        heroes = listOf("cutter", "razzle", "min", "emmitt"),
        goal = PuzzleGoal.WinGame("Win the game this round.", Deadline.END_OF_ROUND),
        intro = "Round 7. You play Cutter and Razzle for Red. The lane has one Wave counter left: push it and you win" +
            " (p.3). Razzle has three doubles on the board, and each of them is Razzle. Spectacle swaps her with " +
            "a minion in range, and another one of her may repeat it on a different minion. The Battle Zone is " +
            "what counts at the end of the round.",
        nudges = R19_NUDGES.first(),
        nudgesByTurn = R19_NUDGES,
        explanation =
            "Turn 1: Ramming Speed's Movement takes Cutter next to the Red ranged minion. Spectacle has Razzle " +
                "swap with the Red ranged minion, and another of her repeat it with the Red melee minion." +
                "\n\n" +
                "Turn 2: Stunt Doubles acts first and defeats the Blue ranged minion at 2. Walk the Plank's Movement " +
                "takes Cutter 1 space. You choose the Glitch that Emmitt swaps with." +
                "\n\n" +
                "Turn 3: Crowd Control takes Razzle's three doubles off the board. Grappling Bolt pulls Cutter 1 " +
                "space to an obstacle, next to where he wants to attack." +
                "\n\n" +
                "Turn 4: Rummage's Movement takes Razzle 3 spaces, next to the Red ranged and heavy minions. Bold " +
                "Thrust defeats the Blue melee minion next to Cutter. In the Minion Battle Red has 4 and Blue 2: Blue" +
                " removes its last two, Red pushes with the last Wave counter, and Red wins the last push (p.3, p.8)." +
                "\n\n" +
                "The computer at strength 7, playing your heroes, doesn't find it, and neither does strength 10.",
        rules = listOf(
            "p.3 Last Push",
            "p.8 Minion Battle, Push the Lane",
            "p.13 Heavy Minion Immunity",
        ),
        known = known.display(
            mapOf(
                "min" to mapOf(
                    1 to "Poof!, without moving.",
                    2 to "Moves to the space outlined.",
                    3 to "Smoke Bomb, on the space outlined.",
                ),
                "emmitt" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Unstable Timeline: Glitch tokens on the spaces outlined, and you choose the one he swaps" +
                        " with.",
                    3 to "Clears nothing.",
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
            "The computer at strength 7 on your heroes",
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

private val R19_NUDGES = listOf(
    listOf(
        "Count the minions in the Battle Zone. Some of yours are not in it. Which of your cards can " +
            "change that?",
        "Spectacle swaps a body of Razzle with a Red minion, twice.",
        "Play Ramming Speed on Cutter, moving next to the Red ranged minion, and Spectacle on Razzle, " +
            "swapping with the Red ranged and then the Red melee minion.",
    ),
    listOf(
        "Now the Blue side. Razzle's first attack is small, and that's enough for a ranged minion.",
        "Stunt Doubles acts first and attacks at 2. Cutter only has to move.",
        "Play Walk the Plank on Cutter, who moves, and Stunt Doubles on Razzle, attacking the Blue ranged" +
            " minion.",
    ),
    listOf(
        "Razzle's doubles have done their work. What about Cutter's last move?",
        "Crowd Control acts first. Grappling Bolt pulls Cutter next to the minion he wants next turn.",
        "Play Grappling Bolt on Cutter and Crowd Control on Razzle.",
    ),
    listOf(
        "One Blue minion to take. Count the battle after that.",
        "Bold Thrust can target a unit next to Cutter. Razzle has only to stay in the Zone.",
        "Play Bold Thrust on Cutter, attacking the Blue melee minion, and Rummage on Razzle, who moves.",
    ),
)
