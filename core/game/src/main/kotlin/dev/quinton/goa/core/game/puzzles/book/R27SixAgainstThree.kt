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
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 14, mined from self-play (g7394_r4_BLUE_push; ADR-052, ENCODING.md "Full rounds"). */
object R27SixAgainstThree : Puzzle() {
    override val concept = "R27"

    override val known = KnownPlays(
        cards = mapOf(
            "razzle" to listOf("razzle-blue-2b", "razzle-green-1", "razzle-silver", "razzle-gold"),
            "ignatia" to listOf("ignatia-blue-2a", "ignatia-red-2a", "ignatia-green-2b", "ignatia-silver"),
        ),
        actions = mapOf(
            "razzle" to mapOf(
                1 to listOf(Choice.Unit("razzle"), Choice.Action(ActionType.HOLD)),
                2 to
                    listOf(
                        Choice.Unit("rz19"),
                        Choice.Action(ActionType.PRIMARY),
                        Choice.Space(Hex(5, -7)),
                        Choice.Unit("rz18"),
                        Choice.Space(Hex(1, -6)),
                    ),
                3 to listOf(Choice.Unit("rz19"), Choice.Action(ActionType.PRIMARY)),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
            "ignatia" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -5))),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(6, -5))),
                3 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Unit("whisper")),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r27",
        number = 14,
        title = "Six Against Three",
        difficulty = 1,
        theme = "Minion Battle arithmetic, two attacks kept for the last turn",
        heroes = listOf("whisper", "hanu", "razzle", "ignatia"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_ROUND, "Push the lane by the end of the round."),
        intro = "Round 4. You play Whisper and Hanu for Blue. Hanu is off the board. In the Battle Zone Red has five " +
            "minions and Blue has six, and a side removes one minion for each it is short at the end of the round" +
            " (p.8). Razzle has three doubles out.",
        nudges = R27_NUDGES.first(),
        nudgesByTurn = R27_NUDGES,
        explanation =
            "This is one way through, and other plans win too: in turn 3 another card can do Shadow Step's work." +
                "\n\n" +
                "Turn 1: Hanu respawns and moves with Fight and Flight's Movement. Lesser Evil's Movement takes " +
                "Whisper 2 spaces." +
                "\n\n" +
                "Turn 2: Whisper plays Death Seeker and Hanu holds with Hurry up!: neither is needed for the attacks " +
                "to come." +
                "\n\n" +
                "Turn 3: Razzle takes her doubles off the board. Shadow Step's Movement takes Whisper next to the Red" +
                " ranged minion, and Unexpected Journey's takes Hanu 3 spaces." +
                "\n\n" +
                "Turn 4: Sanguine Path defeats the Red ranged minion at 3 and Outnumber a Red melee minion at 6. In " +
                "the Minion Battle Red has 3 and Blue 6: Red removes 3, all it has, and Blue pushes the lane (p.8).",
        rules = listOf(
            "p.8 Minion Battle, Push the Lane",
            "p.7 Hero Respawn",
            "p.9 Range and Radius",
        ),
        known = known.display(
            mapOf(
                "razzle" to mapOf(
                    1 to "Holds.",
                    2 to "A double performs its primary action on the spaces outlined.",
                    3 to "Removes her three doubles.",
                    4 to "Holds.",
                ),
                "ignatia" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Moves to the space outlined.",
                    3 to "Performs its primary action on Whisper, if she is in reach.",
                    4 to "Holds.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("whisper", "hanu")

    override fun PositionBuilder.setUp() {
        round = 4
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 3)
        piece(
            PieceState(
                "rz17", "razzle_double", "Razzle",
                Hex(
                    4,
                    -8,
                ),
                "razzle", Team.RED, false, true, false, false, false, "razzle", linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "rz18", "razzle_double", "Razzle",
                Hex(
                    1,
                    -7,
                ),
                "razzle", Team.RED, false, true, false, false, false, "razzle", linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "rz19", "razzle_double", "Razzle",
                Hex(
                    3,
                    -7,
                ),
                "razzle", Team.RED, false, true, false, false, false, "razzle", linkedMapOf(),
            ),
        )
        hero("razzle", Team.RED, Hex(6, -9)) {
            level = 3
            coins = 1
            hand("razzle-gold", "razzle-silver", "razzle-green-1", "razzle-red-2b", "razzle-blue-2b")
            items("razzle-red-2a", "razzle-blue-2a")
            deck("razzle-red-1", "razzle-blue-1")
        }
        hero("ignatia", Team.RED, Hex(0, -4)) {
            level = 4
            hand("ignatia-gold", "ignatia-silver", "ignatia-red-2a", "ignatia-green-2b", "ignatia-blue-2a")
            items("ignatia-red-2b", "ignatia-green-2a", "ignatia-blue-2b")
            deck("ignatia-red-1", "ignatia-green-1", "ignatia-blue-1")
        }
        hero("whisper", Team.BLUE, Hex(3, -4)) {
            level = 3
            hand("whisper-gold", "whisper-silver", "whisper-green-1", "whisper-red-2a", "whisper-blue-2b")
            items("whisper-red-2b", "whisper-blue-2a")
            deck("whisper-red-1", "whisper-blue-1")
        }
        hero("hanu", Team.BLUE, null) {
            level = 3
            coins = 1
            hand("hanu-gold", "hanu-silver", "hanu-blue-1", "hanu-red-2b", "hanu-green-2a")
            items("hanu-red-2a", "hanu-green-2b")
            deck("hanu-red-1", "hanu-green-1")
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
        minion(Team.BLUE, MinionType.MELEE, Hex(7, -5))
    }

    override val solution = listOf(
        card("whisper-blue-2b"),
        card("hanu-gold"),
        space(11, -6),
        action(ActionType.MOVEMENT),
        space(10, -7),
        action(ActionType.MOVEMENT),
        space(3, -6),
        card("whisper-silver"),
        card("hanu-silver"),
        action(ActionType.PRIMARY),
        action(ActionType.HOLD),
        card("whisper-green-1"),
        card("hanu-blue-1"),
        action(ActionType.MOVEMENT),
        space(10, -10),
        action(ActionType.MOVEMENT),
        space(7, -7),
        card("whisper-red-2a"),
        card("hanu-red-2b"),
        action(ActionType.PRIMARY),
        unitAt(6, -7),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("whisper-green-1"),
                card("hanu-green-2a"),
                action(ActionType.MOVEMENT),
                space(5, -5),
                space(11, -6),
                action(ActionType.MOVEMENT),
                decline(),
                card("whisper-silver"),
                card("hanu-blue-1"),
                action(ActionType.HOLD),
                action(ActionType.MOVEMENT),
                space(9, -6),
                card("whisper-gold"),
                card("hanu-red-2b"),
                action(ActionType.MOVEMENT),
                space(6, -6),
                action(ActionType.PRIMARY),
                unitAt(7, -6),
                card("whisper-blue-2b"),
                card("hanu-gold"),
                action(ActionType.HOLD),
                action(ActionType.MOVEMENT),
                space(8, -10),
            ),
        ),
    )
}

private val R27_NUDGES = listOf(
    listOf(
        "Five against six. How many Red minions must fall for the battle to take the rest?",
        "Hanu has to respawn and walk: the fight is a long way off.",
        "Play Lesser Evil on Whisper, moving, and Fight and Flight on Hanu, who respawns and moves.",
    ),
    listOf(
        "Nobody needs to attack this turn. Which two cards do you want left for turn 4?",
        "On this line Sanguine Path and Outnumber are the attacks to keep.",
        "Play Death Seeker on Whisper and Hurry up! on Hanu, who holds.",
    ),
    listOf(
        "Where will Whisper attack from? Shadow Step's Movement offers eleven answers.",
        "With its Movement, only the two spaces next to the Red ranged minion win: all the others are " +
            "proven to lose.",
        "Play Shadow Step on Whisper, moving next to the Red ranged minion, and Unexpected Journey on " +
            "Hanu, moving.",
    ),
    listOf(
        "Two attacks, two minions.",
        "Sanguine Path targets a unit next to Whisper. Outnumber can reach a minion in range next to your" +
            " friendly hero.",
        "Play Sanguine Path on Whisper, attacking the Red ranged minion, and Outnumber on Hanu, attacking" +
            " a Red melee minion.",
    ),
)
