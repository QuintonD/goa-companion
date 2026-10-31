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

/** Full round 8, mined from self-play (g8840_r2_BLUE_coins-7; ADR-052, ENCODING.md "Full rounds"). */
object R35SevenCoinsAndMin : Puzzle() {
    override val concept = "R35"

    override val known = KnownPlays(
        cards = mapOf(
            "tigerclaw" to listOf("tigerclaw-red-2b", "tigerclaw-silver", "tigerclaw-blue-1", "tigerclaw-green-1"),
            "min" to listOf("min-blue-1", "min-gold", "min-green-2b", "min-red-1"),
        ),
        actions = mapOf(
            "tigerclaw" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY)),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(3, -2))),
                3 to listOf(Choice.Action(ActionType.HOLD)),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
            "min" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(5, -7))),
                2 to listOf(Choice.Action(ActionType.PRIMARY)),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -5))),
                4 to listOf(Choice.Action(ActionType.PRIMARY)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r35",
        number = 8,
        title = "Seven Coins and Min",
        difficulty = 1,
        theme = "Coins counted to a defeat, standing next to where the enemy goes",
        heroes = listOf("takahide", "snorri", "tigerclaw", "min"),
        goal = PuzzleGoal.GainCoins(7, Deadline.END_OF_ROUND, "Gain 7 coins by the end of the round."),
        intro = "Round 2. You play Takahide and Snorri for Blue. Melee and ranged minions pay 2 coins here, and Min " +
            "pays 2 to the hero" +
            " who defeats her and 1 to the other. Sting like a Bee is in Takahide's hand this round, and it " +
            "targets a unit at maximum range.",
        nudges = R35_NUDGES.first(),
        nudgesByTurn = R35_NUDGES,
        explanation =
            "This is one way through, and other plans win too: Lead from the Front is proven not to be needed." +
                "\n\n" +
                "Turn 1: Tigerclaw defeats a Blue melee minion. Sting like a Bee defeats the Red ranged minion at " +
                "maximum range: 2 coins. It then swaps itself for another gold card, Float like a Butterfly here." +
                "\n\n" +
                "Turn 2: Min defeats a Blue melee minion. Rune Sigils defeats a Red melee minion next to Snorri: 2 " +
                "more. Proven Warrior's Movement takes Takahide 2 spaces." +
                "\n\n" +
                "Turn 3: Come to Aid's Movement takes Takahide next to the space Min goes to, and Oath of Endurance's" +
                " takes Snorri 2 spaces." +
                "\n\n" +
                "Turn 4: Takahide acts first. Lead from the Front attacks Min at 3. Smoke Bomb gives 1, +1 for the " +
                "item, -1 for the enemy ranged minion in radius 2, makes 1, not enough, and she is defeated (p.13): 2" +
                " coins for Takahide and 1 for Snorri's assist, 7 in all.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense, Minion Defense Modifiers",
            "p.7 Check the initiative",
        ),
        known = known.display(
            mapOf(
                "tigerclaw" to mapOf(
                    1 to "Attacks a unit in its reach.",
                    2 to "Places itself on the space outlined.",
                    3 to "Holds.",
                    4 to "Holds.",
                ),
                "min" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Attacks a unit in its reach.",
                    3 to "Moves to the space outlined.",
                    4 to "Attacks a unit next to it.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("takahide", "snorri")

    override fun PositionBuilder.setUp() {
        round = 2
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 3)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        hero("tigerclaw", Team.RED, Hex(2, 0)) {
            level = 2
            coins = 1
            hand("tigerclaw-gold", "tigerclaw-silver", "tigerclaw-green-1", "tigerclaw-blue-1", "tigerclaw-red-2b")
            items("tigerclaw-red-2a")
            deck("tigerclaw-red-1")
        }
        hero("min", Team.RED, Hex(3, -7)) {
            level = 2
            coins = 1
            hand("min-gold", "min-silver", "min-red-1", "min-blue-1", "min-green-2b")
            items("min-green-2a")
            deck("min-green-1")
        }
        hero("takahide", Team.BLUE, Hex(3, -3)) {
            level = 2
            coins = 1
            hand("takahide-gold-extra-1", "takahide-silver", "takahide-green-1", "takahide-blue-1", "takahide-red-2a")
            items("takahide-red-2b")
            deck("takahide-gold", "takahide-red-1")
        }
        hero("snorri", Team.BLUE, Hex(0, -5)) {
            level = 2
            coins = 1
            hand("snorri-gold", "snorri-silver", "snorri-green-1", "snorri-blue-1", "snorri-red-2b")
            items("snorri-red-2a")
            deck("snorri-red-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.BLUE, MinionType.RANGED, Hex(3, -4))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
    }

    override val solution = listOf(
        card("takahide-gold-extra-1"),
        card("snorri-blue-1"),
        action(ActionType.HOLD),
        action(ActionType.PRIMARY),
        unitAt(0, -3),
        card("takahide-gold"),
        card("takahide-blue-1"),
        card("snorri-gold"),
        action(ActionType.PRIMARY),
        action(ActionType.MOVEMENT),
        space(4, -5),
        card("takahide-green-1"),
        card("snorri-green-1"),
        action(ActionType.MOVEMENT),
        space(0, -7),
        action(ActionType.MOVEMENT),
        space(4, -6),
        card("takahide-red-2a"),
        card("snorri-red-2b"),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("takahide-gold-extra-1"),
                card("snorri-gold"),
                action(ActionType.PRIMARY),
                action(ActionType.PRIMARY),
                unitAt(0, -3),
                card("takahide-gold"),
                card("takahide-green-1"),
                card("snorri-blue-1"),
                action(ActionType.PRIMARY),
                space(0, -2),
                action(ActionType.HOLD),
                card("takahide-blue-1"),
                card("snorri-red-2b"),
                action(ActionType.HOLD),
                action(ActionType.PRIMARY),
                card("takahide-silver"),
                card("snorri-silver"),
                action(ActionType.PRIMARY),
                card("takahide-gold-extra-1"),
                action(ActionType.PRIMARY),
                option(1),
                option(2),
                option(1),
            ),
        ),
    )
}

private val R35_NUDGES = listOf(
    listOf(
        "Seven coins is two minions and Min, with the assist. Which minion can you reach now?",
        "Sting like a Bee reaches the Red ranged minion at maximum range.",
        "Play Sting like a Bee on Takahide, attacking the Red ranged minion, and Safe Passage on Snorri.",
    ),
    listOf(
        "Snorri has a minion next to him. Takahide has to start towards where Min will end up.",
        "Rune Sigils attacks a unit next to Snorri. Proven Warrior's Movement takes Takahide 2 spaces.",
        "Play Proven Warrior on Takahide, moving 2 spaces, and Rune Sigils on Snorri, attacking the Red " +
            "melee minion.",
    ),
    listOf(
        "Min goes to the outlined space this turn. On this line Takahide goes next to it.",
        "Come to Aid's Movement offers thirteen answers. Seven are proven to lose.",
        "Play Come to Aid on Takahide, moving next to Min's space, and Oath of Endurance on Snorri, " +
            "moving.",
    ),
    listOf(
        "Takahide's card has the higher initiative. What can Min do about Attack 3?",
        "Lead from the Front attacks a unit next to Takahide. Bushido is proven to lose.",
        "Play Lead from the Front on Takahide, attacking Min, and Runecaster on Snorri.",
    ),
)
