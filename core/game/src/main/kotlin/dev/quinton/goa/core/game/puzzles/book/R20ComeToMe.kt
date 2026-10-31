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
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 20, mined from self-play (g6433_r2_RED_coins-6; ADR-052, ENCODING.md "Full rounds"). */
object R20ComeToMe : Puzzle() {
    override val concept = "R20"

    override val known = KnownPlays(
        cards = mapOf(
            "razzle" to listOf("razzle-red-1", "razzle-gold", "razzle-silver", "razzle-green-2a"),
            "wasp" to listOf("wasp-green-1", "wasp-blue-2b", "wasp-silver", "wasp-red-1"),
        ),
        actions = mapOf(
            "razzle" to mapOf(
                1 to listOf(Choice.Unit("razzle"), Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(-2, 0))),
                2 to listOf(Choice.Unit("razzle"), Choice.Action(ActionType.PRIMARY)),
                3 to listOf(Choice.Unit("razzle"), Choice.Action(ActionType.PRIMARY)),
            ),
            "wasp" to mapOf(
                1 to listOf(Choice.Decline("Don't respawn")),
                2 to listOf(Choice.Space(Hex(8, -2)), Choice.Action(ActionType.FAST_TRAVEL), Choice.Space(Hex(5, -5))),
                3 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r20",
        number = 20,
        title = "Come to Me",
        difficulty = 3,
        theme = "Bringing the target to you, a Turret set up for later, counting coins",
        heroes = listOf("xargatha", "trinkets", "razzle", "wasp"),
        goal = PuzzleGoal.GainCoins(6, Deadline.END_OF_ROUND, "Gain 6 coins by the end of the round."),
        intro = "Round 2. You play Xargatha and Trinkets for Red. Coins come from minions (2 each here) and from " +
            "defeating a hero and assisting. Wasp is off the board, and Razzle has three doubles out. Their last " +
            "turn isn't known: they answer as hard as they can.",
        nudges = R20_NUDGES.first(),
        nudgesByTurn = R20_NUDGES,
        explanation =
            "Turn 1: Trinkets holds. Charm moves Xargatha and then the Blue ranged minion 2 spaces, next to " +
                "Trinkets, where Cleave can reach it. Razzle walks up to the Red melee minion and Wasp stays off the " +
                "board." +
                "\n\n" +
                "Turn 2: Razzle kills the Red melee minion. Cleave defeats the Blue ranged minion: 2 coins. Salvage " +
                "Parts places the Turret next to a Blue melee minion, which is what Makeshift Minigun needs next " +
                "turn: its target must be in range of both Trinkets and the Turret." +
                "\n\n" +
                "Turn 3: Razzle takes her doubles off the board, whichever body she acts with. Makeshift Minigun " +
                "attacks at 5 and defeats the Blue melee minion: 4 coins. Siren's Call then pulls Razzle next to " +
                "Xargatha, because she is not next to her and in range." +
                "\n\n" +
                "Turn 4: Razzle's last free card is played, and her only defense left is too small: Alleyoop gives 2 " +
                "against Attack 5, and declining is no better. Xargatha gains 2 coins for defeating her and Trinkets " +
                "1 for the assist (p.13), 7 in all: the goal needs 6." +
                "\n\n" +
                "The computer at strength 7, playing your heroes, doesn't reach it, and neither does strength 10.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.11 Movement in the card text",
            "p.9 Range and Radius",
        ),
        known = known.display(
            mapOf(
                "razzle" to mapOf(
                    1 to "Moves to the space outlined, next to the Red melee minion.",
                    2 to "Attacks the Red melee minion.",
                    3 to "Crowd Control: removes her three doubles.",
                ),
                "wasp" to mapOf(
                    1 to "Stays off the board.",
                    2 to "Respawns, then fast travels to the space outlined.",
                    3 to "Holds.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("xargatha", "trinkets")

    override fun PositionBuilder.setUp() {
        round = 2
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 3)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 3)
        piece(
            PieceState(
                "rz17", "razzle_double", "Razzle",
                Hex(
                    3,
                    -3,
                ),
                "razzle", Team.BLUE, false, true, false, false, false, "razzle", linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "rz18", "razzle_double", "Razzle",
                Hex(
                    3,
                    -2,
                ),
                "razzle", Team.BLUE, false, true, false, false, false, "razzle", linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "rz19", "razzle_double", "Razzle",
                Hex(
                    4,
                    -4,
                ),
                "razzle", Team.BLUE, false, true, false, false, false, "razzle", linkedMapOf(),
            ),
        )
        hero("xargatha", Team.RED, Hex(0, -4)) {
            level = 3
            hand("xargatha-gold", "xargatha-silver", "xargatha-green-1", "xargatha-red-2a", "xargatha-blue-2b")
            items("xargatha-red-2b", "xargatha-blue-2a")
            deck("xargatha-red-1", "xargatha-blue-1")
        }
        hero("trinkets", Team.RED, Hex(4, -7)) {
            level = 2
            hand("trinkets-gold", "trinkets-silver", "trinkets-red-1", "trinkets-green-1", "trinkets-blue-2a")
            items("trinkets-blue-2b")
            deck("trinkets-blue-1")
        }
        hero("razzle", Team.BLUE, Hex(2, -2)) {
            level = 2
            coins = 1
            hand("razzle-gold", "razzle-silver", "razzle-red-1", "razzle-blue-1", "razzle-green-2a")
            items("razzle-green-2b")
            deck("razzle-green-1")
        }
        hero("wasp", Team.BLUE, null) {
            level = 2
            coins = 1
            hand("wasp-gold", "wasp-silver", "wasp-red-1", "wasp-green-1", "wasp-blue-2b")
            items("wasp-blue-2a")
            deck("wasp-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.BLUE, MinionType.RANGED, Hex(3, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
    }

    override val solution = listOf(
        card("xargatha-green-1"),
        card("trinkets-gold"),
        action(ActionType.HOLD),
        action(ActionType.PRIMARY),
        space(2, -5),
        unitAt(3, -4),
        space(3, -6),
        card("xargatha-gold"),
        card("trinkets-silver"),
        action(ActionType.PRIMARY),
        yes(),
        action(ActionType.PRIMARY),
        space(4, -8),
        card("xargatha-silver"),
        card("trinkets-red-1"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        hero("razzle"),
        space(3, -6),
        card("xargatha-red-2a"),
        card("trinkets-green-1"),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "The computer at strength 7 on your heroes",
            listOf(
                card("xargatha-silver"),
                card("trinkets-gold"),
                action(ActionType.PRIMARY),
                option(1),
                action(ActionType.PRIMARY),
                space(1, -4),
                card("xargatha-blue-2b"),
                card("trinkets-silver"),
                action(ActionType.HOLD),
                action(ActionType.HOLD),
                card("xargatha-red-2a"),
                card("trinkets-green-1"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(4, -9),
                card("xargatha-gold"),
                card("trinkets-blue-2a"),
                hero("xargatha"),
                action(ActionType.HOLD),
                action(ActionType.PRIMARY),
            ),
        ),
    )
}

private val R20_NUDGES = listOf(
    listOf(
        "Six coins is two minions and a hero. Which of them can you reach, and which must come to you?",
        "Charm can move an enemy ranged minion in radius 2 spaces. Move it where Xargatha can hit it next" +
            " turn.",
        "Play Charm on Xargatha, moving the Blue ranged minion next to Trinkets, and Rapid Redeployment " +
            "on Trinkets, who holds.",
    ),
    listOf(
        "Xargatha can collect the first coins now. Trinkets has to get ready for next turn.",
        "Makeshift Minigun needs the target in range of Trinkets and of the Turret. The Turret isn't out " +
            "yet.",
        "Play Cleave on Xargatha, attacking the Blue ranged minion, and Salvage Parts on Trinkets, " +
            "placing the Turret next to a Blue melee minion.",
    ),
    listOf(
        "Trinkets collects two more coins. Who is the hero, and how far away is she?",
        "Siren's Call moves an enemy unit in range next to you. After Crowd Control, Razzle is one body " +
            "again.",
        "Play Siren's Call on Xargatha, pulling Razzle next to her, and Makeshift Minigun on Trinkets, " +
            "shooting the Blue melee minion.",
    ),
    listOf(
        "Razzle is next to you, and she has played one card a turn. Look at her hand and at your attack.",
        "Xargatha acts before Razzle. Razzle's last card can't stop Attack 5, and defeating her pays you " +
            "and an assist to Trinkets.",
        "Play Deadly Swipe on Xargatha, attacking Razzle, and Early Prototype on Trinkets.",
    ),
)
