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
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.tokenAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 13, mined from self-play (g2470_r3_BLUE_coins-6; ADR-052, ENCODING.md "Full rounds"). */
object R13HurryUpXargatha : Puzzle() {
    override val concept = "R13"

    override val known = KnownPlays(
        cards = mapOf(
            "misa" to listOf("misa-gold", "misa-silver", "misa-green-1", "misa-red-2a"),
            "emmitt" to listOf("emmitt-blue-1", "emmitt-silver", "emmitt-green-2a", "emmitt-red-1"),
        ),
        actions = mapOf(
            "misa" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Option(1, "Defeat a minion adjacent to you")),
                2 to listOf(Choice.Action(ActionType.HOLD)),
                3 to listOf(Choice.Action(ActionType.HOLD)),
            ),
            "emmitt" to mapOf(
                1 to listOf(Choice.Action(ActionType.HOLD)),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(3, -7)), Choice.Space(Hex(0, -5)), Choice.Unit("hanu")),
                3 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r13",
        number = 13,
        title = "Hurry Up, Xargatha",
        difficulty = 3,
        theme = "Changing your own card's initiative, attack bonuses counted from the board",
        heroes = listOf("hanu", "xargatha", "misa", "emmitt"),
        goal = PuzzleGoal.GainCoins(6, Deadline.END_OF_ROUND, "Gain 6 coins by the end of the round."),
        intro = "Round 3. You play Hanu and Xargatha for Blue. Minions pay 2 coins here, and Misa pays 3 to the hero " +
            "who defeats her and 1 to the other. Hurry up! sets the printed Initiative of a hero's unresolved " +
            "card to 11, and it can be your own. Deadly Swipe gets +2 Attack for each other enemy unit next to " +
            "you. Turn 4 isn't known.",
        nudges = R13_NUDGES.first(),
        nudgesByTurn = R13_NUDGES,
        explanation = "Turn 1: Misa defeats a Blue minion as planned. You have nothing in reach, so Monkey Trick and " +
            "Siren's Call are the cards to spend." +
            "\n\n" +
            "Turn 2: Unexpected Journey's Movement takes Hanu 3 spaces, next to the Red ranged minion. Charm's " +
            "takes Xargatha 2. Emmitt's Unstable Timeline has you choose the Glitch he swaps with." +
            "\n\n" +
            "Turn 3: Fight and Flight attacks at only 2, but that defeats the Red ranged minion: 2 coins. " +
            "Cleave's Movement takes Xargatha 1 space, next to Misa and Emmitt, which is what Deadly Swipe " +
            "counts." +
            "\n\n" +
            "Turn 4: Hanu acts first, and Hurry up! sets Deadly Swipe's printed Initiative to 11, ahead of Misa's" +
            " Matter of Honor at 9 (p.7). So Xargatha attacks before Misa can act, at 8 with Emmitt next to her. " +
            "Dash and Slash gives 7, not enough, and Misa is defeated (p.13): 3 coins for Xargatha and 1 for " +
            "Hanu. Every other option at these two decisions is proven to lose." +
            "\n\n" +
            "The computer at strength 7, playing your heroes, doesn't find it, and neither does strength 10.",
        rules = listOf(
            "p.7 Check the initiative",
            "p.13 Attacking a hero, When a hero is defeated",
        ),
        known = known.display(
            mapOf(
                "misa" to mapOf(
                    1 to "Watch How I Soar: defeats a Blue minion next to her.",
                    2 to "Holds.",
                    3 to "Holds.",
                ),
                "emmitt" to mapOf(
                    1 to "Holds.",
                    2 to "Unstable Timeline: Glitch tokens on the spaces outlined, and he swaps with one.",
                    3 to "Holds.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("hanu", "xargatha")

    override fun PositionBuilder.setUp() {
        round = 3
        tieBreaker = Team.BLUE
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 3)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        hero("misa", Team.RED, Hex(4, -7)) {
            level = 3
            coins = 1
            hand("misa-gold", "misa-silver", "misa-green-1", "misa-red-2a", "misa-blue-2b")
            items("misa-red-2b", "misa-blue-2a")
            deck("misa-red-1", "misa-blue-1")
        }
        hero("emmitt", Team.RED, Hex(-1, -5)) {
            level = 2
            hand("emmitt-gold", "emmitt-silver", "emmitt-red-1", "emmitt-blue-1", "emmitt-green-2a")
            items("emmitt-green-2b")
            deck("emmitt-green-1")
        }
        hero("hanu", Team.BLUE, Hex(1, -2)) {
            level = 2
            coins = 2
            hand("hanu-gold", "hanu-silver", "hanu-green-1", "hanu-blue-1", "hanu-red-2a")
            items("hanu-red-2b")
            deck("hanu-red-1")
        }
        hero("xargatha", Team.BLUE, Hex(3, -3)) {
            level = 3
            hand("xargatha-gold", "xargatha-silver", "xargatha-green-1", "xargatha-red-2a", "xargatha-blue-2a")
            items("xargatha-red-2b", "xargatha-blue-2b")
            deck("xargatha-red-1", "xargatha-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
    }

    override val solution = listOf(
        card("hanu-green-1"),
        card("xargatha-silver"),
        action(ActionType.PRIMARY),
        action(ActionType.HOLD),
        card("hanu-blue-1"),
        card("xargatha-green-1"),
        action(ActionType.MOVEMENT),
        space(-1, -3),
        action(ActionType.PRIMARY),
        space(3, -5),
        tokenAt(3, -7),
        card("hanu-gold"),
        card("xargatha-gold"),
        action(ActionType.PRIMARY),
        action(ActionType.MOVEMENT),
        space(3, -6),
        card("hanu-silver"),
        card("xargatha-red-2a"),
        action(ActionType.PRIMARY),
        hero("xargatha"),
        action(ActionType.PRIMARY),
        hero("misa"),
    )

    override val wrongTries = listOf(
        WrongTry(
            "The computer at strength 7 on your heroes",
            listOf(
                card("hanu-silver"),
                card("xargatha-gold"),
                action(ActionType.PRIMARY),
                action(ActionType.PRIMARY),
                decline(),
                card("hanu-blue-1"),
                card("xargatha-green-1"),
                action(ActionType.HOLD),
                action(ActionType.HOLD),
                tokenAt(0, -5),
                card("hanu-red-2a"),
                card("xargatha-silver"),
                action(ActionType.HOLD),
                action(ActionType.PRIMARY),
                card("hanu-gold"),
                card("xargatha-red-2a"),
                action(ActionType.HOLD),
                action(ActionType.MOVEMENT),
                space(1, -4),
            ),
        ),
    )
}

private val R13_NUDGES = listOf(
            listOf(
                "Six coins is a minion and Misa. Look at her Turn 4 card's initiative, and at yours.",
                "Nothing is in reach yet. Spend the cards you won't need.",
                "Play Monkey Trick on Hanu and Siren's Call on Xargatha.",
            ),
            listOf(
                "Hanu wants to stand next to the Red ranged minion. Xargatha has to start towards Misa and " +
                "Emmitt.",
                "Unexpected Journey's Movement is 3. Charm's is 2.",
                "Play Unexpected Journey on Hanu, moving next to the Red ranged minion, and Charm on Xargatha, " +
                "who moves.",
            ),
            listOf(
                "Hanu can collect now, even at Attack 2. Where must Xargatha end this turn?",
                "Next to both Misa and Emmitt: count what Deadly Swipe is worth there.",
                "Play Fight and Flight on Hanu, attacking the Red ranged minion, and Cleave on Xargatha, moving " +
                "next to Misa and Emmitt.",
            ),
            listOf(
                "Deadly Swipe would act after Misa. Which of your cards changes that?",
                "Hanu's Hurry up! acts first and sets Deadly Swipe to 11, ahead of Misa's 9.",
                "Play Hurry up! on Hanu, on Xargatha's card, and Deadly Swipe on Xargatha, attacking Misa.",
            ),
)
