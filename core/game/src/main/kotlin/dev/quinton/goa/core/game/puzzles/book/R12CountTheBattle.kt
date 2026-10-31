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
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 13, mined from self-play (g9086_r6_BLUE_push; ADR-052, ENCODING.md "Full rounds"). */
object R12CountTheBattle : Puzzle() {
    override val concept = "R12"

    override val known = KnownPlays(
        cards = mapOf(
            "silverarrow" to
                listOf("silverarrow-green-2a", "silverarrow-red-2a", "silverarrow-blue-3a", "silverarrow-gold"),
            "snorri" to listOf("snorri-blue-2b", "snorri-green-2a", "snorri-red-2b", "snorri-gold"),
        ),
        actions = mapOf(
            "silverarrow" to mapOf(
                1 to listOf(Choice.Action(ActionType.HOLD)),
                2 to
                    listOf(
                        Choice.Action(ActionType.PRIMARY),
                        Choice.Option(1, "Target a unit adjacent to you"),
                        Choice.Unit("m1009"),
                    ),
                3 to listOf(Choice.Action(ActionType.HOLD)),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
            "snorri" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(4, -8))),
                2 to listOf(Choice.Action(ActionType.FAST_TRAVEL), Choice.Space(Hex(5, -11))),
                3 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Unit("xargatha"), Choice.Space(Hex(4, -11))),
                4 to listOf(Choice.Action(ActionType.FAST_TRAVEL), Choice.Space(Hex(5, -9))),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r12",
        number = 13,
        title = "Count the Battle",
        difficulty = 1,
        theme = "Minion Battle arithmetic, a minion pulled into reach",
        heroes = listOf("xargatha", "mrak", "silverarrow", "snorri"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_ROUND, "Push the lane by the end of the round."),
        intro = "Round 6. You play Xargatha and Mrak for Blue. The Battle Zone is Lane Zone 3, and both of you start " +
            "a Zone away. Snorri has a long shot in turn 3, and Silverarrow takes a Blue minion in turn 2.",
        nudges = R12_NUDGES.first(),
        nudgesByTurn = R12_NUDGES,
        explanation =
            "This is one way through, and other plans win too; Siren's Call is proven not to be needed. Every" +
                " plan we found comes down to the count: the Minion Battle has to leave Red with nothing." +
                "\n\n" +
                "Turn 1: Petrifying Stare's and Seismic Assault's Movement take Xargatha and Mrak into Lane Zone 3, " +
                "side by side next to a Red melee minion." +
                "\n\n" +
                "Turn 2: Cleave defeats that Red melee minion (it has no second target to repeat on). Silverarrow " +
                "defeats a Blue melee minion, as planned. Fissure's Movement takes Mrak next to a Blue melee minion." +
                "\n\n" +
                "Turn 3: Snorri shoots at maximum range: at Mrak on this line, at 6. Treacherous Ground defends: 4, " +
                "+1 for the item, +1 for the adjacent Blue melee minion, makes 6 (p.14). Boulder Blitz, or not " +
                "defending at all, wins as well. Siren's Call moves the other Red melee minion next to Xargatha." +
                "\n\n" +
                "Turn 4: Long Thrust attacks it at 5 and defeats it. In the Minion Battle Red has 2 and Blue 5: Red " +
                "has to remove 3, more than it has, so both go and Blue pushes the lane (p.8).",
        rules = listOf(
            "p.8 Minion Battle, Push the Lane",
            "p.14 Defense, Minion Defense Modifiers",
            "p.9 Range",
        ),
        known = known.display(
            mapOf(
                "silverarrow" to mapOf(
                    1 to "Holds.",
                    2 to "Attacks the Blue melee minion outlined.",
                    3 to "Holds.",
                    4 to "Holds.",
                ),
                "snorri" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Fast travels to the space outlined.",
                    3 to "Attacks Xargatha if she is at maximum range, otherwise a unit of its choice there, then " +
                        "moves to the space outlined.",
                    4 to "Fast travels to the space outlined.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("xargatha", "mrak")

    override fun PositionBuilder.setUp() {
        round = 6
        tieBreaker = Team.BLUE
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 4)
        hero("silverarrow", Team.RED, Hex(8, -5)) {
            level = 5
            coins = 1
            hand(
                "silverarrow-gold",
                "silverarrow-silver",
                "silverarrow-red-2a",
                "silverarrow-green-2a",
                "silverarrow-blue-3a",
            )
            items("silverarrow-red-2b", "silverarrow-green-2b", "silverarrow-blue-2b", "silverarrow-blue-3b")
            deck("silverarrow-red-1", "silverarrow-green-1", "silverarrow-blue-1")
        }
        hero("snorri", Team.RED, Hex(5, -11)) {
            level = 4
            coins = 1
            hand("snorri-gold", "snorri-silver", "snorri-red-2b", "snorri-green-2a", "snorri-blue-2b")
            items("snorri-red-2a", "snorri-green-2b", "snorri-blue-2a")
            deck("snorri-red-1", "snorri-green-1", "snorri-blue-1")
            marker("marker_rune_axe", "snorri", true, mapOf("slot" to "1"))
            marker("marker_rune_anvil", "snorri", true, mapOf("slot" to "2"))
            marker("marker_rune_horn", "snorri", true, mapOf("slot" to "3"))
            marker("marker_rune_bird", "snorri", true, mapOf("slot" to "4"))
        }
        hero("xargatha", Team.BLUE, Hex(5, -9)) {
            level = 5
            hand("xargatha-gold", "xargatha-silver", "xargatha-red-2b", "xargatha-blue-2a", "xargatha-green-3b")
            items("xargatha-red-2a", "xargatha-green-2b", "xargatha-blue-2b", "xargatha-green-3a")
            deck("xargatha-red-1", "xargatha-green-1", "xargatha-blue-1")
        }
        hero("mrak", Team.BLUE, Hex(4, -7)) {
            level = 3
            coins = 1
            hand("mrak-gold", "mrak-silver", "mrak-green-1", "mrak-red-2a", "mrak-blue-2a")
            items("mrak-red-2b", "mrak-blue-2b")
            deck("mrak-red-1", "mrak-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -11))
        minion(Team.RED, MinionType.MELEE, Hex(7, -10))
        minion(Team.RED, MinionType.MELEE, Hex(8, -9))
        minion(Team.BLUE, MinionType.RANGED, Hex(9, -9))
        minion(Team.RED, MinionType.HEAVY, Hex(8, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(9, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(11, -8))
        minion(Team.RED, MinionType.RANGED, Hex(6, -7))
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(7, -5))
    }

    override val solution = listOf(
        card("xargatha-blue-2a"),
        card("mrak-red-2a"),
        action(ActionType.MOVEMENT),
        space(7, -11),
        action(ActionType.MOVEMENT),
        space(6, -10),
        card("xargatha-gold"),
        card("mrak-gold"),
        action(ActionType.PRIMARY),
        yes(),
        action(ActionType.MOVEMENT),
        space(7, -10),
        card("xargatha-silver"),
        card("mrak-silver"),
        action(ActionType.HOLD),
        card("mrak-green-1"),
        action(ActionType.PRIMARY),
        unitAt(8, -9),
        card("xargatha-red-2b"),
        card("mrak-blue-2a"),
        action(ActionType.PRIMARY),
        unitAt(6, -10),
        action(ActionType.MOVEMENT),
        space(6, -10),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("xargatha-green-3b"),
                card("mrak-green-1"),
                hero("mrak"),
                action(ActionType.MOVEMENT),
                space(6, -9),
                action(ActionType.MOVEMENT),
                space(7, -11),
                card("xargatha-silver"),
                card("mrak-red-2a"),
                action(ActionType.ATTACK),
                action(ActionType.PRIMARY),
                unitAt(8, -9),
                space(6, -10),
                card("xargatha-blue-2a"),
                card("mrak-blue-2a"),
                action(ActionType.MOVEMENT),
                space(7, -8),
                action(ActionType.MOVEMENT),
                space(6, -8),
                card("xargatha-gold"),
                card("mrak-silver"),
                action(ActionType.HOLD),
                action(ActionType.HOLD),
            ),
        ),
    )
}

private val R12_NUDGES = listOf(
    listOf(
        "The fight is in Lane Zone 3. Who can you reach next turn from there?",
        "On this line both your heroes use their Movement this turn.",
        "Play Petrifying Stare on Xargatha and Seismic Assault on Mrak, moving both into Lane Zone 3, " +
            "next to the Red melee minion.",
    ),
    listOf(
        "You are there now. One Red minion falls this turn. How many must be gone before the battle?",
        "Cleave can reach a minion next to Xargatha. Mrak only has to step towards the Blue melee minion.",
        "Play Cleave on Xargatha, attacking the Red melee minion, and Fissure on Mrak, who moves.",
    ),
    listOf(
        "There is a Red melee minion Xargatha can't reach. Yet. Snorri's shot needn't worry you: any " +
            "defense of Mrak's, or none, still wins.",
        "Siren's Call moves an enemy unit next to Xargatha. Choose the minion: choosing Snorri is proven " +
            "to lose.",
        "Play Siren's Call on Xargatha, moving the Red melee minion to her, and Stone Grip on Mrak.",
    ),
    listOf(
        "The Red melee minion is next to Xargatha now. What is left to do before the battle?",
        "Long Thrust attacks a unit in range at 5. Count the battle after that.",
        "Play Long Thrust on Xargatha, attacking the Red melee minion, and on this line Boulder Blitz on Mrak.",
    ),
)
