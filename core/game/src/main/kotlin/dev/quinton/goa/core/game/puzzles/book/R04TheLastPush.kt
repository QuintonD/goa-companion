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
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.tokenAt
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 4, mined from self-play (g3777_r8_RED_push; ADR-052, ENCODING.md "Full rounds"). */
object R04TheLastPush : Puzzle() {
    override val concept = "R04"

    override val known = KnownPlays(
        cards = mapOf(
            "ignatia" to listOf("ignatia-blue-2a", "ignatia-gold", "ignatia-green-2b", "ignatia-silver"),
            "razzle" to listOf("razzle-blue-3b", "razzle-silver", "razzle-green-2b", "razzle-red-2a"),
        ),
        actions = mapOf(
            "ignatia" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY)),
                2 to listOf(Choice.Action(ActionType.HOLD)),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(0, 1))),
                4 to listOf(Choice.Action(ActionType.PRIMARY)),
            ),
            "razzle" to mapOf(
                1 to listOf(Choice.Action(ActionType.FAST_TRAVEL), Choice.Space(Hex(12, -7))),
                2 to listOf(Choice.Action(ActionType.HOLD)),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(10, -5))),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r04",
        number = 4,
        title = "The Last Push",
        difficulty = 2,
        theme = "Trees set up for a later shot, three minions in three turns",
        heroes = listOf("wuk", "bain", "ignatia", "razzle"),
        goal = PuzzleGoal.PushLane(
            Deadline.END_OF_ROUND,
            "Push the lane by the end of the round. It is the last Wave counter: the push wins the game.",
        ),
        intro = "Round 8. You play Wuk and Bain for Red. Blue has more minions in the Battle Zone than you can take " +
            "in one turn, and Razzle's first card removes one of yours. Nature's Champion can shoot a unit in " +
            "range only if that unit stands next to a Tree token. Tree tokens stay at the end of a round.",
        nudges = R04_NUDGES.first(),
        nudgesByTurn = R04_NUDGES,
        explanation =
            "Turn 1: Razzle removes the Red ranged minion as planned. Monstrous Throw's Movement takes Wuk next " +
                "to the Blue ranged minion. Bain plays Get over here!." +
                "\n\n" +
                "Turn 2: Tree Slam defeats the Blue ranged minion at 6. Bain moves 1 space with Dead or Alive's " +
                "Movement, which lines him up for next turn." +
                "\n\n" +
                "Turn 3: Heavy Crossbow needs a straight line with no units or terrain in the way: from there it " +
                "reaches a Blue melee minion and defeats it at 8. Mystic Saplings takes the Trees already on the " +
                "board and places them again, next to the Blue melee minions." +
                "\n\n" +
                "Turn 4: Nature's Champion shoots a Blue melee minion next to a Tree and defeats it. In the Minion " +
                "Battle Red has 4 and Blue 2: Blue removes its last two, Red pushes the lane with the last Wave " +
                "counter, and Red wins the last push (p.3, p.8)." +
                "\n\n" +
                "The computer at strength 7, playing your heroes, doesn't find it, and neither does strength 10.",
        rules = listOf(
            "p.8 Minion Battle, Push the Lane",
            "p.3 Last Push",
            "p.19 Tokens",
            "p.19 Straight line",
        ),
        known = known.display(
            mapOf(
                "ignatia" to mapOf(
                    1 to "Spontaneous Immolation.",
                    2 to "Holds.",
                    3 to "Moves to the space outlined, next to the Red heavy minion.",
                    4 to "Equilibrium.",
                ),
                "razzle" to mapOf(
                    1 to "Aaaand it's gone!: fast travels to the space outlined and removes the Red ranged minion.",
                    2 to "Holds.",
                    3 to "Moves to the space outlined.",
                    4 to "Holds.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("wuk", "bain")

    override fun PositionBuilder.setUp() {
        round = 8
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 1)
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 1)
        piece(
            PieceState(
                "t54", "token_tree", "Tree",
                Hex(
                    0,
                    -5,
                ),
                "wuk", Team.RED, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t55", "token_tree", "Tree",
                Hex(
                    0,
                    -4,
                ),
                "wuk", Team.RED, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t56", "token_tree", "Tree",
                Hex(
                    -1,
                    -5,
                ),
                "wuk", Team.RED, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        hero("wuk", Team.RED, Hex(2, -2)) {
            level = 7
            coins = 1
            hand("wuk-gold", "wuk-silver", "wuk-red-3a", "wuk-green-3a", "wuk-blue-3b")
            items("wuk-red-2b", "wuk-green-2a", "wuk-blue-2a", "wuk-red-3b", "wuk-green-3b", "wuk-blue-3a")
            deck("wuk-red-1", "wuk-green-1", "wuk-blue-1")
        }
        hero("bain", Team.RED, Hex(-1, -3)) {
            level = 6
            hand("bain-gold", "bain-silver", "bain-red-2a", "bain-green-3a", "bain-blue-3a")
            items("bain-red-2b", "bain-green-2b", "bain-blue-2b", "bain-green-3b", "bain-blue-3b")
            deck("bain-red-1", "bain-green-1", "bain-blue-1")
        }
        hero("ignatia", Team.BLUE, Hex(-1, 2)) {
            level = 5
            hand("ignatia-gold", "ignatia-silver", "ignatia-green-2b", "ignatia-blue-2a", "ignatia-red-3b")
            items("ignatia-red-2a", "ignatia-green-2a", "ignatia-blue-2b", "ignatia-red-3a")
            deck("ignatia-red-1", "ignatia-green-1", "ignatia-blue-1")
        }
        hero("razzle", Team.BLUE, Hex(0, 4)) {
            level = 5
            hand("razzle-gold", "razzle-silver", "razzle-red-2a", "razzle-green-2b", "razzle-blue-3b")
            items("razzle-red-2b", "razzle-green-2a", "razzle-blue-2b", "razzle-blue-3a")
            deck("razzle-red-1", "razzle-green-1", "razzle-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.RANGED, Hex(3, -4))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
    }

    override val solution = listOf(
        card("wuk-green-3a"),
        card("bain-silver"),
        action(ActionType.PRIMARY),
        unitAt(0, -3),
        action(ActionType.MOVEMENT),
        space(4, -4),
        card("wuk-gold"),
        card("bain-gold"),
        action(ActionType.MOVEMENT),
        space(-1, -4),
        action(ActionType.PRIMARY),
        card("wuk-silver"),
        card("bain-red-2a"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        tokenAt(0, -5),
        space(4, -8),
        tokenAt(0, -4),
        space(6, -8),
        tokenAt(-1, -5),
        space(7, -8),
        card("wuk-red-3a"),
        card("bain-green-3a"),
        action(ActionType.PRIMARY),
        action(ActionType.MOVEMENT),
        space(-1, -6),
    )

    override val wrongTries = listOf(
        WrongTry(
            "The computer at strength 7 on your heroes",
            listOf(
                card("wuk-red-3a"),
                card("bain-blue-3a"),
                action(ActionType.MOVEMENT),
                space(1, -2),
                action(ActionType.MOVEMENT),
                space(4, -5),
                card("wuk-gold"),
                card("bain-green-3a"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(3, -4),
                card("wuk-green-3a"),
                card("bain-red-2a"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(5, -7),
                card("wuk-silver"),
                card("bain-gold"),
                action(ActionType.MOVEMENT),
                space(3, -5),
                action(ActionType.HOLD),
            ),
        ),
    )
}

private val R04_NUDGES = listOf(
    listOf(
        "Count the Minion Battle: Red will lose its ranged minion to Razzle. How many Blue minions must " +
            "be gone by the end?",
        "Three Blue minions, one a turn from turn 2. This turn is for getting into position.",
        "Play Monstrous Throw on Wuk, moving next to the Blue ranged minion, and Get over here! on Bain.",
    ),
    listOf(
        "Wuk is next to the first one. Bain has a straight-line shot next turn: where must he stand for " +
            "it?",
        "Tree Slam targets a minion next to Wuk. Bain only has to move.",
        "Play Tree Slam on Wuk, attacking the Blue ranged minion, and Dead or Alive on Bain, moving 1 " +
            "space.",
    ),
    listOf(
        "Bain can take the second one now. Wuk's last attack needs something on the board first.",
        "Nature's Champion targets a unit in range next to a Tree token. Place Trees next to the Blue " +
            "minions you want.",
        "Play Mystic Saplings on Wuk, placing Trees next to the Blue melee minions, and Heavy Crossbow on" +
            " Bain, shooting a Blue melee minion.",
    ),
    listOf(
        "One more Blue minion, and you are done. Which is next to a Tree?",
        "Nature's Champion shoots at 8 here. Bain has nothing to do but stay safe.",
        "Play Nature's Champion on Wuk, attacking the Blue melee minion, and Perfect Getaway on Bain, who" +
            " moves.",
    ),
)
