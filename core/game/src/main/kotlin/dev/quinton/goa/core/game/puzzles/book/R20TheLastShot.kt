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
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 19, mined from self-play (g4321_r5_BLUE_defeat-xargatha; ADR-052, ENCODING.md "Full rounds"). */
object R20TheLastShot : Puzzle() {
    override val concept = "R20"

    override val known = KnownPlays(
        cards = mapOf(
            "xargatha" to listOf("xargatha-red-2a", "xargatha-gold", "xargatha-silver", "xargatha-green-2a"),
            "ursafar" to listOf("ursafar-red-2b", "ursafar-green-2a", "ursafar-gold", "ursafar-blue-2a"),
        ),
        actions = mapOf(
            "xargatha" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(8, -10))),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Unit("m1000"), Choice.Yes),
            ),
            "ursafar" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY)),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(7, -6))),
                3 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(7, -7))),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r20",
        number = 19,
        title = "The Last Shot",
        difficulty = 2,
        theme = "A shot kept for turn 4, a hand emptied on the way",
        heroes = listOf("misa", "brynn", "xargatha", "ursafar"),
        goal = PuzzleGoal.DefeatHero("xargatha", Deadline.END_OF_ROUND, "Defeat Xargatha by the end of the round."),
        intro = "Round 5. You play Misa and Brynn for Blue. Xargatha plays a card every turn, and a hero defends by " +
            "discarding a card. A hero with no cards left passes (p.7).",
        nudges = R20_NUDGES.first(),
        nudgesByTurn = R20_NUDGES,
        explanation =
            "This is one way through, and other plans win too: making her spend a card before turn 4 is proven " +
                "not to be needed. In this line it is what empties her hand." +
                "\n\n" +
                "Turn 1: Ursafar takes a Blue melee minion and Xargatha walks up, as planned. Misa plays Watch How I " +
                "Soar and Brynn plays Decoy without using it." +
                "\n\n" +
                "Turn 2: Xargatha's Cleave takes another Blue melee minion. Swoop In places Misa next to her, and " +
                "Bear Trap's Movement brings Brynn next to her as well." +
                "\n\n" +
                "Turn 3: Familiar Ground attacks her at 3, and she has to spend a card: if she declines, she is " +
                "defeated (p.13). Matter of Honor then attacks at 6, and it costs her another. Two cards in one turn," +
                " on top of one played each turn, leave her hand empty." +
                "\n\n" +
                "Turn 4: She has no cards and passes (p.7). Split Attack shoots her at 4, she has no card to defend " +
                "with, and she is defeated: 4 coins for Brynn and 2 for Misa's assist.",
        rules = listOf(
            "p.7 Play one card",
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense",
        ),
        known = known.display(
            mapOf(
                "xargatha" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Attacks the Blue melee minion outlined.",
                ),
                "ursafar" to mapOf(
                    1 to "Attacks a unit in its reach.",
                    2 to "Moves to the space outlined.",
                    3 to "Attacks a unit in its reach, and may step to the space outlined.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("misa", "brynn")

    override fun PositionBuilder.setUp() {
        round = 5
        tieBreaker = Team.BLUE
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        hero("xargatha", Team.RED, Hex(6, -8)) {
            level = 4
            coins = 2
            hand("xargatha-gold", "xargatha-silver", "xargatha-red-2a", "xargatha-green-2a", "xargatha-blue-2b")
            items("xargatha-red-2b", "xargatha-green-2b", "xargatha-blue-2a")
            deck("xargatha-red-1", "xargatha-green-1", "xargatha-blue-1")
        }
        hero("ursafar", Team.RED, Hex(6, -4)) {
            level = 4
            coins = 3
            hand("ursafar-gold", "ursafar-silver", "ursafar-red-2b", "ursafar-green-2a", "ursafar-blue-2a")
            items("ursafar-red-2a", "ursafar-green-2b", "ursafar-blue-2b")
            deck("ursafar-red-1", "ursafar-green-1", "ursafar-blue-1")
        }
        hero("misa", Team.BLUE, Hex(6, -10)) {
            level = 4
            hand("misa-gold", "misa-silver", "misa-red-2a", "misa-green-2b", "misa-blue-2a")
            items("misa-red-2b", "misa-green-2a", "misa-blue-2b")
            deck("misa-red-1", "misa-green-1", "misa-blue-1")
        }
        hero("brynn", Team.BLUE, Hex(7, -7)) {
            level = 3
            coins = 3
            hand("brynn-gold", "brynn-silver", "brynn-green-1", "brynn-red-2b", "brynn-blue-2a")
            items("brynn-red-2a", "brynn-blue-2b")
            deck("brynn-red-1", "brynn-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -11))
        minion(Team.RED, MinionType.MELEE, Hex(7, -9))
        minion(Team.BLUE, MinionType.RANGED, Hex(9, -9))
        minion(Team.RED, MinionType.HEAVY, Hex(8, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(9, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(11, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(7, -5))
    }

    override val solution = listOf(
        card("misa-gold"),
        card("brynn-silver"),
        action(ActionType.HOLD),
        action(ActionType.PRIMARY),
        decline(),
        card("misa-silver"),
        card("brynn-green-1"),
        action(ActionType.PRIMARY),
        space(7, -10),
        action(ActionType.MOVEMENT),
        space(8, -9),
        card("misa-red-2a"),
        card("brynn-gold"),
        action(ActionType.PRIMARY),
        option(0),
        hero("xargatha"),
        action(ActionType.PRIMARY),
        hero("xargatha"),
        card("misa-green-2b"),
        card("brynn-red-2b"),
        action(ActionType.PRIMARY),
        hero("xargatha"),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("misa-silver"),
                card("brynn-red-2b"),
                action(ActionType.PRIMARY),
                hero("xargatha"),
                action(ActionType.PRIMARY),
                space(7, -8),
                card("misa-gold"),
                card("brynn-green-1"),
                action(ActionType.PRIMARY),
                option(1),
                action(ActionType.PRIMARY),
                card("misa-green-2b"),
                card("brynn-gold"),
                action(ActionType.PRIMARY),
                unitAt(8, -8),
                card("brynn-blue-2a"),
                action(ActionType.MOVEMENT),
                space(7, -9),
                card("misa-red-2a"),
                card("brynn-silver"),
                action(ActionType.MOVEMENT),
                space(4, -9),
            ),
        ),
    )
}

private val R20_NUDGES = listOf(
    listOf(
        "Count Xargatha's cards turn by turn. How many defenses can she afford, and how many attacks can " +
            "you land on her in one turn?",
        "On this line nobody attacks yet, and neither of you moves.",
        "Play Watch How I Soar on Misa and Decoy on Brynn, without using it.",
    ),
    listOf(
        "Xargatha defeats a minion and stays put. Get next to her, both of you.",
        "Swoop In places Misa next to two or more enemy units. Bear Trap's Movement does the same for " +
            "Brynn.",
        "Play Swoop In on Misa and Bear Trap on Brynn, moving next to Xargatha.",
    ),
    listOf(
        "Even a small attack must be answered, and she only has so many cards.",
        "Brynn first with Attack 3, then Misa with Attack 6: each one costs her a card.",
        "Play Matter of Honor on Misa and Familiar Ground on Brynn, and both attack Xargatha.",
    ),
    listOf(
        "On this line she has no cards at all now. Anything that reaches her will do.",
        "Split Attack can reach a unit in range, and she can't defend.",
        "Play Living Tornado on Misa and Split Attack on Brynn, attacking Xargatha.",
    ),
)
