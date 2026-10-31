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

/** Full round 5, mined from self-play (g2499_r5_RED_coins-6; ADR-052, ENCODING.md "Full rounds"). */
object R05ReloadTheSnipe : Puzzle() {
    override val concept = "R05"

    override val known = KnownPlays(
        cards = mapOf(
            "dodger" to listOf("dodger-green-2a", "dodger-blue-2a", "dodger-silver", "dodger-red-2a"),
            "nebkher" to listOf("nebkher-green-2a", "nebkher-silver", "nebkher-blue-1", "nebkher-gold"),
        ),
        actions = mapOf(
            "dodger" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(5, -8))),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -9))),
                3 to listOf(Choice.Action(ActionType.HOLD)),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
            "nebkher" to mapOf(
                1 to
                    listOf(
                        Choice.Action(ActionType.PRIMARY),
                        Choice.Space(Hex(2, -2)),
                        Choice.Space(Hex(1, -2)),
                        Choice.Decline("Skip"),
                    ),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Decline("Stop")),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -3))),
                4 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Option(1, "Defeat a minion adjacent to you")),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r05",
        number = 5,
        title = "Reload the Snipe",
        difficulty = 2,
        theme = "Playing a card now so another can use it later",
        heroes = listOf("swift", "sabina", "dodger", "nebkher"),
        goal = PuzzleGoal.GainCoins(6, Deadline.END_OF_ROUND, "Gain 6 coins by the end of the round."),
        intro = "Round 5. You play Swift and Sabina for Red. Dodger is worth 4 coins to the hero who defeats him and " +
            "2 to the other. Swift's Reload! can perform the primary action of his rightmost resolved card, " +
            "whichever card that is by then.",
        nudges = R05_NUDGES.first(),
        nudgesByTurn = R05_NUDGES,
        explanation = "Turn 1: Shootout defeats a Blue melee minion: 2 coins. Swift plays Mark for Death." +
            "\n\n" +
            "Turn 2: Sabina takes Point Blank Shot's Movement to Side Zone 2, Bounce moves Swift 2 spaces in a " +
            "straight line, and Dodger comes next to Sabina as planned." +
            "\n\n" +
            "Turn 3: This is the turn that decides it. Swift plays Snipe and uses only its Movement, to a space " +
            "at maximum range and in a straight line from Dodger. Most of his options here are proven to lose." +
            "\n\n" +
            "Turn 4: Snipe is now Swift's rightmost resolved card, so Reload! performs its primary action. Attack" +
            " 6 reaches Dodger, whose Dread Razor gives only 2, and he is defeated (p.13): Swift gains 4 coins " +
            "and Sabina 2 for the assist." +
            "\n\n" +
            "The computer at strength 7, playing your heroes, doesn't find it, and neither does strength 10. " +
            "Using a card only for its Movement, a turn before you need its Attack, is the idea.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.19 Straight line",
            "p.9 Range",
        ),
        known = known.display(
            mapOf(
                "dodger" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Moves to the space outlined, next to Sabina.",
                    3 to "Holds.",
                    4 to "Holds.",
                ),
                "nebkher" to mapOf(
                    1 to "Multiple Projections: two Illusions on the spaces outlined.",
                    2 to "Diabolical Laughter, and stops there.",
                    3 to "Moves to the space outlined.",
                    4 to "Mind Grip, which defeats a Red melee minion.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("swift", "sabina")

    override fun PositionBuilder.setUp() {
        round = 5
        tieBreaker = Team.BLUE
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 1)
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 4)
        hero("swift", Team.RED, Hex(10, -7)) {
            level = 3
            coins = 1
            hand("swift-gold", "swift-silver", "swift-red-1", "swift-green-2b", "swift-blue-2a")
            items("swift-green-2a", "swift-blue-2b")
            deck("swift-green-1", "swift-blue-1")
        }
        hero("sabina", Team.RED, Hex(4, -9)) {
            level = 4
            coins = 1
            hand("sabina-gold", "sabina-silver", "sabina-red-2b", "sabina-green-2b", "sabina-blue-2b")
            items("sabina-red-2a", "sabina-green-2a", "sabina-blue-2a")
            deck("sabina-red-1", "sabina-green-1", "sabina-blue-1")
        }
        hero("dodger", Team.BLUE, Hex(7, -8)) {
            level = 4
            coins = 3
            hand("dodger-gold", "dodger-silver", "dodger-red-2a", "dodger-green-2a", "dodger-blue-2a")
            items("dodger-red-2b", "dodger-green-2b", "dodger-blue-2b")
            deck("dodger-red-1", "dodger-green-1", "dodger-blue-1")
        }
        hero("nebkher", Team.BLUE, Hex(4, -3)) {
            level = 3
            coins = 2
            hand("nebkher-gold", "nebkher-silver", "nebkher-blue-1", "nebkher-red-2a", "nebkher-green-2a")
            items("nebkher-red-2b", "nebkher-green-2b")
            deck("nebkher-red-1", "nebkher-green-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.RANGED, Hex(3, -4))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
    }

    override val solution = listOf(
        card("swift-green-2b"),
        card("sabina-red-2b"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        card("swift-silver"),
        card("sabina-gold"),
        action(ActionType.MOVEMENT),
        space(4, -10),
        action(ActionType.PRIMARY),
        space(10, -9),
        card("swift-red-1"),
        card("sabina-silver"),
        action(ActionType.HOLD),
        action(ActionType.MOVEMENT),
        space(7, -9),
        card("swift-gold"),
        card("sabina-green-2b"),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "The computer at strength 7 on your heroes",
            listOf(
                card("swift-blue-2a"),
                card("sabina-red-2b"),
                hero("sabina"),
                action(ActionType.PRIMARY),
                action(ActionType.PRIMARY),
                space(6, -7),
                number(2),
                card("swift-gold"),
                card("sabina-green-2b"),
                action(ActionType.PRIMARY),
                space(3, -7),
                number(2),
                action(ActionType.MOVEMENT),
                space(2, -8),
                card("swift-silver"),
                card("sabina-silver"),
                action(ActionType.PRIMARY),
                space(1, -7),
                action(ActionType.HOLD),
                card("swift-red-1"),
                card("sabina-gold"),
                action(ActionType.MOVEMENT),
                space(3, -9),
                action(ActionType.HOLD),
            ),
        ),
    )
}

private val R05_NUDGES = listOf(
    listOf(
        "Two coins are there for the taking now. The rest take three more turns to line up.",
        "Sabina can shoot a Blue minion at once. Swift has something to do with an enemy minion.",
        "Play Mark for Death on Swift and Shootout on Sabina, and attack the Blue melee minion with " +
            "Shootout.",
    ),
    listOf(
        "Dodger is coming next to Sabina. Where does Swift need to be two turns from now?",
        "Swift has a shot that needs a long straight line. Start walking; Sabina steps away.",
        "Play Bounce on Swift and Point Blank Shot on Sabina; Sabina takes the Movement.",
    ),
    listOf(
        "Swift has one attack that reaches Dodger from far away, and he won't use it this turn. Play it " +
            "anyway.",
        "Snipe's Attack is for next turn. This turn its Movement takes Swift to a space at Snipe's " +
            "maximum range, in a straight line from where Dodger will hold.",
        "Play Snipe on Swift, taking its Movement, and Back to Back on Sabina.",
    ),
    listOf(
        "Which card is Swift's rightmost resolved card now? What would Reload! do with it?",
        "Reload! performs Snipe's primary action: Attack 6, at maximum range in a straight line.",
        "Play Reload! on Swift, performing Snipe at Dodger, and Close Support on Sabina.",
    ),
)
