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

/** Full round 21, mined from self-play (g9416_r3_BLUE_coins-7; ADR-052, ENCODING.md "Full rounds"). */
object R36SevenCoins : Puzzle() {
    override val concept = "R36"

    override val known = KnownPlays(
        cards = mapOf(
            "emmitt" to listOf("emmitt-blue-1", "emmitt-green-1", "emmitt-red-2a", "emmitt-silver"),
            "silverarrow" to
                listOf("silverarrow-gold", "silverarrow-silver", "silverarrow-green-2a", "silverarrow-blue-2b"),
        ),
        actions = mapOf(
            "emmitt" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Decline("Don't move")),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -8))),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(2, -8))),
            ),
            "silverarrow" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY)),
                2 to listOf(Choice.Action(ActionType.HOLD)),
                3 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r36",
        number = 21,
        title = "Seven Coins",
        difficulty = 2,
        theme = "Coins counted to the last turn",
        heroes = listOf("dodger", "snorri", "emmitt", "silverarrow"),
        goal = PuzzleGoal.GainCoins(7, Deadline.END_OF_ROUND, "Gain 7 coins by the end of the round."),
        intro = "Round 3. You play Dodger and Snorri for Blue. Melee and ranged minions pay 2 coins here, and " +
            "Emmitt pays 2 to the " +
            "hero who defeats him and 1 to the other. Dark Ritual gains 1 coin if there are 2 or more empty spawn" +
            " points in its radius in the Battle Zone.",
        nudges = R36_NUDGES.first(),
        nudgesByTurn = R36_NUDGES,
        explanation =
            "This is one way through, and other plans win too: neither Runic Hammer nor defeating Emmitt is " +
                "needed for all of them." +
                "\n\n" +
                "Turn 1: Silverarrow defeats a Blue melee minion. Rune Sigils defeats a Red melee minion: 2 coins. " +
                "Dark Ritual gains Dodger 1 coin." +
                "\n\n" +
                "Turn 2: Dread Razor's Movement takes Dodger 1 space, and Snorri places his runes with Inscribe the " +
                "Runes, the Horn below turn 4 on this line: Runic Hammer's move needs it." +
                "\n\n" +
                "Turn 3: Safe Passage's Movement takes Snorri 3 spaces, and Dodger plays Death Trap." +
                "\n\n" +
                "Turn 4: Burning Skull attacks Emmitt at 3. Reverse Time gives 2, +1 for the item, -1 for the " +
                "adjacent enemy heavy minion, makes 2, not enough, and he is defeated (p.13): 2 coins for Dodger and " +
                "1 for Snorri's assist. Runic Hammer moves Snorri 1 space and defeats a Red melee minion at 5: 2 " +
                "more, and the goal is passed.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense, Minion Defense Modifiers",
            "p.9 Range and Radius",
        ),
        known = known.display(
            mapOf(
                "emmitt" to mapOf(
                    1 to "Takes its Movement and stays where it is.",
                    2 to "Moves to the space outlined.",
                    3 to "Moves to the space outlined.",
                ),
                "silverarrow" to mapOf(
                    1 to "Attacks a unit at maximum range.",
                    2 to "Holds.",
                    3 to "Holds.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("dodger", "snorri")

    override fun PositionBuilder.setUp() {
        round = 3
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 3)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        hero("emmitt", Team.RED, Hex(5, -9)) {
            level = 2
            hand("emmitt-gold", "emmitt-silver", "emmitt-green-1", "emmitt-blue-1", "emmitt-red-2a")
            items("emmitt-red-2b")
            deck("emmitt-red-1")
        }
        hero("silverarrow", Team.RED, Hex(4, -2)) {
            level = 3
            coins = 1
            hand(
                "silverarrow-gold",
                "silverarrow-silver",
                "silverarrow-red-1",
                "silverarrow-green-2a",
                "silverarrow-blue-2b",
            )
            items("silverarrow-green-2b", "silverarrow-blue-2a")
            deck("silverarrow-green-1", "silverarrow-blue-1")
        }
        hero("dodger", Team.BLUE, Hex(3, -6)) {
            level = 3
            hand("dodger-gold", "dodger-silver", "dodger-green-1", "dodger-red-2b", "dodger-blue-2b")
            items("dodger-red-2a", "dodger-blue-2a")
            deck("dodger-red-1", "dodger-blue-1")
        }
        hero("snorri", Team.BLUE, Hex(1, -5)) {
            level = 3
            hand("snorri-gold", "snorri-silver", "snorri-blue-1", "snorri-red-2a", "snorri-green-2b")
            items("snorri-red-2b", "snorri-green-2a")
            deck("snorri-red-1", "snorri-green-1")
            marker("marker_rune_bird", "snorri", true, mapOf("slot" to "1"))
            marker("marker_rune_axe", "snorri", true, mapOf("slot" to "2"))
            marker("marker_rune_horn", "snorri", true, mapOf("slot" to "3"))
            marker("marker_rune_anvil", "snorri", true, mapOf("slot" to "4"))
        }
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
    }

    override val solution = listOf(
        card("dodger-green-1"),
        card("snorri-gold"),
        action(ActionType.PRIMARY),
        unitAt(0, -6),
        action(ActionType.PRIMARY),
        card("dodger-gold"),
        card("snorri-silver"),
        action(ActionType.MOVEMENT),
        space(3, -7),
        action(ActionType.PRIMARY),
        option(0),
        option(0),
        option(0),
        card("dodger-silver"),
        card("snorri-blue-1"),
        action(ActionType.PRIMARY),
        space(0, -7),
        action(ActionType.HOLD),
        card("dodger-red-2b"),
        card("snorri-red-2a"),
        action(ActionType.PRIMARY),
        hero("emmitt"),
        unitAt(2, -7),
        space(2, -8),
        action(ActionType.PRIMARY),
        space(1, -7),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("dodger-silver"),
                card("snorri-gold"),
                action(ActionType.PRIMARY),
                unitAt(0, -6),
                action(ActionType.PRIMARY),
                card("dodger-green-1"),
                card("snorri-green-2b"),
                action(ActionType.MOVEMENT),
                space(3, -7),
                action(ActionType.MOVEMENT),
                space(2, -6),
                card("dodger-gold"),
                card("snorri-blue-1"),
                action(ActionType.HOLD),
                action(ActionType.HOLD),
                card("dodger-red-2b"),
                card("snorri-silver"),
                action(ActionType.PRIMARY),
                hero("emmitt"),
                decline(),
                action(ActionType.PRIMARY),
                option(1),
                option(2),
                option(1),
            ),
        ),
    )
}

