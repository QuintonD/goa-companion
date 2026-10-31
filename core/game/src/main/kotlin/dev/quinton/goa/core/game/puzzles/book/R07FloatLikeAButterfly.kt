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
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 21, mined from self-play (g6470_r3_BLUE_push; ADR-052, ENCODING.md "Full rounds"). */
object R07FloatLikeAButterfly : Puzzle() {
    override val concept = "R07"

    override val known = KnownPlays(
        cards = mapOf(
            "tali" to listOf("tali-green-1", "tali-silver", "tali-red-2b", "tali-gold"),
            "bain" to listOf("bain-red-1", "bain-blue-1", "bain-green-2b", "bain-gold"),
        ),
        actions = mapOf(
            "tali" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -3))),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Decline("Skip")),
                3 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Unit("m1002")),
                4 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -4))),
            ),
            "bain" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(0, -1))),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(1, -1))),
                3 to listOf(Choice.Action(ActionType.HOLD)),
                4 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Decline("Skip")),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r07",
        number = 21,
        title = "Float Like a Butterfly",
        difficulty = 3,
        theme = "Gold cards swapped in sequence, Minion Battle arithmetic",
        heroes = listOf("takahide", "swift", "tali", "bain"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_ROUND, "Push the lane by the end of the round."),
        intro = "Round 3. You play Takahide and Swift for Blue. The Battle Zone holds the Red heavy minion against " +
            "the Blue heavy and two Blue melee minions, and you know what is coming for them. Takahide's gold " +
            "cards are in the deck as well as his hand: Bushido swaps one for another.",
        nudges = R07_NUDGES.first(),
        nudgesByTurn = R07_NUDGES,
        explanation =
            "Turn 1: Come to Aid's Movement starts Takahide across the board. Calculated Risk stays in his hand " +
                "on this line." +
                "\n\n" +
                "Turn 2: Bushido swaps a gold card: Float like a Butterfly comes from the deck into his hand, ready " +
                "for next turn. Tali's plan comes to nothing, as she has no gold card to perform." +
                "\n\n" +
                "Turn 3: Tali defeats a Blue melee minion, leaving Blue with two. Float like a Butterfly swaps itself" +
                " for Sting like a Bee and moves Takahide up to 5 spaces: he ends next to Bain and the Red heavy " +
                "minion." +
                "\n\n" +
                "Turn 4: Bain acts before Takahide and has two targets. If he attacks the Blue melee minion, Lead " +
                "from the Front defeats the Red heavy: with no other Red minion in the Zone it isn't immune (p.13), " +
                "Red has none left, and the lane is pushed. If he attacks Takahide at 4, Calculated Risk defends: 4, " +
                "+1 for the item, -1 for the adjacent Red heavy, makes 4 (p.14). Not defending wins as well. Either " +
                "way the Minion Battle is Red 1, Blue 2, Red removes its last minion and Blue pushes the lane (p.8).",
        rules = listOf(
            "p.8 Minion Battle, Push the Lane",
            "p.13 Heavy Minion Immunity",
            "p.14 Defense, Minion Defense Modifiers",
            "p.7 Tied Initiative",
        ),
        known = known.display(
            mapOf(
                "tali" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Performs its primary action, with no gold card to perform.",
                    3 to "Attacks the Blue melee minion outlined, the one 2 spaces from Bain.",
                    4 to "Moves to the space outlined.",
                ),
                "bain" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Moves to the space outlined.",
                    3 to "Holds.",
                    4 to "Attacks a unit next to it, of its choice.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("takahide", "swift")

    override fun PositionBuilder.setUp() {
        round = 3
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 3)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        hero("tali", Team.RED, Hex(3, -5)) {
            level = 3
            coins = 2
            hand("tali-gold", "tali-silver", "tali-green-1", "tali-red-2b", "tali-blue-2b")
            items("tali-red-2a", "tali-blue-2a")
            deck("tali-red-1", "tali-blue-1")
        }
        hero("bain", Team.RED, Hex(0, -5)) {
            level = 2
            coins = 2
            hand("bain-gold", "bain-silver", "bain-red-1", "bain-blue-1", "bain-green-2b")
            items("bain-green-2a")
            deck("bain-green-1")
        }
        hero("takahide", Team.BLUE, Hex(3, -7)) {
            level = 3
            coins = 1
            hand("takahide-gold-extra-1", "takahide-silver", "takahide-green-1", "takahide-red-2a", "takahide-blue-2b")
            items("takahide-red-2b", "takahide-blue-2a")
            deck("takahide-gold", "takahide-red-1", "takahide-blue-1")
        }
        hero("swift", Team.BLUE, Hex(3, -8)) {
            level = 2
            coins = 2
            hand("swift-gold", "swift-silver", "swift-red-1", "swift-blue-1", "swift-green-2a")
            items("swift-green-2b")
            deck("swift-green-1")
        }
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
    }

    override val solution = listOf(
        card("takahide-green-1"),
        card("swift-red-1"),
        action(ActionType.HOLD),
        action(ActionType.MOVEMENT),
        space(2, -5),
        card("takahide-silver"),
        card("swift-gold"),
        action(ActionType.MOVEMENT),
        space(3, -9),
        action(ActionType.PRIMARY),
        card("takahide-gold"),
        card("takahide-gold"),
        card("swift-blue-1"),
        action(ActionType.MOVEMENT),
        space(1, -7),
        action(ActionType.PRIMARY),
        card("takahide-gold-extra-1"),
        space(0, 0),
        card("takahide-red-2a"),
        card("swift-green-2a"),
        card("takahide-blue-2b"),
        action(ActionType.MOVEMENT),
        space(0, 1),
        action(ActionType.MOVEMENT),
        space(3, -9),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("takahide-red-2a"),
                card("swift-green-2a"),
                action(ActionType.MOVEMENT),
                space(4, -8),
                action(ActionType.MOVEMENT),
                space(2, -8),
                card("takahide-green-1"),
                card("swift-blue-1"),
                action(ActionType.PRIMARY),
                space(5, -8),
                action(ActionType.MOVEMENT),
                space(2, -8),
                card("takahide-blue-2b"),
                card("swift-gold"),
                action(ActionType.PRIMARY),
                decline(),
                action(ActionType.PRIMARY),
                space(5, -7),
                card("takahide-silver"),
                card("swift-red-1"),
                action(ActionType.HOLD),
                action(ActionType.HOLD),
            ),
        ),
    )
}

