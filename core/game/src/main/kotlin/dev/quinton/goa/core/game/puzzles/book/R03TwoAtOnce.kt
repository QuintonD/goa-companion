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
import dev.quinton.goa.core.game.puzzles.Moves.number
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 3, mined from self-play (g6501_r3_RED_coins-7; ADR-052, ENCODING.md "Full rounds"). */
object R03TwoAtOnce : Puzzle() {
    override val concept = "R03"

    override val known = KnownPlays(
        cards = mapOf(
            "takahide" to listOf("takahide-silver", "takahide-green-1", "takahide-red-2a", "takahide-blue-1"),
            "tigerclaw" to listOf("tigerclaw-gold", "tigerclaw-red-1", "tigerclaw-silver", "tigerclaw-green-1"),
        ),
        actions = mapOf(
            "takahide" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Card("takahide-gold-extra-1")),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -5))),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Decline("Don't move")),
                4 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(2, -3))),
            ),
            "tigerclaw" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY)),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(2, -8))),
                3 to listOf(Choice.Action(ActionType.HOLD)),
                4 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(3, -9))),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r03",
        number = 3,
        title = "Two at Once",
        difficulty = 2,
        theme = "Two defeats in one turn, swapping to reach the front",
        heroes = listOf("hanu", "ursafar", "takahide", "tigerclaw"),
        goal = PuzzleGoal.GainCoins(7, Deadline.END_OF_ROUND, "Gain 7 coins by the end of the round."),
        intro = "Round 3. You play Hanu and Ursafar for Red. Minions pay 2 coins here. Takahide and Tigerclaw each " +
            "pay 2 to the hero who defeats them and 1 to the other, and the whole of their round is known.",
        nudges = R03_NUDGES.first(),
        nudgesByTurn = R03_NUDGES,
        explanation =
            "Turn 1: Tigerclaw defeats a Red melee minion. Sniff Out's Movement brings Ursafar up, and Hear " +
                "Nothing swaps Hanu with Takahide, which puts Hanu next to a Blue melee minion." +
                "\n\n" +
                "Turn 2: Fight and Flight defeats that minion: 2 coins. Ursafar moves next to the Blue heavy with " +
                "Claws That Catch's Movement. Tigerclaw takes a second Red minion and moves next to Ursafar." +
                "\n\n" +
                "Turn 3: Hurry up! and Angry Roar are played now so that the two attacks are left for turn 4. Angry " +
                "Roar also makes Ursafar enraged this round." +
                "\n\n" +
                "Turn 4: Takahide walks next to Hanu as planned. Hanu acts first on the tie: Helping Hand attacks him" +
                " at 4, Sting like a Bee gives only 2, and he is defeated (p.13), 2 coins to Hanu and 1 to Ursafar. " +
                "Rip then attacks Tigerclaw at 5. Sidestep's Block fails (p.14), he is defeated before his card " +
                "resolves, 2 coins to Ursafar and 1 to Hanu, and enraged Rip pays 1 more. The goal needs 7; you have " +
                "9." +
                "\n\n" +
                "Even so Blue wins the Minion Battle, 2 against 1: your coins are the goal here, and it costs you the" +
                " lane. The computer at strength 7, playing your heroes, doesn't find it, and neither does strength " +
                "10.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Block",
            "p.7 Tied Initiative",
            "p.8 Minion Battle, Push the Lane",
        ),
        known = known.display(
            mapOf(
                "takahide" to mapOf(
                    1 to "Bushido: takes Sting like a Bee into its hand.",
                    2 to "Moves to the space outlined.",
                    3 to "Lead from the Front, without moving.",
                    4 to "Moves to the space outlined, next to Hanu.",
                ),
                "tigerclaw" to mapOf(
                    1 to "Blink Strike, ending on the space outlined, and defeats a Red melee minion.",
                    2 to "Attacks a Red melee minion, then moves to the space outlined, next to Ursafar.",
                    3 to "Holds.",
                    4 to "Plays Light-Fingered.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("hanu", "ursafar")

    override fun PositionBuilder.setUp() {
        round = 3
        tieBreaker = Team.BLUE
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 3)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        hero("hanu", Team.RED, Hex(4, -4)) {
            level = 3
            hand("hanu-gold", "hanu-silver", "hanu-red-1", "hanu-green-2b", "hanu-blue-2b")
            items("hanu-green-2a", "hanu-blue-2a")
            deck("hanu-green-1", "hanu-blue-1")
        }
        hero("ursafar", Team.RED, Hex(3, -5)) {
            level = 3
            coins = 1
            hand("ursafar-gold", "ursafar-silver", "ursafar-green-1", "ursafar-red-2b", "ursafar-blue-2b")
            items("ursafar-red-2a", "ursafar-blue-2a")
            deck("ursafar-red-1", "ursafar-blue-1")
        }
        hero("takahide", Team.BLUE, Hex(2, -2)) {
            level = 2
            coins = 2
            hand("takahide-gold-extra-2", "takahide-silver", "takahide-green-1", "takahide-blue-1", "takahide-red-2a")
            items("takahide-red-2b")
            deck("takahide-gold", "takahide-red-1")
        }
        hero("tigerclaw", Team.BLUE, Hex(-1, -5)) {
            level = 2
            coins = 2
            hand("tigerclaw-gold", "tigerclaw-silver", "tigerclaw-red-1", "tigerclaw-green-1", "tigerclaw-blue-2a")
            items("tigerclaw-blue-2b")
            deck("tigerclaw-blue-1")
        }
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
    }

    override val solution = listOf(
        card("hanu-green-2b"),
        card("ursafar-green-1"),
        action(ActionType.MOVEMENT),
        space(3, -7),
        action(ActionType.PRIMARY),
        card("hanu-gold"),
        card("ursafar-gold"),
        action(ActionType.PRIMARY),
        action(ActionType.MOVEMENT),
        space(3, -8),
        card("hanu-silver"),
        card("ursafar-silver"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        card("hanu-red-1"),
        card("ursafar-red-2b"),
        hero("hanu"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "The computer at strength 7 on your heroes",
            listOf(
                card("hanu-silver"),
                card("ursafar-green-1"),
                action(ActionType.HOLD),
                action(ActionType.MOVEMENT),
                space(3, -3),
                card("hanu-red-1"),
                card("ursafar-blue-2b"),
                action(ActionType.PRIMARY),
                space(4, -3),
                action(ActionType.MOVEMENT),
                space(1, -1),
                card("hanu-gold"),
                card("ursafar-silver"),
                action(ActionType.PRIMARY),
                action(ActionType.HOLD),
                card("hanu-blue-2b"),
                card("ursafar-red-2b"),
                action(ActionType.PRIMARY),
                number(2),
                space(-1, 1),
                action(ActionType.PRIMARY),
            ),
        ),
    )
}

private val R03_NUDGES = listOf(
    listOf(
        "Both enemy heroes spend their cards on minions. Count what each will have left in turn 4.",
        "Hanu stands away from the Blue minions. Is there a card that takes him where Takahide is?",
        "Play Hear Nothing on Hanu, swapping with Takahide, and Sniff Out on Ursafar, taking its " +
            "Movement.",
    ),
    listOf(
        "Hanu has a minion next to him now. Ursafar has to get close to where Tigerclaw is going.",
        "Fight and Flight attacks a unit next to Hanu. Ursafar's Movement matters more than his attack " +
            "this turn.",
        "Play Fight and Flight on Hanu, attacking the Blue melee minion, and Claws That Catch on Ursafar," +
            " taking its Movement.",
    ),
    listOf(
        "The enemy is close enough. Now spend the cards you won't need in turn 4.",
        "Keep both of your attacks. Ursafar is enraged by Angry Roar for the round.",
        "Play Hurry up! on Hanu and Angry Roar on Ursafar.",
    ),
    listOf(
        "Takahide walks next to Hanu, and Tigerclaw is beside Ursafar. Can their last cards stop your " +
            "attacks?",
        "Hanu's Helping Hand and Ursafar's Rip both act before Tigerclaw's card. Sidestep's Block doesn't" +
            " work against a non-ranged attack.",
        "Play Helping Hand on Hanu, attacking Takahide, and Rip on Ursafar, attacking Tigerclaw.",
    ),
)
