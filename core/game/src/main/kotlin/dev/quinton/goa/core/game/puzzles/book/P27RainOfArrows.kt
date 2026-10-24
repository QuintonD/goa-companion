package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C27 "Rain of Arrows" (changed: all three Red heroes stand at the item-raised maximum
 * range; Tigerclaw keeps Dodge, which blocks ranged attacks, and Xargatha keeps Charm, Defense 3).
 */
object P27RainOfArrows : Puzzle() {
    override val concept = "C27"

    override val info = PuzzleInfo(
        id = "p27",
        number = 27,
        title = "Rain of Arrows",
        difficulty = 2,
        theme = "Items change maximum range; Block",
        heroes = listOf("silverarrow", "wuk", "arien", "tigerclaw", "dodger", "xargatha"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 6, turn 4, three heroes a side. Silverarrow (level 6) has played Rain of Arrows and " +
            "acts next; among her five items are one Range and one Attack item. Tigerclaw holds Dodge, " +
            "Xargatha holds Charm (Defense 3), Dodger has no cards left in hand. Red has two Life counters " +
            "left. Wuk and Arien have already acted.",
        nudges = listOf(
            "What is your real Range this turn, and which heroes stand exactly that far away?",
            "Dodge has no Defense value, but read what it does against an arrow.",
            "Shoot Dodger and Xargatha.",
        ),
        explanation = "Each item adds +1 to the matching value on your cards (p.16), so Rain of Arrows has " +
            "Range 4 and Attack 4, and \"at maximum range\" means exactly four spaces (p.9). Three Red heroes " +
            "stand there. After targeting a hero the card must repeat on a different hero (card text, p.10 " +
            "Repeat), so you pick two. Tigerclaw's Dodge shows no Defense value, but it blocks a ranged attack " +
            "outright (p.14 Block). Xargatha's Charm defends only 3 against 4, and Dodger has nothing (p.13): " +
            "two tier I heroes defeated cost Red its last two Life counters.",
        rules = listOf("p.9 Range", "p.10 Repeat", "p.13 When a hero is defeated", "p.14 Block", "p.16 Item bonus"),
    )

    override val playerSeats = setOf("silverarrow")

    override fun PositionBuilder.setUp() {
        round = 6
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 2)
        hero("silverarrow", Team.BLUE, Hex(1, -5)) {
            level = 6
            played("silverarrow-red-3b")
            resolved(1, "silverarrow-gold")
            resolved(2, "silverarrow-silver")
            resolved(3, "silverarrow-green-2a")
            hand("silverarrow-blue-3a")
            items(
                "silverarrow-red-2a",
                "silverarrow-red-3a",
                "silverarrow-green-2b",
                "silverarrow-blue-2a",
                "silverarrow-blue-3b",
            )
        }
        hero("wuk", Team.BLUE, Hex(-1, -3)) {
            resolved(1, "wuk-gold")
            resolved(2, "wuk-silver")
            resolved(3, "wuk-red-1")
            resolved(4, "wuk-green-1")
            hand("wuk-blue-1")
        }
        hero("arien", Team.BLUE, Hex(-1, 1)) {
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-red-1")
            resolved(4, "arien-green-1")
            hand("arien-blue-1")
        }
        hero("tigerclaw", Team.RED, Hex(5, -7)) {
            played("tigerclaw-green-1")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-red-1")
            hand("tigerclaw-blue-1")
        }
        hero("dodger", Team.RED, Hex(1, -1)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-1")
            discard("dodger-blue-1")
            hand()
        }
        hero("xargatha", Team.RED, Hex(3, -3)) {
            played("xargatha-red-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-blue-1")
            hand("xargatha-green-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "silverarrow")
    }

    override val solution = listOf(action(ActionType.PRIMARY), hero("dodger"), hero("xargatha"))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Shoot Tigerclaw first: Dodge blocks the arrow",
            listOf(action(ActionType.PRIMARY), hero("tigerclaw")),
        ),
        WrongTry(
            "Dodger, then Tigerclaw: Dodge blocks the second arrow",
            listOf(action(ActionType.PRIMARY), hero("dodger"), hero("tigerclaw")),
        ),
    )
}
