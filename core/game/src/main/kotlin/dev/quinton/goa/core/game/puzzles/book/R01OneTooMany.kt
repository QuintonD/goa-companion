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

/** Full round 5, mined from self-play (g7906_r3_BLUE_push; ADR-052, ENCODING.md "Full rounds"). */
object R01OneTooMany : Puzzle() {
    override val concept = "R01"

    override val known = KnownPlays(
        cards = mapOf(
            "rowenna" to listOf("rowenna-silver", "rowenna-green-1", "rowenna-red-2b", "rowenna-blue-2a"),
            "ursafar" to listOf("ursafar-blue-2b", "ursafar-red-2a", "ursafar-silver", "ursafar-gold"),
        ),
        actions = mapOf(
            "rowenna" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(0, -5))),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(1, -6))),
                3 to listOf(Choice.Action(ActionType.HOLD)),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
            "ursafar" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(2, -2))),
                2 to listOf(Choice.Action(ActionType.PRIMARY)),
                3 to listOf(Choice.Action(ActionType.HOLD)),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r01",
        number = 5,
        title = "One Too Many",
        difficulty = 1,
        theme = "Minion Battle arithmetic, a shot that can wait",
        heroes = listOf("arien", "trinkets", "rowenna", "ursafar"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_ROUND, "Push the lane by the end of the round."),
        intro = "Round 3. You play Arien and Trinkets for Blue. In the Battle Zone Red has three minions and Blue has" +
            " three, and Ursafar is going to take one of yours. Gatling Gun can only shoot a unit in range of " +
            "both Trinkets and the Turret, and the Turret isn't on the board yet.",
        nudges = R01_NUDGES.first(),
        nudgesByTurn = R01_NUDGES,
        explanation =
            "This is one way through, and other plans win too: neither Noble Blade nor Gatling Gun is needed for " +
                "all of them. Every plan we found comes down to the count: Red has to end the round far enough " +
                "behind in the Minion " +
                "Battle to lose its last minion." +
                "\n\n" +
                "Turn 1: Red has a ranged, a melee and a heavy minion; Blue a heavy and two melee, and Ursafar will " +
                "take a melee in turn 2. Noble Blade defeats the ranged minion next to Arien at once." +
                "\n\n" +
                "Turn 2: Ursafar defeats a Blue melee minion as planned. Rapid Redeployment moves Trinkets 3 spaces " +
                "and places the Turret next to him. Arien plays Spell Break." +
                "\n\n" +
                "Turn 3: The shot can come in turn 3 or turn 4: here Trinkets uses Salvage Parts first, placing the " +
                "Turret again next to the Red heavy minion. Arien moves away." +
                "\n\n" +
                "Turn 4: Gatling Gun attacks the Red melee minion at 3 and defeats it. In the Minion Battle Red has 1" +
                " and Blue 2: Red removes its heavy, its last minion in the Zone, and Blue pushes the lane (p.8).",
        rules = listOf(
            "p.8 Minion Battle, Push the Lane",
            "p.9 Range and Radius",
            "p.13 Heavy Minion Immunity",
        ),
        known = known.display(
            mapOf(
                "rowenna" to mapOf(
                    1 to "Lands on the space outlined.",
                    2 to "Moves to the space outlined.",
                    3 to "Holds.",
                    4 to "Holds.",
                ),
                "ursafar" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Attacks a unit next to it.",
                    3 to "Holds.",
                    4 to "Holds.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("arien", "trinkets")

    override fun PositionBuilder.setUp() {
        round = 3
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 3)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 3)
        hero("rowenna", Team.RED, Hex(2, -8)) {
            level = 3
            hand("rowenna-gold", "rowenna-silver", "rowenna-green-1", "rowenna-red-2b", "rowenna-blue-2a")
            items("rowenna-red-2a", "rowenna-blue-2b")
            deck("rowenna-red-1", "rowenna-blue-1")
        }
        hero("ursafar", Team.RED, Hex(2, -3)) {
            level = 4
            hand("ursafar-gold", "ursafar-silver", "ursafar-red-2a", "ursafar-green-2a", "ursafar-blue-2b")
            items("ursafar-red-2b", "ursafar-green-2b", "ursafar-blue-2a")
            deck("ursafar-red-1", "ursafar-green-1", "ursafar-blue-1")
        }
        hero("arien", Team.BLUE, Hex(0, -4)) {
            level = 3
            coins = 1
            hand("arien-gold", "arien-silver", "arien-red-1", "arien-green-2a", "arien-blue-2b")
            items("arien-green-2b", "arien-blue-2a")
            deck("arien-green-1", "arien-blue-1")
        }
        hero("trinkets", Team.BLUE, Hex(-1, 3)) {
            level = 2
            coins = 2
            hand("trinkets-gold", "trinkets-silver", "trinkets-green-1", "trinkets-blue-1", "trinkets-red-2a")
            items("trinkets-red-2b")
            deck("trinkets-red-1")
        }
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
    }

    override val solution = listOf(
        card("arien-gold"),
        card("trinkets-green-1"),
        action(ActionType.PRIMARY),
        space(-1, -4),
        action(ActionType.PRIMARY),
        card("arien-silver"),
        card("trinkets-gold"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        space(-1, 0),
        space(-1, -1),
        card("arien-red-1"),
        card("trinkets-silver"),
        action(ActionType.MOVEMENT),
        space(0, -8),
        action(ActionType.PRIMARY),
        option(0),
        space(0, 0),
        card("arien-green-2a"),
        card("trinkets-red-2a"),
        action(ActionType.PRIMARY),
        unitAt(-2, 1),
        action(ActionType.PRIMARY),
        space(2, -8),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("arien-gold"),
                card("trinkets-blue-1"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(0, 1),
                space(0, -3),
                card("arien-green-2a"),
                card("trinkets-silver"),
                action(ActionType.PRIMARY),
                space(0, 0),
                action(ActionType.PRIMARY),
                space(-1, 0),
                card("arien-silver"),
                card("trinkets-green-1"),
                action(ActionType.PRIMARY),
                action(ActionType.HOLD),
                card("arien-blue-2b"),
                card("trinkets-gold"),
                action(ActionType.HOLD),
                action(ActionType.PRIMARY),
                space(-1, 1),
            ),
        ),
    )
}

private val R01_NUDGES = listOf(
    listOf(
        "Count to the end of the round: Ursafar takes one of your three, so Blue will have two. How many " +
            "may Red keep?",
        "Arien stands next to the Red ranged minion now. That is one of the ways to start.",
        "Play Noble Blade on Arien, attacking the Red ranged minion, and Early Prototype on Trinkets.",
    ),
    listOf(
        "Red still has two minions, one too many. Which card of Trinkets can reach the melee minion, and " +
            "what does it need?",
        "Rapid Redeployment moves Trinkets up and puts the Turret on the board.",
        "Play Spell Break on Arien and Rapid Redeployment on Trinkets, moving next to the Red melee " +
            "minion.",
    ),
    listOf(
        "The Turret already reaches the Red melee minion, so the shot can come now or next turn.",
        "Salvage Parts can place the Turret again, and that wins. Its third choice, removing the Turret " +
            "to retrieve a card, loses.",
        "Play Dangerous Current on Arien, moving away, and Salvage Parts on Trinkets, placing the Turret " +
            "again.",
    ),
    listOf(
        "Enemies only hold this turn. All that is left is the shot.",
        "Gatling Gun reaches the Red melee minion from Trinkets and from the Turret.",
        "Play Magical Current on Arien and Gatling Gun on Trinkets, attacking the Red melee minion.",
    ),
)
