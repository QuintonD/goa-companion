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

/** Full round 2, mined from self-play (g772_r7_RED_win; ADR-052, ENCODING.md "Full rounds"). */
object R02ShePlaysAnAttack : Puzzle() {
    override val concept = "R02"

    override val known = KnownPlays(
        cards = mapOf(
            "ignatia" to listOf("ignatia-blue-2b", "ignatia-gold", "ignatia-green-2b", "ignatia-red-2b"),
            "xargatha" to listOf("xargatha-green-2b", "xargatha-red-2b", "xargatha-blue-2a", "xargatha-gold"),
        ),
        actions = mapOf(
            "ignatia" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(5, -4))),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(9, -6))),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(10, -8))),
            ),
            "xargatha" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY)),
                2 to listOf(Choice.Action(ActionType.HOLD)),
                3 to listOf(Choice.Action(ActionType.PRIMARY)),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r02",
        number = 2,
        title = "She Plays an Attack",
        difficulty = 2,
        theme = "A bonus you can only plan because you know their card",
        heroes = listOf("tigerclaw", "sabina", "ignatia", "xargatha"),
        goal = PuzzleGoal.WinGame("Win the game this round.", Deadline.END_OF_ROUND),
        intro = "Round 7. You play Tigerclaw and Sabina for Red. Blue has 2 Life counters left, and defeating " +
            "Xargatha costs both. Sabina's Gunslinger gets +3 Attack if its target played an attack card this " +
            "turn. You know what Xargatha will play in turn 4.",
        nudges = R02_NUDGES.first(),
        nudgesByTurn = R02_NUDGES,
        explanation = "Turn 1: Ignatia's Searing Heat moves Sabina 2 spaces, as planned. Both of you use your cards' " +
            "Movement to stay in the Zone." +
            "\n\n" +
            "Turn 2: Point Blank Shot's Movement takes Sabina 1 space, and Blend Into Shadows places Tigerclaw " +
            "near the Blue melee minion. Ignatia walks next to Xargatha." +
            "\n\n" +
            "Turn 3: Poisoned Dagger's Movement and Back to Back are spent. Gunslinger stays in Sabina's hand." +
            "\n\n" +
            "Turn 4: Xargatha's Cleave is an attack card, so Gunslinger gets its +3 even though she does nothing " +
            "with Cleave: 7 in all. She defends with her last card, Siren's Call: 3, +1 for her adjacent heavy " +
            "minion, 4, not enough. She is defeated (p.13), and Blue spends its last 2 Life counters: the game is" +
            " over." +
            "\n\n" +
            "The computer at strength 7, playing your heroes, doesn't find it, and neither does strength 10.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense, Minion Defense Modifiers",
            "p.9 Range",
        ),
        known = known.display(
            mapOf(
                "ignatia" to mapOf(
                    1 to "Searing Heat: moves Sabina 2 spaces along a line, to the space outlined.",
                    2 to "Moves to the space outlined, next to Xargatha.",
                    3 to "Moves to the space outlined, next to Xargatha.",
                ),
                "xargatha" to mapOf(
                    1 to "Plays her skill.",
                    2 to "Holds.",
                    3 to "Plays Petrifying Stare as her skill.",
                    4 to "Holds with Cleave.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("tigerclaw", "sabina")

    override fun PositionBuilder.setUp() {
        round = 7
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 2)
        hero("tigerclaw", Team.RED, Hex(6, -9)) {
            level = 5
            coins = 5
            hand("tigerclaw-gold", "tigerclaw-silver", "tigerclaw-green-2b", "tigerclaw-blue-2b", "tigerclaw-red-3a")
            items("tigerclaw-red-2a", "tigerclaw-green-2a", "tigerclaw-blue-2a", "tigerclaw-red-3b")
            deck("tigerclaw-red-1", "tigerclaw-green-1", "tigerclaw-blue-1")
        }
        hero("sabina", Team.RED, Hex(7, -6)) {
            level = 5
            coins = 4
            hand("sabina-gold", "sabina-silver", "sabina-red-2a", "sabina-blue-2a", "sabina-green-3b")
            items("sabina-red-2b", "sabina-green-2a", "sabina-blue-2b", "sabina-green-3a")
            deck("sabina-red-1", "sabina-green-1", "sabina-blue-1")
        }
        hero("ignatia", Team.BLUE, Hex(8, -5)) {
            level = 4
            coins = 2
            hand("ignatia-gold", "ignatia-silver", "ignatia-red-2b", "ignatia-green-2b", "ignatia-blue-2b")
            items("ignatia-red-2a", "ignatia-green-2a", "ignatia-blue-2a")
            deck("ignatia-red-1", "ignatia-green-1", "ignatia-blue-1")
        }
        hero("xargatha", Team.BLUE, Hex(9, -7)) {
            level = 4
            coins = 4
            hand("xargatha-gold", "xargatha-silver", "xargatha-red-2b", "xargatha-green-2b", "xargatha-blue-2a")
            items("xargatha-red-2a", "xargatha-green-2a", "xargatha-blue-2b")
            deck("xargatha-red-1", "xargatha-green-1", "xargatha-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -11))
        minion(Team.RED, MinionType.MELEE, Hex(7, -10))
        minion(Team.BLUE, MinionType.RANGED, Hex(9, -9))
        minion(Team.RED, MinionType.HEAVY, Hex(8, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(9, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(11, -8))
    }

    override val solution = listOf(
        card("tigerclaw-gold"),
        card("sabina-green-3b"),
        action(ActionType.MOVEMENT),
        space(6, -10),
        action(ActionType.MOVEMENT),
        space(6, -6),
        card("tigerclaw-silver"),
        card("sabina-gold"),
        action(ActionType.MOVEMENT),
        space(6, -7),
        action(ActionType.PRIMARY),
        space(8, -12),
        card("tigerclaw-green-2b"),
        card("sabina-silver"),
        action(ActionType.HOLD),
        action(ActionType.MOVEMENT),
        space(9, -12),
        card("tigerclaw-blue-2b"),
        card("sabina-red-2a"),
        action(ActionType.MOVEMENT),
        space(8, -12),
        action(ActionType.PRIMARY),
        hero("xargatha"),
    )

    override val wrongTries = listOf(
        WrongTry(
            "The computer at strength 7 on your heroes",
            listOf(
                card("tigerclaw-gold"),
                card("sabina-red-2a"),
                action(ActionType.MOVEMENT),
                decline(),
                action(ActionType.MOVEMENT),
                space(8, -9),
                card("tigerclaw-blue-2b"),
                card("sabina-green-3b"),
                action(ActionType.MOVEMENT),
                space(8, -12),
                action(ActionType.MOVEMENT),
                space(10, -10),
                card("tigerclaw-red-3a"),
                card("sabina-gold"),
                action(ActionType.PRIMARY),
                action(ActionType.PRIMARY),
                space(7, -11),
                space(8, -12),
                card("tigerclaw-silver"),
                card("sabina-silver"),
                action(ActionType.HOLD),
                action(ActionType.HOLD),
            ),
        ),
    )
}

private val R02_NUDGES = listOf(
            listOf(
                "Look along Xargatha's cards. Which turn is she playing an attack card, and which of your attacks" +
                " cares?",
                "Gunslinger has to be left for turn 4, and Sabina has to end up in range of her by then.",
                "Play Blink Strike on Tigerclaw, who moves, and Covering Fire on Sabina, who moves.",
            ),
            listOf(
                "Ignatia has pushed Sabina about. Walk back in.",
                "Tigerclaw needs to be near too, for the Zone and for the assist.",
                "Play Blend Into Shadows on Tigerclaw and Point Blank Shot on Sabina, who moves.",
            ),
            listOf(
                "This is a turn for spending what's left.",
                "Keep Gunslinger. Sabina needn't go further.",
                "Play Poisoned Dagger on Tigerclaw, who moves, and Back to Back on Sabina.",
            ),
            listOf(
                "Xargatha has played Cleave, an attack card. What is Gunslinger worth now?",
                "Attack 7. Her only card left is Siren's Call, which gives her 4 here.",
                "Play Parry on Tigerclaw and Gunslinger on Sabina, attacking Xargatha.",
            ),
)