private val R36_NUDGES = listOf(
    listOf(
        "On this line, add it up: minions, a hero, an assist, and the coin from Dark Ritual.",
        "Snorri can reach a Red melee minion now, and Dodger's ritual pays at once.",
        "Play Dark Ritual on Dodger and Rune Sigils on Snorri, attacking the Red melee minion.",
    ),
    listOf(
        "Nobody attacks this turn on this line. Dodger has to step, and Snorri's runes go down.",
        "Dread Razor's Movement takes Dodger 1 space. Inscribe the Runes places the runes below his turn " +
            "slots.",
        "Play Dread Razor on Dodger, moving 1 space, and Inscribe the Runes on Snorri.",
    ),
    listOf(
        "Where must Snorri stand for his last attack? About half of Safe Passage's spaces are proven to " +
            "lose.",
        "Keep Burning Skull and Runic Hammer for next turn.",
        "Play Death Trap on Dodger and Safe Passage on Snorri, moving 3 spaces.",
    ),
    listOf(
        "Two attacks: one at Emmitt, one at a minion.",
        "Burning Skull targets a unit in range. Runic Hammer may move Snorri 1 space first, and only two " +
            "of those moves win.",
        "Play Burning Skull on Dodger, attacking Emmitt, and Runic Hammer on Snorri, moving and attacking" +
            " the Red melee minion.",
    ),
)
