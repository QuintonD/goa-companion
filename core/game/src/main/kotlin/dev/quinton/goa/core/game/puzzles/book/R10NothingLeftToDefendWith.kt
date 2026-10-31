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
import dev.quinton.goa.core.game.puzzles.Moves.number
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 9, mined from self-play (g4536_r8_RED_win; ADR-052, ENCODING.md "Full rounds"). */
object R10NothingLeftToDefendWith : Puzzle() {
    override val concept = "R10"

    override val known = KnownPlays(
        cards = mapOf(
            "widget" to listOf("widget-green-2b", "widget-gold", "widget-blue-3a", "widget-red-2a"),
            "nebkher" to listOf("nebkher-silver", "nebkher-gold", "nebkher-green-3a", "nebkher-blue-2b"),
        ),
        actions = mapOf(
            "widget" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(1, -2))),
                2 to listOf(Choice.Action(ActionType.PRIMARY)),
                3 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(0, 1))),
            ),
            "nebkher" to mapOf(
                1 to
                    listOf(
                        Choice.Action(ActionType.PRIMARY),
                        Choice.Option(1, "Place an Illusion token in an adjacent space"),
                        Choice.Space(Hex(5, -4)),
                        Choice.Option(0, "Swap with an Illusion token in radius"),
                        Choice.Option(1, "Place an Illusion token in an adjacent space"),
                        Choice.Space(Hex(5, -5)),
                    ),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(5, -3))),
                3 to listOf(Choice.Action(ActionType.FAST_TRAVEL), Choice.Space(Hex(5, -4))),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r10",
        number = 9,
        title = "Nothing Left to Defend With",
        difficulty = 1,
        theme = "Making a hero spend its spare card",
        heroes = listOf("misa", "wasp", "widget", "nebkher"),
        goal = PuzzleGoal.WinGame("Win the game this round.", Deadline.END_OF_ROUND),
        intro = "Round 8. You play Misa and Wasp for Red. Blue has 2 Life counters left, and defeating Widget costs " +
            "Blue both of them. Widget holds five cards and will play four: one is spare, and a hero defends by " +
            "discarding a card.",
        nudges = R10_NUDGES.first(),
        nudgesByTurn = R10_NUDGES,
        explanation =
            "Turn 1: Electrocute attacks Widget for 7, and it has to spend a card to stop it (p.14): Dragon Bond " +
                "or Diversionary Attack defends, anything less and Widget is defeated at once, which ends the game." +
                "\n\n" +
                "Turn 2: Widget defeats the Red melee minion. Swoop In puts Misa next to Widget and the Blue melee " +
                "minion (it needs two or more enemy units next to the space), while Wasp plays Static Barrier." +
                "\n\n" +
                "Turn 3: Widget's Safe Landing moves Pyro and swaps with it, so you know where it will end. Dash and " +
                "Slash takes Misa one space, next to that space: most of its other spaces are proven to lose." +
                "\n\n" +
                "Turn 4: Misa acts first and attacks with Worthy Opponent for 7. On this line Widget has no card to " +
                "defend with and is defeated (p.13). Its own card is resolved, without performing an action, and Blue" +
                " spends its last 2 Life counters: the game is over.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense",
            "p.7 Check the initiative",
        ),
        known = known.display(
            mapOf(
                "widget" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Attacks a unit in its reach.",
                    3 to "Moves Pyro to the space outlined, then swaps places with Pyro.",
                ),
                "nebkher" to mapOf(
                    1 to "Places Illusions on the spaces outlined and swaps with one.",
                    2 to "Moves to the space outlined.",
                    3 to "Fast travels to the space outlined.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("misa", "wasp")

    override fun PositionBuilder.setUp() {
        round = 8
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 1)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 2)
        piece(
            PieceState(
                "t33", "pyro", "Pyro",
                Hex(
                    -1,
                    1,
                ),
                "widget", Team.BLUE, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        hero("misa", Team.RED, Hex(4, -1)) {
            level = 6
            coins = 6
            hand("misa-gold", "misa-silver", "misa-blue-2b", "misa-red-3a", "misa-green-3b")
            items("misa-red-2b", "misa-green-2a", "misa-blue-2a", "misa-red-3b", "misa-green-3a")
            deck("misa-red-1", "misa-green-1", "misa-blue-1")
        }
        hero("wasp", Team.RED, Hex(-1, -3)) {
            level = 5
            coins = 3
            hand("wasp-gold", "wasp-silver", "wasp-red-2a", "wasp-green-2a", "wasp-blue-3b")
            items("wasp-red-2b", "wasp-green-2b", "wasp-blue-2b", "wasp-blue-3a")
            deck("wasp-red-1", "wasp-green-1", "wasp-blue-1")
        }
        hero("widget", Team.BLUE, Hex(-1, -2)) {
            level = 5
            coins = 5
            hand("widget-gold", "widget-silver", "widget-red-2a", "widget-green-2b", "widget-blue-3a")
            items("widget-red-2b", "widget-green-2a", "widget-blue-2a", "widget-blue-3b")
            deck("widget-red-1", "widget-green-1", "widget-blue-1")
        }
        hero("nebkher", Team.BLUE, Hex(4, -4)) {
            level = 5
            coins = 4
            hand("nebkher-gold", "nebkher-silver", "nebkher-red-2a", "nebkher-blue-2b", "nebkher-green-3a")
            items("nebkher-red-2b", "nebkher-green-2b", "nebkher-blue-2a", "nebkher-green-3b")
            deck("nebkher-red-1", "nebkher-green-1", "nebkher-blue-1")
        }
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
    }

    override val solution = listOf(
        card("misa-gold"),
        card("wasp-red-2a"),
        action(ActionType.MOVEMENT),
        space(4, -2),
        action(ActionType.PRIMARY),
        card("misa-silver"),
        card("wasp-silver"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        space(1, -1),
        card("misa-blue-2b"),
        card("wasp-gold"),
        hero("misa"),
        action(ActionType.PRIMARY),
        space(0, 0),
        action(ActionType.MOVEMENT),
        space(-1, -4),
        card("misa-red-3a"),
        card("wasp-blue-3b"),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("misa-green-3b"),
                card("wasp-blue-3b"),
                action(ActionType.PRIMARY),
                hero("widget"),
                number(3),
                action(ActionType.MOVEMENT),
                space(2, 0),
                card("misa-silver"),
                card("wasp-green-2a"),
                action(ActionType.PRIMARY),
                space(1, -1),
                action(ActionType.MOVEMENT),
                space(1, -5),
                card("misa-red-3a"),
                card("wasp-red-2a"),
                action(ActionType.PRIMARY),
                unitAt(2, -1),
                action(ActionType.HOLD),
                card("misa-blue-2b"),
                card("wasp-gold"),
                hero("misa"),
                action(ActionType.MOVEMENT),
                decline(),
                action(ActionType.MOVEMENT),
                space(1, -6),
            ),
        ),
    )
}

private val R10_NUDGES = listOf(
    listOf(
        "Widget has five cards and plays four. What will it have left in turn 4?",
        "A defense costs a card (p.14). Attack Widget now and it has to spend one, or fall.",
        "Play Watch How I Soar on Misa and Electrocute on Wasp, and attack Widget with Electrocute.",
    ),
    listOf(
        "Widget's next two plans are known. Where do you need Misa standing by turn 4?",
        "Swoop In places Misa next to two or more enemy units. Look at who will be standing where.",
        "Play Swoop In on Misa, into the space next to Widget and the Blue melee minion, and Static " +
            "Barrier on Wasp.",
    ),
    listOf(
        "Widget's plan this turn ends on the outlined space. Be next to it.",
        "Misa only needs one space, and Dash and Slash can stop after one. Most of its spaces are proven " +
            "to lose.",
        "Play Dash and Slash on Misa, one space along, next to the Red heavy minion, and Magnetic Dagger " +
            "on Wasp, who just moves.",
    ),
    listOf(
        "Look at Widget's hand, and at who acts first.",
        "Misa acts before Widget in this turn. Her attack is 7.",
        "Play Worthy Opponent on Misa and Kinetic Blast on Wasp, and attack Widget with Worthy Opponent.",
    ),
)
