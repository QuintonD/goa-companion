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
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 11, mined from self-play (g4536_r7_RED_coins-8; ADR-052, ENCODING.md "Full rounds"). */
object R11SwapTheMinionNextDoor : Puzzle() {
    override val concept = "R11"

    override val known = KnownPlays(
        cards = mapOf(
            "widget" to listOf("widget-blue-3a", "widget-red-2a", "widget-gold", "widget-silver"),
            "nebkher" to listOf("nebkher-blue-2b", "nebkher-gold", "nebkher-silver", "nebkher-red-2a"),
        ),
        actions = mapOf(
            "widget" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(-1, -6))),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(-1, -2))),
                3 to listOf(Choice.Action(ActionType.PRIMARY)),
            ),
            "nebkher" to mapOf(
                1 to listOf(Choice.Action(ActionType.FAST_TRAVEL), Choice.Space(Hex(7, -7))),
                2 to listOf(Choice.Action(ActionType.FAST_TRAVEL), Choice.Space(Hex(5, -4))),
                3 to
                    listOf(
                        Choice.Action(ActionType.PRIMARY),
                        Choice.Option(1, "Place an Illusion token in an adjacent space"),
                        Choice.Space(Hex(4, -4)),
                        Choice.Option(0, "Swap with an Illusion token in radius"),
                        Choice.Option(2, "Swap two resolved cards of an enemy hero in radius"),
                        Choice.Unit("wasp"),
                        Choice.Option(1, "Turn slot 1: Kinetic Blast"),
                    ),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r11",
        number = 11,
        title = "Swap the Minion Next Door",
        difficulty = 2,
        theme = "Moving a minion to you with Storm Spirit, the big coin at the end",
        heroes = listOf("misa", "wasp", "widget", "nebkher"),
        goal = PuzzleGoal.GainCoins(8, Deadline.END_OF_ROUND, "Gain 8 coins by the end of the round."),
        intro = "Round 7. You play Misa and Wasp for Red. A minion pays 2 coins here. NebKher pays 5 to the hero who " +
            "defeats him and 2 to the other, and Blue has 2 Life counters left. NebKher plays four of his five " +
            "cards, and his best defense in turn 4 will be 5.",
        nudges = R11_NUDGES.first(),
        nudgesByTurn = R11_NUDGES,
        explanation =
            "Turn 1: Storm Spirit swaps Wasp with the Blue ranged minion, which brings the two next to each other" +
                " in new places. NebKher and Widget move as planned." +
                "\n\n" +
                "Turn 2: Magnetic Dagger defeats the Blue ranged minion: Wasp's first 2 coins. Watch How I Soar " +
                "places Misa 4 spaces away." +
                "\n\n" +
                "Turn 3: Widget defeats the Red ranged minion. NebKher places an Illusion next to Wasp and swaps with" +
                " it, so he comes to her. Then he swaps two of Wasp's resolved cards. Swoop In and Static Barrier are" +
                " the cards you can spare." +
                "\n\n" +
                "Turn 4: Electrocute attacks NebKher at 7. Master of Illusions gives 5, not enough, and he is " +
                "defeated before his card resolves (p.13): 5 coins for Wasp and 2 for Misa, 9 in all. Blue spends its" +
                " last 2 Life counters too." +
                "\n\n" +
                "The computer at strength 7, playing your heroes, doesn't find it. Strength 10 does.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense",
            "p.18 Place",
        ),
        known = known.display(
            mapOf(
                "widget" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Moves to the space outlined, next to the Red ranged minion.",
                    3 to "Attacks the Red ranged minion.",
                ),
                "nebkher" to mapOf(
                    1 to "Fast travels to the space outlined.",
                    2 to "Fast travels to the space outlined.",
                    3 to "Diabolical Laughter: an Illusion next to Wasp, swaps with it, and swaps two of Wasp's " +
                        "resolved cards.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("misa", "wasp")

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
                "t33", "pyro", "Pyro",
                Hex(
                    5,
                    -3,
                ),
                "widget", Team.BLUE, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        hero("misa", Team.RED, Hex(4, -5)) {
            level = 6
            coins = 1
            hand("misa-gold", "misa-silver", "misa-blue-2b", "misa-red-3a", "misa-green-3b")
            items("misa-red-2b", "misa-green-2a", "misa-blue-2a", "misa-red-3b", "misa-green-3a")
            deck("misa-red-1", "misa-green-1", "misa-blue-1")
        }
        hero("wasp", Team.RED, Hex(3, -5)) {
            level = 5
            hand("wasp-gold", "wasp-silver", "wasp-red-2a", "wasp-green-2a", "wasp-blue-3b")
            items("wasp-red-2b", "wasp-green-2b", "wasp-blue-2b", "wasp-blue-3a")
            deck("wasp-red-1", "wasp-green-1", "wasp-blue-1")
        }
        hero("widget", Team.BLUE, Hex(0, -7)) {
            level = 5
            coins = 2
            hand("widget-gold", "widget-silver", "widget-red-2a", "widget-green-2b", "widget-blue-3a")
            items("widget-red-2b", "widget-green-2a", "widget-blue-2a", "widget-blue-3b")
            deck("widget-red-1", "widget-green-1", "widget-blue-1")
        }
        hero("nebkher", Team.BLUE, Hex(5, -5)) {
            level = 5
            coins = 3
            hand("nebkher-gold", "nebkher-silver", "nebkher-red-2a", "nebkher-blue-2b", "nebkher-green-3a")
            items("nebkher-red-2b", "nebkher-green-2b", "nebkher-blue-2a", "nebkher-green-3b")
            deck("nebkher-red-1", "nebkher-green-1", "nebkher-blue-1")
        }
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.RANGED, Hex(3, -4))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
    }

    override val solution = listOf(
        card("misa-green-3b"),
        card("wasp-green-2a"),
        action(ActionType.PRIMARY),
        hero("wasp"),
        action(ActionType.HOLD),
        card("misa-gold"),
        card("wasp-gold"),
        action(ActionType.PRIMARY),
        option(0),
        space(4, -9),
        action(ActionType.PRIMARY),
        card("misa-silver"),
        card("wasp-silver"),
        action(ActionType.PRIMARY),
        action(ActionType.HOLD),
        card("misa-red-3a"),
        card("wasp-red-2a"),
        action(ActionType.MOVEMENT),
        space(3, -11),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "The computer at strength 7 on your heroes",
            listOf(
                card("misa-gold"),
                card("wasp-red-2a"),
                action(ActionType.PRIMARY),
                option(0),
                space(0, -4),
                action(ActionType.PRIMARY),
                card("misa-red-3a"),
                card("wasp-blue-3b"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(2, -2),
                card("misa-green-3b"),
                card("wasp-gold"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(-1, -3),
                card("misa-silver"),
                card("wasp-silver"),
                action(ActionType.HOLD),
                action(ActionType.HOLD),
                card("wasp-green-2a"),
            ),
        ),
    )
}

private val R11_NUDGES = listOf(
    listOf(
        "5 and 2 is 7. Where does the last coin's minion come from?",
        "Wasp can't reach the Blue ranged minion the way things stand. Misa can change that with Storm " +
            "Spirit.",
        "Play Storm Spirit on Misa, swapping Wasp and the Blue ranged minion, and Deflect Projectiles on " +
            "Wasp.",
    ),
    listOf(
        "Wasp has a minion next to her now. Misa needs to be somewhere else by turn 4.",
        "Watch How I Soar places Misa at maximum range.",
        "Play Watch How I Soar on Misa, placing her away, and Magnetic Dagger on Wasp, attacking the Blue" +
            " ranged minion.",
    ),
    listOf(
        "NebKher will come to Wasp this turn on his own. Don't spend your attack.",
        "Keep Electrocute. What else can Wasp afford?",
        "Play Swoop In on Misa and Static Barrier on Wasp.",
    ),
    listOf(
        "NebKher stands next to Wasp. His last spare can't reach 7.",
        "Electrocute acts before his Phantasmal Warrior. Misa only has to move.",
        "Play Worthy Opponent on Misa, who moves, and Electrocute on Wasp, attacking NebKher.",
    ),
)
