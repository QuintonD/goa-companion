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

/** Full round 8, mined from self-play (g6389_r4_RED_coins-6; ADR-052, ENCODING.md "Full rounds"). */
object R08WalkThePlankThenTheThrust : Puzzle() {
    override val concept = "R08"

    override val known = KnownPlays(
        cards = mapOf(
            "ignatia" to listOf("ignatia-silver", "ignatia-blue-2a", "ignatia-green-2a", "ignatia-gold"),
            "gydion" to listOf("gydion-red-1", "gydion-green-2b", "gydion-blue-2a", "gydion-gold"),
        ),
        actions = mapOf(
            "ignatia" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY)),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(2, -5))),
                3 to listOf(Choice.Action(ActionType.FAST_TRAVEL), Choice.Space(Hex(5, -7))),
                4 to listOf(Choice.Action(ActionType.PRIMARY)),
            ),
            "gydion" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(2, 0))),
                2 to listOf(Choice.Action(ActionType.HOLD)),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(4, -2))),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r08",
        number = 8,
        title = "Walk the Plank, Then the Thrust",
        difficulty = 2,
        theme = "A skill that spends the enemy's spare card, coins from a defeat",
        heroes = listOf("cutter", "razzle", "ignatia", "gydion"),
        goal = PuzzleGoal.GainCoins(6, Deadline.END_OF_ROUND, "Gain 6 coins by the end of the round."),
        intro = "Round 4. You play Cutter and Razzle for Red. Minions pay 2 coins here and defeating Ignatia pays 4. " +
            "The Battle Zone is Lane Zone 3. Ignatia holds five cards and will play four, and her last turn's " +
            "attack is hers to aim.",
        nudges = R08_NUDGES.first(),
        nudgesByTurn = R08_NUDGES,
        explanation = "Turn 1: Grappling Bolt pulls Cutter along a straight line to an obstacle, next to a Blue melee " +
            "minion in the Battle Zone. Razzle uses Rummage's Movement." +
            "\n\n" +
            "Turn 2: Cutter wins the tie and Walk the Plank defeats the Blue melee minion next to him: 2 coins." +
            "\n\n" +
            "Turn 3: Ignatia fast travels next to the Red ranged minion as planned. Brace for Impact moves Cutter" +
            " 3 spaces in a straight line to the space next to her, and she has to discard Crack of Doom, her " +
            "spare." +
            "\n\n" +
            "Turn 4: Ignatia acts first and attacks Cutter at 3; X Marks the Spot defends with 6 (p.14). Then " +
            "Bold Thrust attacks her at 5. She has no card to defend with and is defeated (p.13): 4 more coins, " +
            "and the goal is met." +
            "\n\n" +
            "The computer at strength 7, playing your heroes, doesn't find it, and neither does strength 10.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense",
            "p.11 Fast Travel",
        ),
        known = known.display(
            mapOf(
                "ignatia" to mapOf(
                    1 to "Spontaneous Immolation.",
                    2 to "Moves to the space outlined.",
                    3 to "Fast travels to the space outlined.",
                    4 to "Attacks you with Chaos Bolt, as hard as it can.",
                ),
                "gydion" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Holds.",
                    3 to "Moves to the space outlined.",
                    4 to "Holds.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("cutter", "razzle")

    override fun PositionBuilder.setUp() {
        round = 4
        tieBreaker = Team.BLUE
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 4)
        hero("cutter", Team.RED, Hex(3, -2)) {
            level = 3
            coins = 2
            hand("cutter-gold", "cutter-silver", "cutter-green-1", "cutter-red-2a", "cutter-blue-2b")
            items("cutter-red-2b", "cutter-blue-2a")
            deck("cutter-red-1", "cutter-blue-1")
        }
        hero("razzle", Team.RED, Hex(4, -8)) {
            level = 3
            hand("razzle-gold", "razzle-silver", "razzle-green-1", "razzle-red-2b", "razzle-blue-2a")
            items("razzle-red-2a", "razzle-blue-2b")
            deck("razzle-red-1", "razzle-blue-1")
        }
        hero("ignatia", Team.BLUE, Hex(-1, -2)) {
            level = 4
            coins = 2
            hand("ignatia-gold", "ignatia-silver", "ignatia-red-2b", "ignatia-green-2a", "ignatia-blue-2a")
            items("ignatia-red-2a", "ignatia-green-2b", "ignatia-blue-2b")
            deck("ignatia-red-1", "ignatia-green-1", "ignatia-blue-1")
        }
        hero("gydion", Team.BLUE, Hex(0, 2)) {
            level = 3
            coins = 1
            hand("gydion-gold", "gydion-silver", "gydion-red-1", "gydion-green-2b", "gydion-blue-2a")
            items("gydion-green-2a", "gydion-blue-2b")
            deck("gydion-green-1", "gydion-blue-1")
            extra("gydion-spell-magic-missile", "gydion-spell-expeditious-retreat", "gydion-spell-burning-hands", tag = "removed")
            extra("gydion-spell-shocking-grasp", "gydion-spell-fireball", "gydion-spell-sunburst", "gydion-spell-vampiric-touch", "gydion-spell-create-undead", "gydion-spell-energy-drain", "gydion-spell-shield", "gydion-spell-banishment", "gydion-spell-invulnerability", "gydion-spell-dimension-door", "gydion-spell-find-familiar", "gydion-spell-cloud-kill", "gydion-spell-suggestion", "gydion-spell-dominate-person", "gydion-spell-power-word-kill", "gydion-spell-midas-touch", "gydion-spell-disintegrate", "gydion-spell-polymorph", "gydion-spell-wish", tag = "spellbook")
            counter("spellbook", 19)
            counter("spellsRemoved", 3)
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
        minion(Team.BLUE, MinionType.MELEE, Hex(7, -5))
    }

    override val solution = listOf(
        card("cutter-silver"),
        card("razzle-red-2b"),
        action(ActionType.MOVEMENT),
        space(2, -10),
        action(ActionType.PRIMARY),
        space(7, -6),
        card("cutter-gold"),
        card("razzle-gold"),
        hero("cutter"),
        action(ActionType.PRIMARY),
        action(ActionType.MOVEMENT),
        space(1, -10),
        card("cutter-green-1"),
        card("razzle-silver"),
        action(ActionType.HOLD),
        action(ActionType.PRIMARY),
        space(6, -8),
        card("cutter-red-2a"),
        card("razzle-blue-2a"),
        card("cutter-blue-2b"),
        action(ActionType.MOVEMENT),
        space(2, -11),
        action(ActionType.PRIMARY),
        option(1),
    )

    override val wrongTries = listOf(
        WrongTry(
            "The computer at strength 7 on your heroes",
            listOf(
                card("cutter-green-1"),
                card("razzle-green-1"),
                action(ActionType.MOVEMENT),
                space(5, -3),
                action(ActionType.PRIMARY),
                space(5, -7),
                card("cutter-silver"),
                card("razzle-silver"),
                action(ActionType.HOLD),
                action(ActionType.PRIMARY),
                space(6, -3),
                card("cutter-blue-2b"),
                card("razzle-blue-2a"),
                hero("razzle"),
                action(ActionType.MOVEMENT),
                space(8, -10),
                action(ActionType.MOVEMENT),
                space(5, -5),
                card("cutter-red-2a"),
                card("razzle-gold"),
                action(ActionType.PRIMARY),
                space(8, -11),
                space(9, -10),
                space(7, -9),
                action(ActionType.HOLD),
                unitAt(7, -10),
            ),
        ),
    )
}

private val R08_NUDGES = listOf(
            listOf(
                "A Blue minion is worth 2 coins and Ignatia 4. That's your 6: how do you get Ignatia with no card" +
                " left?",
                "Cutter's way to the front is a hook, and it needs an obstacle in a straight line.",
                "Play Grappling Bolt on Cutter, next to the Blue melee minion, and Rummage on Razzle, who moves.",
            ),
            listOf(
                "Cutter stands next to a Blue minion now.",
                "Walk the Plank can defeat a minion next to you. Razzle keeps walking.",
                "Play Walk the Plank on Cutter, defeating the Blue melee minion, and Stunt Doubles on Razzle, who" +
                " moves.",
            ),
            listOf(
                "Ignatia fast travels this turn, and you know the space. Her spare card is what stands between " +
                "you and 4 coins.",
                "Brace for Impact moves Cutter 3 spaces in a straight line, next to her, and it costs her a card.",
                "Play Brace for Impact on Cutter and Crowd Control on Razzle.",
            ),
            listOf(
                "Ignatia attacks Cutter first. Keep the card that holds, then answer.",
                "X Marks the Spot defends. Bold Thrust then attacks her, and she has nothing left.",
                "Play Bold Thrust on Cutter, attacking Ignatia, and Group Performance on Razzle; defend with X " +
                "Marks the Spot.",
            ),
)
