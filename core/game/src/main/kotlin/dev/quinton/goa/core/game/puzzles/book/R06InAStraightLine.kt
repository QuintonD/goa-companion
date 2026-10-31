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
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 6, mined from self-play (g612_r4_RED_defeat-trinkets; ADR-052, ENCODING.md "Full rounds"). */
object R06InAStraightLine : Puzzle() {
    override val concept = "R06"

    override val known = KnownPlays(
        cards = mapOf(
            "brogan" to listOf("brogan-green-2b", "brogan-red-1", "brogan-gold", "brogan-silver"),
            "trinkets" to listOf("trinkets-green-1", "trinkets-silver", "trinkets-blue-2a", "trinkets-red-1"),
        ),
        actions = mapOf(
            "brogan" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY)),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(-3, -3))),
                3 to listOf(Choice.Action(ActionType.PRIMARY)),
            ),
            "trinkets" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -7))),
                2 to listOf(Choice.Action(ActionType.HOLD)),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(0, -4))),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r06",
        number = 6,
        title = "In a Straight Line",
        difficulty = 2,
        theme = "Walking a shot into place, a straight line kept open",
        heroes = listOf("brynn", "cutter", "brogan", "trinkets"),
        goal = PuzzleGoal.DefeatHero("trinkets", Deadline.END_OF_ROUND, "Defeat Trinkets by the end of the round."),
        intro = "Round 4. You play Brynn and Cutter for Red. Trinkets defends badly: its Rapid Redeployment gives 1. " +
            "Cutter's Evasive Shot reaches a unit in range 2 in a straight line, so everything depends on where " +
            "Cutter and Trinkets stand in turn 4. Turn 4 isn't known.",
        nudges = R06_NUDGES.first(),
        nudgesByTurn = R06_NUDGES,
        explanation = "Turn 1: Trinkets walks next to Cutter as planned. Tread Lightly's Movement starts Brynn across, and " +
            "Cutter holds with Barrage." +
            "\n\n" +
            "Turn 2: Walk the Plank can't move Trinkets here. Familiar Ground's Movement takes Brynn next to " +
            "Brogan, and Brogan walks off towards Lane Zone 1." +
            "\n\n" +
            "Turn 3: Brogan defeats a Red melee minion, and Trinkets moves to the outlined space. Grappling Bolt " +
            "takes Cutter along a straight line, to a space from which Evasive Shot can reach it next turn." +
            "\n\n" +
            "Turn 4: Evasive Shot attacks Trinkets at 5. Rapid Redeployment gives 1, and it is defeated before " +
            "Makeshift Minigun resolves (p.13). Blue spends a Life counter. Several of the other options in turns" +
            " 3 and 4 are proven to lose." +
            "\n\n" +
            "The computer at strength 7, playing your heroes, doesn't find it. Strength 10 does.",
        rules = listOf(
            "p.19 Straight line",
            "p.13 Attacking a hero, When a hero is defeated",
            "p.18 Obstacle",
        ),
        known = known.display(
            mapOf(
                "brogan" to mapOf(
                    1 to "Plays War Drummer.",
                    2 to "Moves to the space outlined.",
                    3 to "Attacks a Red melee minion, then moves to the space outlined.",
                ),
                "trinkets" to mapOf(
                    1 to "Moves to the space outlined, next to Cutter.",
                    2 to "Holds.",
                    3 to "Moves to the space outlined.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("brynn", "cutter")

    override fun PositionBuilder.setUp() {
        round = 4
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-1")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 3)
        piece(PieceState("p17", "trinkets_turret", "Turret", Hex(2, -2), "trinkets", null, false, false, false, false, false, null, linkedMapOf()))
        hero("brynn", Team.RED, Hex(-1, -2)) {
            level = 2
            coins = 2
            hand("brynn-gold", "brynn-silver", "brynn-green-1", "brynn-blue-1", "brynn-red-2a")
            items("brynn-red-2b")
            deck("brynn-red-1")
        }
        hero("cutter", Team.RED, Hex(3, -6)) {
            level = 4
            coins = 1
            hand("cutter-gold", "cutter-silver", "cutter-red-2b", "cutter-green-2a", "cutter-blue-2a")
            items("cutter-red-2a", "cutter-green-2b", "cutter-blue-2b")
            deck("cutter-red-1", "cutter-green-1", "cutter-blue-1")
        }
        hero("brogan", Team.BLUE, Hex(0, -6)) {
            level = 3
            coins = 2
            hand("brogan-gold", "brogan-silver", "brogan-red-1", "brogan-green-2b", "brogan-blue-2a")
            items("brogan-green-2a", "brogan-blue-2b")
            deck("brogan-green-1", "brogan-blue-1")
        }
        hero("trinkets", Team.BLUE, Hex(4, -9)) {
            level = 2
            coins = 1
            hand("trinkets-gold", "trinkets-silver", "trinkets-red-1", "trinkets-green-1", "trinkets-blue-2a")
            items("trinkets-blue-2b")
            deck("trinkets-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(-4, -2))
        minion(Team.RED, MinionType.MELEE, Hex(-5, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(-4, -1))
        minion(Team.BLUE, MinionType.RANGED, Hex(-3, 0))
        minion(Team.RED, MinionType.MELEE, Hex(-8, 1))
        minion(Team.RED, MinionType.HEAVY, Hex(-6, 1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(-5, 1))
        minion(Team.RED, MinionType.RANGED, Hex(-6, 2))
        minion(Team.BLUE, MinionType.MELEE, Hex(-5, 2))
        minion(Team.BLUE, MinionType.MELEE, Hex(-4, 3))
        minion(Team.RED, MinionType.MELEE, Hex(-5, 4))
    }

    override val solution = listOf(
        card("brynn-blue-1"),
        card("cutter-blue-2a"),
        action(ActionType.HOLD),
        action(ActionType.MOVEMENT),
        space(1, -5),
        card("brynn-gold"),
        card("cutter-gold"),
        action(ActionType.PRIMARY),
        number(0),
        action(ActionType.MOVEMENT),
        space(1, -6),
        card("brynn-silver"),
        card("cutter-silver"),
        action(ActionType.PRIMARY),
        space(1, -6),
        action(ActionType.HOLD),
        card("brynn-green-1"),
        card("cutter-red-2b"),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "The computer at strength 7 on your heroes",
            listOf(
                card("brynn-blue-1"),
                card("cutter-gold"),
                action(ActionType.MOVEMENT),
                space(2, -6),
                action(ActionType.MOVEMENT),
                space(-3, 1),
                card("brynn-red-2a"),
                card("cutter-green-2a"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(0, -5),
                card("brynn-silver"),
                card("cutter-silver"),
                action(ActionType.PRIMARY),
                space(-4, -1),
                action(ActionType.PRIMARY),
                option(1),
                space(-5, -2),
                option(0),
                unitAt(-4, 3),
                space(-3, 3),
                card("brynn-green-1"),
                card("cutter-red-2b"),
                action(ActionType.PRIMARY),
                unitAt(-4, -1),
                decline(),
                action(ActionType.HOLD),
            ),
        ),
    )
}

private val R06_NUDGES = listOf(
            listOf(
                "Cutter's attack is his last card. Don't spend it. What can each of you do without it?",
                "Trinkets is about to come next to Cutter. Cutter can wait; Brynn should get moving.",
                "Play Tread Lightly on Brynn, moving, and Barrage on Cutter, who holds.",
            ),
            listOf(
                "Trinkets stands next to Cutter. It won't stay there. Brynn has further to go.",
                "Walk the Plank pushes an enemy next to you, and a push of 0 is allowed: Trinkets can't be moved " +
                "anyway.",
                "Play Familiar Ground on Brynn, moving next to Brogan, and Walk the Plank on Cutter.",
            ),
            listOf(
                "Trinkets is going to the outlined space. Which space is two away from it in a straight line?",
                "Grappling Bolt pulls Cutter along a straight line to an obstacle.",
                "Play Decoy on Brynn, who holds, and Grappling Bolt on Cutter.",
            ),
            listOf(
                "Trinkets is in reach. Who acts first?",
                "Evasive Shot acts before its card, and it has nothing that stops Attack 5.",
                "Play Bear Trap on Brynn and Evasive Shot on Cutter, attacking Trinkets.",
            ),
)
