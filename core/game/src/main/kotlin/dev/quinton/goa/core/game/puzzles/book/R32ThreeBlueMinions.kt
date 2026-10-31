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
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 10, mined from self-play (g4193_r4_RED_coins-6; ADR-052, ENCODING.md "Full rounds"). */
object R32ThreeBlueMinions : Puzzle() {
    override val concept = "R32"

    override val known = KnownPlays(
        cards = mapOf(
            "brogan" to listOf("brogan-green-2b", "brogan-blue-1", "brogan-red-2b", "brogan-gold"),
            "brynn" to listOf("brynn-green-2a", "brynn-red-1", "brynn-silver", "brynn-gold"),
        ),
        actions = mapOf(
            "brogan" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -1))),
                2 to listOf(Choice.Action(ActionType.FAST_TRAVEL), Choice.Space(Hex(5, -7))),
                3 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Option(0, "Target a unit adjacent to you")),
                4 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(6, -8))),
            ),
            "brynn" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(7, -5))),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Unit("m1008")),
                3 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Decline("Skip")),
                4 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(7, -6))),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r32",
        number = 10,
        title = "Three Blue Minions",
        difficulty = 1,
        theme = "Six coins without touching a hero",
        heroes = listOf("bain", "wasp", "brogan", "brynn"),
        goal = PuzzleGoal.GainCoins(6, Deadline.END_OF_ROUND, "Gain 6 coins by the end of the round."),
        intro = "Round 4. You play Bain and Wasp for Red. Minions pay 2 coins here. Get over here! moves a unit or a " +
            "token in range and in a straight line towards Bain until it is next to him.",
        nudges = R32_NUDGES.first(),
        nudgesByTurn = R32_NUDGES,
        explanation = "This is one way through, and other plans win too: Get over here! is proven not to be needed." +
            "\n\n" +
            "Turn 1: Close Call's Movement takes Bain 2 spaces, and Electrocute's takes Wasp 4." +
            "\n\n" +
            "Turn 2: Magnetic Dagger defeats a Blue melee minion at 5 and Dead or Alive another at 4: 4 coins. On" +
            " this line Bain gives Brynn the Bounty marker. Brynn defeats a Red melee minion." +
            "\n\n" +
            "Turn 3: Bain acts first on the tie. Get over here! moves a Blue melee minion 1 space, next to him. " +
            "Brogan defeats the Red ranged minion." +
            "\n\n" +
            "Turn 4: Hand Crossbow, with its second text, attacks the Blue melee minion next to Bain at 4 and " +
            "defeats it: 6 coins, and the goal is met.",
        rules = listOf(
            "p.8 Minions",
            "p.7 Tied Initiative",
            "p.19 Straight line",
        ),
        known = known.display(
            mapOf(
                "brogan" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Fast travels to the space outlined.",
                    3 to "Attacks a unit next to it.",
                    4 to "Moves to the space outlined.",
                ),
                "brynn" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Attacks the Red melee minion outlined.",
                    3 to "Performs its primary action, and skips what it offers.",
                    4 to "Moves to the space outlined.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("bain", "wasp")

    override fun PositionBuilder.setUp() {
        round = 4
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        hero("bain", Team.RED, Hex(7, -4)) {
            level = 3
            coins = 2
            hand("bain-gold", "bain-silver", "bain-green-1", "bain-red-2b", "bain-blue-2b")
            items("bain-red-2a", "bain-blue-2a")
            deck("bain-red-1", "bain-blue-1")
        }
        hero("wasp", Team.RED, Hex(4, -9)) {
            level = 4
            coins = 1
            hand("wasp-gold", "wasp-silver", "wasp-red-2a", "wasp-green-2a", "wasp-blue-2a")
            items("wasp-red-2b", "wasp-green-2b", "wasp-blue-2b")
            deck("wasp-red-1", "wasp-green-1", "wasp-blue-1")
        }
        hero("brogan", Team.BLUE, Hex(1, 1)) {
            level = 3
            hand("brogan-gold", "brogan-silver", "brogan-blue-1", "brogan-red-2b", "brogan-green-2b")
            items("brogan-red-2a", "brogan-green-2a")
            deck("brogan-red-1", "brogan-green-1")
        }
        hero("brynn", Team.BLUE, Hex(5, -3)) {
            level = 3
            coins = 1
            hand("brynn-gold", "brynn-silver", "brynn-red-1", "brynn-green-2a", "brynn-blue-2b")
            items("brynn-green-2b", "brynn-blue-2a")
            deck("brynn-green-1", "brynn-blue-1")
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
    }

    override val solution = listOf(
        card("bain-green-1"),
        card("wasp-red-2a"),
        action(ActionType.MOVEMENT),
        space(8, -12),
        action(ActionType.MOVEMENT),
        space(9, -6),
        card("bain-gold"),
        card("wasp-gold"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        hero("brynn"),
        card("bain-silver"),
        card("wasp-silver"),
        hero("bain"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        card("bain-red-2b"),
        card("wasp-green-2a"),
        action(ActionType.PRIMARY),
        option(1),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("bain-green-1"),
                card("wasp-blue-2a"),
                action(ActionType.MOVEMENT),
                space(7, -11),
                action(ActionType.HOLD),
                card("bain-silver"),
                card("wasp-gold"),
                action(ActionType.PRIMARY),
                unitAt(11, -8),
                action(ActionType.PRIMARY),
                card("bain-gold"),
                card("wasp-green-2a"),
                action(ActionType.PRIMARY),
                unitAt(8, -5),
                hero("brynn"),
                action(ActionType.HOLD),
                card("bain-blue-2b"),
                card("wasp-red-2a"),
                action(ActionType.MOVEMENT),
                space(5, -5),
                action(ActionType.MOVEMENT),
                space(9, -10),
            ),
        ),
    )
}

private val R32_NUDGES = listOf(
    listOf(
        "Three Blue minions make six coins. Where are they, and who can reach which?",
        "This turn is for moving. Wasp has a long way to go.",
        "Play Close Call on Bain, moving 2 spaces, and Electrocute on Wasp, moving 4 spaces.",
    ),
    listOf(
        "You are each next to a Blue melee minion now.",
        "Dead or Alive and Magnetic Dagger each attack a unit next to you.",
        "Play Dead or Alive on Bain and Magnetic Dagger on Wasp, each attacking a Blue melee minion.",
    ),
    listOf(
        "The third minion isn't next to Bain, and Hand Crossbow's second text needs it to be.",
        "Get over here! moves it next to him. Holding it instead is proven to lose.",
        "Play Get over here! on Bain, moving the Blue melee minion, and Static Barrier on Wasp.",
    ),
    listOf(
        "One coin-worthy minion left, and it is next to Bain.",
        "Hand Crossbow's first text, a hero with a Bounty marker, is proven to lose here. Its second " +
            "targets a unit next to you.",
        "Play Hand Crossbow on Bain, attacking the Blue melee minion, and Deflect Projectiles on Wasp.",
    ),
)
