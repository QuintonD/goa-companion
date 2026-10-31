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

/** Full round 4, mined from self-play (g8229_r4_RED_coins-6; ADR-052, ENCODING.md "Full rounds"). */
object R24MindGrip : Puzzle() {
    override val concept = "R24"

    override val known = KnownPlays(
        cards = mapOf(
            "tali" to listOf("tali-blue-2a", "tali-green-2b", "tali-gold", "tali-red-1"),
            "razzle" to listOf("razzle-blue-2b", "razzle-gold", "razzle-green-2b", "razzle-red-2b"),
        ),
        actions = mapOf(
            "tali" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(-2, -4))),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(-4, -2))),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(-2, -3))),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
            "razzle" to mapOf(
                1 to listOf(Choice.Unit("rz19"), Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(-3, -2))),
                2 to listOf(Choice.Unit("razzle"), Choice.Action(ActionType.PRIMARY)),
                3 to listOf(Choice.Unit("rz18"), Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(0, -6))),
                4 to listOf(Choice.Unit("rz20"), Choice.Action(ActionType.FAST_TRAVEL), Choice.Space(Hex(-1, -2))),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r24",
        number = 4,
        title = "Mind Grip",
        difficulty = 1,
        theme = "Coins counted, a token placed for one way of ending it",
        heroes = listOf("sabina", "nebkher", "tali", "razzle"),
        goal = PuzzleGoal.GainCoins(6, Deadline.END_OF_ROUND, "Gain 6 coins by the end of the round."),
        intro = "Round 4. You play Sabina and NebKher for Red. Minions pay 2 coins here, and Tali pays 3 to the hero " +
            "who defeats her and 1 to the other. Phantasmal Warrior moves an Illusion token next to an enemy hero" +
            " in range and attacks that hero, so where your Illusions stand in turn 4 decides who you can reach.",
        nudges = R24_NUDGES.first(),
        nudgesByTurn = R24_NUDGES,
        explanation =
            "This is one way through, and other plans win too: not one of Fleeting Image, Phantasmal Warrior or " +
                "defeating Tali is needed for all of them." +
                "\n\n" +
                "Turn 1: Mind Grip defeats a Blue melee minion: 2 coins. Sabina moves with Close Support." +
                "\n\n" +
                "Turn 2: Razzle defeats a Red melee minion. Point Blank Shot's Movement takes Sabina 1 space, and " +
                "Diabolical Laughter places two Illusions next to NebKher." +
                "\n\n" +
                "Turn 3: Fleeting Image places a third Illusion. On this line NebKher also swaps with one of the " +
                "Illusions, and not swapping wins as well. Sabina holds with Back to Back." +
                "\n\n" +
                "Turn 4: Phantasmal Warrior moves the Illusion next to Tali and attacks her at 3. Commune with " +
                "Spirits gives 2, not enough, and she is defeated (p.13): 3 coins for NebKher and 1 for Sabina's " +
                "assist, 6 in all.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense",
            "p.19 Tokens",
        ),
        known = known.display(
            mapOf(
                "tali" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Performs its primary action on the space outlined.",
                    3 to "Moves to the space outlined.",
                    4 to "Holds.",
                ),
                "razzle" to mapOf(
                    1 to "A double moves to the space outlined.",
                    2 to "Attacks a unit in its reach.",
                    3 to "A double moves to the space outlined.",
                    4 to "A double fast travels to the space outlined.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("sabina", "nebkher")

    override fun PositionBuilder.setUp() {
        round = 4
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-1")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 4)
        piece(
            PieceState(
                "rz18", "razzle_double", "Razzle",
                Hex(
                    1,
                    -6,
                ),
                "razzle", Team.BLUE, false, true, false, false, false, "razzle", linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "rz19", "razzle_double", "Razzle",
                Hex(
                    0,
                    -4,
                ),
                "razzle", Team.BLUE, false, true, false, false, false, "razzle", linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "rz20", "razzle_double", "Razzle",
                Hex(
                    2,
                    -6,
                ),
                "razzle", Team.BLUE, false, true, false, false, false, "razzle", linkedMapOf(),
            ),
        )
        hero("sabina", Team.RED, Hex(-6, -3)) {
            level = 3
            hand("sabina-gold", "sabina-silver", "sabina-red-1", "sabina-green-2b", "sabina-blue-2a")
            items("sabina-green-2a", "sabina-blue-2b")
            deck("sabina-green-1", "sabina-blue-1")
        }
        hero("nebkher", Team.RED, Hex(-3, 2)) {
            level = 2
            coins = 1
            hand("nebkher-gold", "nebkher-silver", "nebkher-green-1", "nebkher-blue-1", "nebkher-red-2a")
            items("nebkher-red-2b")
            deck("nebkher-red-1")
        }
        hero("tali", Team.BLUE, Hex(1, -7)) {
            level = 3
            hand("tali-gold", "tali-silver", "tali-red-1", "tali-green-2b", "tali-blue-2a")
            items("tali-green-2a", "tali-blue-2b")
            deck("tali-green-1", "tali-blue-1")
        }
        hero("razzle", Team.BLUE, Hex(2, -8)) {
            level = 4
            coins = 4
            hand("razzle-gold", "razzle-silver", "razzle-red-2b", "razzle-green-2b", "razzle-blue-2b")
            items("razzle-red-2a", "razzle-green-2a", "razzle-blue-2a")
            deck("razzle-red-1", "razzle-green-1", "razzle-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(-4, -2))
        minion(Team.RED, MinionType.MELEE, Hex(-5, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(-4, -1))
        minion(Team.BLUE, MinionType.RANGED, Hex(-3, 0))
        minion(Team.RED, MinionType.MELEE, Hex(-8, 1))
        minion(Team.RED, MinionType.HEAVY, Hex(-6, 1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(-5, 1))
        minion(Team.RED, MinionType.RANGED, Hex(-6, 2))
        minion(Team.BLUE, MinionType.MELEE, Hex(-5, 2))
        minion(Team.BLUE, MinionType.MELEE, Hex(-4, 3))
        minion(Team.RED, MinionType.MELEE, Hex(-5, 4))
    }

    override val solution = listOf(
        card("sabina-green-2b"),
        card("nebkher-gold"),
        action(ActionType.PRIMARY),
        action(ActionType.MOVEMENT),
        space(-5, -5),
        card("sabina-gold"),
        card("nebkher-silver"),
        action(ActionType.MOVEMENT),
        space(-5, -6),
        action(ActionType.PRIMARY),
        option(1),
        space(-3, 1),
        option(0),
        option(1),
        space(-2, 0),
        card("sabina-silver"),
        card("nebkher-green-1"),
        action(ActionType.HOLD),
        action(ActionType.PRIMARY),
        space(-3, -1),
        tokenAt(-3, 2),
        card("sabina-red-1"),
        card("nebkher-red-2a"),
        action(ActionType.MOVEMENT),
        space(-3, -7),
        action(ActionType.PRIMARY),
        tokenAt(-3, -1),
        space(-2, -2),
        hero("tali"),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("sabina-blue-2a"),
                card("nebkher-gold"),
                action(ActionType.MOVEMENT),
                space(-5, -2),
                action(ActionType.PRIMARY),
                card("sabina-red-1"),
                card("nebkher-silver"),
                action(ActionType.PRIMARY),
                unitAt(-4, -1),
                action(ActionType.HOLD),
                card("sabina-green-2b"),
                card("nebkher-blue-1"),
                action(ActionType.PRIMARY),
                option(2),
                action(ActionType.HOLD),
                card("sabina-silver"),
                card("nebkher-red-2a"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(-4, 0),
            ),
        ),
    )
}

private val R24_NUDGES = listOf(
    listOf(
        "On this line six coins is a minion and Tali. Which of them is there for the taking now?",
        "NebKher's gold card can start on the minion at once. Sabina only has to move.",
        "Play Close Support on Sabina, moving, and Mind Grip on NebKher, defeating the Blue melee minion.",
    ),
    listOf(
        "Phantasmal Warrior needs an Illusion token in range in turn 4. Where do they come from?",
        "Diabolical Laughter can place Illusions next to NebKher, one choice at a time.",
        "Play Point Blank Shot on Sabina, who moves 1 space, and Diabolical Laughter on NebKher, placing " +
            "two Illusions.",
    ),
    listOf(
        "One more Illusion, and its place matters: every other space Fleeting Image offers here is proven" +
            " to lose.",
        "Tali ends this turn on the outlined space. Place the Illusion where Phantasmal Warrior can reach" +
            " next to her next turn.",
        "Play Back to Back on Sabina and Fleeting Image on NebKher, placing an Illusion two spaces from " +
            "where Tali is going.",
    ),
    listOf(
        "Which Illusion can get next to Tali, and what can her last defense do against Attack 3?",
        "Phantasmal Warrior moves the Illusion next to Tali and attacks her. Choosing Razzle instead is " +
            "proven to lose.",
        "Play Quickdraw on Sabina, who moves, and Phantasmal Warrior on NebKher, attacking Tali.",
    ),
)