private val R07_NUDGES = listOf(
    listOf(
        "Tali takes a Blue minion in turn 3, and Bain goes for something in turn 4. How many must be left" +
            " for the battle to take the Red heavy?",
        "On this line Takahide is next to the Red heavy by turn 4, and his gold cards take him there in " +
            "two turns.",
        "Play Come to Aid on Takahide, moving 2 spaces, and Snipe on Swift, who holds.",
    ),
    listOf(
        "One of Takahide's gold cards moves him 5 spaces. It isn't in his hand yet.",
        "Bushido swaps your gold card for another from the deck: take the one that moves.",
        "Play Bushido on Takahide, taking Float like a Butterfly, and Reload! on Swift, who moves 1 " +
            "space.",
    ),
    listOf(
        "Now is the time to run, and to look at where Bain stands.",
        "Float like a Butterfly swaps itself for Sting like a Bee and moves up to 5 spaces. End next to " +
            "Bain and the Red heavy minion: all 69 other spaces are proven to lose.",
        "Play Float like a Butterfly on Takahide and Steam Jump on Swift, whose Movement takes it next to" +
            " the Blue heavy.",
    ),
    listOf(
        "Bain has to choose: Takahide or the Blue minion. Make both choices lose.",
        "If Bain hits the minion, Takahide can defeat the Red heavy himself. If Bain attacks Takahide, " +
            "the Minion Battle does the work, whether or not he defends.",
        "Play Lead from the Front on Takahide and Pin Down on Swift. You may defend with Calculated Risk " +
            "if Bain attacks.",
    ),
)
