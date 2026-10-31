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
import dev.quinton.goa.core.game.puzzles.Moves.tokenAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 6, mined from self-play (g2971_r5_BLUE_push; ADR-052, ENCODING.md "Full rounds"). */
object R33ATreeNextToIt : Puzzle() {
    override val concept = "R33"

    override val known = KnownPlays(
        cards = mapOf(
            "brogan" to listOf("brogan-green-3b", "brogan-silver", "brogan-blue-2b", "brogan-gold"),
            "min" to listOf("min-blue-2a", "min-red-1", "min-gold", "min-silver"),
        ),
        actions = mapOf(
            "brogan" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY)),
                2 to listOf(Choice.Action(ActionType.PRIMARY)),
                3 to listOf(Choice.Action(ActionType.HOLD)),
                4 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(7, -6))),
            ),
            "min" to mapOf(
                1 to
                    listOf(
                        Choice.Action(ActionType.PRIMARY),
                        Choice.Space(Hex(8, -10)),
                        Choice.Space(Hex(6, -9)),
                        Choice.Space(Hex(7, -9)),
                        Choice.Space(Hex(7, -9)),
                    ),
                2 to
                    listOf(
                        Choice.Action(ActionType.MOVEMENT),
                        Choice.Space(Hex(7, -6)),
                        Choice.Space(Hex(8, -7)),
                        Choice.Space(Hex(9, -7)),
                        Choice.Space(Hex(10, -7)),
                        Choice.Decline("Stop here"),
                    ),
                3 to listOf(Choice.Action(ActionType.PRIMARY)),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r33",
        number = 6,
        title = "A Tree Next to It",
        difficulty = 1,
        theme = "Minion Battle arithmetic, Trees placed for a later shot",
        heroes = listOf("mortimer", "wuk", "brogan", "min"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_ROUND, "Push the lane by the end of the round."),
        intro = "Round 5. You play Mortimer and Wuk for Blue. Nature's Guardian targets a hero next to Wuk, or a unit" +
            " in range next to a Tree token. Min will take a Blue minion in turn 3, and Red has a melee and a " +
            "heavy minion left in the Battle Zone.",
        nudges = R33_NUDGES.first(),
        nudgesByTurn = R33_NUDGES,
        explanation = "This is one way through, and other plans win too: the Trees needn't be placed in turn 3." +
            "\n\n" +
            "Turn 1: Mighty Throw's Movement takes Wuk 2 spaces. Mortimer holds with Knife of the Living Dead. " +
            "Min places Mines and Brogan's card pays Min 2 coins." +
            "\n\n" +
            "Turn 2: Awaken! places Mortimer's Zombie tokens again. Tree Slam's Movement takes Wuk 1 space." +
            "\n\n" +
            "Turn 3: Min defeats a Blue melee minion. Mystic Saplings takes Trees from the board and places them " +
            "in radius, one of them next to the Red melee minion." +
            "\n\n" +
            "Turn 4: Nature's Guardian attacks the Red melee minion at 6 and defeats it. In the Minion Battle Red" +
            " has 1 and Blue 3: Red removes its heavy, its last minion, and Blue pushes the lane (p.8).",
        rules = listOf(
            "p.8 Minion Battle, Push the Lane",
            "p.19 Tokens",
            "p.9 Range and Radius",
        ),
        known = known.display(
            mapOf(
                "brogan" to mapOf(
                    1 to "Performs its primary action.",
                    2 to "Performs its primary action.",
                    3 to "Holds.",
                    4 to "Moves to the space outlined.",
                ),
                "min" to mapOf(
                    1 to "Places Mines on the spaces outlined.",
                    2 to "Moves along the spaces outlined.",
                    3 to "Attacks a unit in its reach.",
                    4 to "Holds.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("mortimer", "wuk")

    override fun PositionBuilder.setUp() {
        round = 5
        tieBreaker = Team.BLUE
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        piece(
            PieceState(
                "t18", "token_zombie", "Zombie",
                Hex(
                    4,
                    -11,
                ),
                "mortimer", Team.BLUE, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t50", "token_tree", "Tree",
                Hex(
                    3,
                    -1,
                ),
                "wuk", Team.BLUE, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t51", "token_tree", "Tree",
                Hex(
                    3,
                    -2,
                ),
                "wuk", Team.BLUE, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t54", "token_zombie", "Zombie",
                Hex(
                    7,
                    -10,
                ),
                "mortimer", Team.BLUE, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t55", "token_zombie", "Zombie",
                Hex(
                    8,
                    -12,
                ),
                "mortimer", Team.BLUE, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t56", "token_zombie", "Zombie",
                Hex(
                    5,
                    -8,
                ),
                "mortimer", Team.BLUE, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        hero("brogan", Team.RED, Hex(8, -6)) {
            level = 5
            coins = 1
            hand("brogan-gold", "brogan-silver", "brogan-red-2a", "brogan-blue-2b", "brogan-green-3b")
            items("brogan-red-2b", "brogan-green-2a", "brogan-blue-2a", "brogan-green-3a")
            deck("brogan-red-1", "brogan-green-1", "brogan-blue-1")
        }
        hero("min", Team.RED, Hex(7, -7)) {
            level = 3
            coins = 3
            hand("min-gold", "min-silver", "min-red-1", "min-green-2b", "min-blue-2a")
            items("min-green-2a", "min-blue-2b")
            deck("min-green-1", "min-blue-1")
        }
        hero("mortimer", Team.BLUE, Hex(7, -11)) {
            level = 4
            coins = 2
            hand("mortimer-gold", "mortimer-silver", "mortimer-red-2b", "mortimer-green-2a", "mortimer-blue-2b")
            items("mortimer-red-2a", "mortimer-green-2b", "mortimer-blue-2a")
            deck("mortimer-red-1", "mortimer-green-1", "mortimer-blue-1")
        }
        hero("wuk", Team.BLUE, Hex(4, -5)) {
            level = 4
            coins = 1
            hand("wuk-gold", "wuk-silver", "wuk-red-2a", "wuk-green-2a", "wuk-blue-2b")
            items("wuk-red-2b", "wuk-green-2b", "wuk-blue-2a")
            deck("wuk-red-1", "wuk-green-1", "wuk-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -11))
        minion(Team.RED, MinionType.MELEE, Hex(8, -9))
        minion(Team.BLUE, MinionType.RANGED, Hex(9, -9))
        minion(Team.RED, MinionType.HEAVY, Hex(8, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(9, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(11, -8))
    }

    override val solution = listOf(
        card("mortimer-gold"),
        card("wuk-green-2a"),
        action(ActionType.HOLD),
        action(ActionType.MOVEMENT),
        space(5, -7),
        card("mortimer-silver"),
        card("wuk-gold"),
        action(ActionType.MOVEMENT),
        space(6, -8),
        action(ActionType.PRIMARY),
        tokenAt(4, -11),
        space(6, -10),
        tokenAt(7, -10),
        space(7, -10),
        tokenAt(8, -12),
        space(8, -12),
        tokenAt(5, -8),
        space(5, -8),
        card("mortimer-red-2b"),
        card("wuk-silver"),
        action(ActionType.MOVEMENT),
        action(ActionType.PRIMARY),
        space(9, -11),
        tokenAt(3, -1),
        space(9, -10),
        tokenAt(3, -2),
        space(4, -9),
        card("mortimer-green-2a"),
        card("wuk-red-2a"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        option(0),
        tokenAt(6, -10),
        space(5, -9),
        option(0),
        tokenAt(5, -9),
        space(6, -10),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("mortimer-blue-2b"),
                card("wuk-red-2a"),
                action(ActionType.PRIMARY),
                option(0),
                tokenAt(8, -12),
                space(9, -12),
                option(0),
                tokenAt(4, -11),
                decline(),
                action(ActionType.MOVEMENT),
                space(4, -4),
                space(5, -5),
                space(6, -6),
                space(6, -7),
                card("mortimer-silver"),
                card("wuk-silver"),
                action(ActionType.PRIMARY),
                tokenAt(7, -10),
                space(6, -10),
                tokenAt(9, -12),
                space(8, -12),
                tokenAt(4, -11),
                tokenAt(6, -10),
                action(ActionType.PRIMARY),
                space(7, -8),
                tokenAt(3, -1),
                space(6, -6),
                decline(),
                card("mortimer-green-2a"),
                card("wuk-green-2a"),
                action(ActionType.PRIMARY),
                decline(),
                action(ActionType.HOLD),
                card("mortimer-gold"),
                card("wuk-gold"),
                action(ActionType.CLEAR),
                decline(),
                action(ActionType.MOVEMENT),
                decline(),
            ),
        ),
    )
}

private val R33_NUDGES = listOf(
    listOf(
        "Count the battle: Blue has one to lose, and Red two. What has to be true for Red to lose them " +
            "both?",
        "Wuk has to come up. Mortimer can keep his cards for now.",
        "Play Knife of the Living Dead on Mortimer, who holds, and Mighty Throw on Wuk, moving 2 spaces.",
    ),
    listOf(
        "Wuk is on his way. Mortimer's Zombies are already on the board.",
        "Awaken! places Zombie tokens again. Tree Slam's Movement takes Wuk 1 space further.",
        "Play Awaken! on Mortimer and Tree Slam on Wuk, moving 1 space.",
    ),
    listOf(
        "Wuk's last attack needs a Tree next to the Red melee minion.",
        "Mystic Saplings places Trees in radius. Placing none is proven to lose.",
        "Play Braains...! on Mortimer, who moves, and Mystic Saplings on Wuk, placing a Tree next to the " +
            "Red melee minion.",
    ),
    listOf(
        "The Red melee minion is next to a Tree. Which of Wuk's texts reaches it?",
        "Nature's Guardian targets it from range. Claim Dominance is proven to lose.",
        "Play Crowd Drift on Mortimer and Nature's Guardian on Wuk, attacking the Red melee minion.",
    ),
)
