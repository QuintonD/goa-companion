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
 * CONCEPTS.md C27 "Rain of Arrows" (changed: three Red heroes at the item-raised maximum range, at
 * levels that fit round 6; Red has 3 Life counters, so the level 4 Tigerclaw must fall, and the order of
 * the two arrows matters: Tigerclaw stands on a spawn point that feeds Dodger's Shield of Decay).
 */
object P27RainOfArrows : Puzzle() {
    override val concept = "C27"

    override val info = PuzzleInfo(
        id = "p27",
        number = 27,
        title = "Rain of Arrows",
        difficulty = 2,
        theme = "Items and maximum range; Block; Life counters by tier; defense from the board",
        heroes = listOf("silverarrow", "arien", "wuk", "tigerclaw", "xargatha", "dodger"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 6, turn 4, three heroes a side; Red has 3 Life counters left. Silverarrow (level 6) has " +
            "played Rain of Arrows and acts next; among her five items are one Range and two Attack items. " +
            "Tigerclaw (level 4) keeps Parry in hand, Xargatha (level 3) Long Thrust (Defense 4), Dodger " +
            "(level 3) Shield of Decay. Arien and Wuk have already acted.",
        nudges = listOf(
            "Count Red's Life counters, and what each Red hero would cost them. Then count each defense.",
            "Parry blocks only one kind of attack. And Shield of Decay counts the empty spawn points around " +
                "Dodger at the moment she defends.",
            "Shoot Dodger first, then Tigerclaw.",
        ),
        explanation = "Each item adds +1 to the matching value on your cards (p.16): Rain of Arrows has Range " +
            "4 and Attack 3 + 2 = 5, and \"at maximum range\" means exactly four spaces (p.9). Three Red heroes " +
            "stand there; after targeting a hero the card must repeat on a different hero (card text, p.10 " +
            "Repeat), so you pick two, in order. A defeated hero costs Red Life counters equal to his lowest " +
            "card Tier (p.13): Tigerclaw at level 4 has only Tier II cards and costs 2, Dodger and Xargatha 1 " +
            "each, so Tigerclaw must fall. Parry blocks only a non-ranged attack (card text, p.14 Block). " +
            "Xargatha defends 4, +1 for the Red melee minion beside her (p.14): 5 against 5 holds. Dodger's " +
            "Shield of Decay adds 2 only with two or more empty spawn points within 2 spaces: one is empty, " +
            "the other is under Tigerclaw. Shoot her first: 3 + 1 for the minion = 4, she falls. Shoot " +
            "Tigerclaw first and his spawn point is empty when she defends: 3 + 2 + 1 = 6. Order your shots " +
            "for the board as it will be, not as it is.",
        rules = listOf(
            "p.9 Range",
            "p.10 Repeat",
            "p.13 When a hero is defeated",
            "p.14 Block, Defense total, Minion defense modifiers",
            "p.16 Item bonus",
            "p.17 Empty",
        ),
    )

    override val playerSeats = setOf("silverarrow")

    override fun PositionBuilder.setUp() {
        round = 6
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("silverarrow", Team.BLUE, Hex(2, -3)) {
            level = 6
            played("silverarrow-red-3b")
            resolved(1, "silverarrow-gold")
            resolved(2, "silverarrow-silver")
            resolved(3, "silverarrow-green-2a")
            hand("silverarrow-blue-3a")
            items(
                "silverarrow-red-2b",
                "silverarrow-red-3a",
                "silverarrow-green-2b",
                "silverarrow-blue-2b",
                "silverarrow-blue-3b",
            )
        }
        // Already acted this turn: Spell Break (13).
        hero("arien", Team.BLUE, Hex(-1, 1)) {
            level = 3
            resolved(1, "arien-gold")
            resolved(2, "arien-red-2a")
            resolved(3, "arien-green-2a")
            resolved(4, "arien-silver")
            hand("arien-blue-1")
            items("arien-red-2b", "arien-green-2b")
        }
        // Already acted this turn: Tree Slam (11 + 2 for his Initiative items).
        hero("wuk", Team.BLUE, Hex(-1, -3)) {
            level = 3
            resolved(1, "wuk-silver")
            resolved(2, "wuk-red-2b")
            resolved(3, "wuk-green-1")
            resolved(4, "wuk-gold")
            hand("wuk-blue-2a")
            items("wuk-red-2a", "wuk-blue-2b")
        }
        hero("tigerclaw", Team.RED, Hex(1, -6)) {
            level = 4
            played("tigerclaw-green-2a")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-red-2b")
            hand("tigerclaw-blue-2b")
            items("tigerclaw-red-2a", "tigerclaw-green-2b", "tigerclaw-blue-2a")
        }
        hero("xargatha", Team.RED, Hex(3, -7)) {
            level = 3
            played("xargatha-green-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-blue-2a")
            hand("xargatha-red-2b")
            items("xargatha-red-2a", "xargatha-blue-2b")
        }
        hero("dodger", Team.RED, Hex(2, -7)) {
            level = 3
            played("dodger-green-2a")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-2b")
            hand("dodger-blue-1")
            items("dodger-red-2a", "dodger-green-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(2, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(5, -7))
        phase = PositionPhase.Resolving(next = "silverarrow")
    }

    override val solution = listOf(action(ActionType.PRIMARY), hero("dodger"), hero("tigerclaw"))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Tigerclaw first: he falls, and his empty spawn point gives Dodger her +2",
            listOf(action(ActionType.PRIMARY), hero("tigerclaw")),
        ),
        WrongTry(
            "Dodger, then Xargatha: Xargatha holds 5 against 5, and Dodger alone costs 1 counter",
            listOf(action(ActionType.PRIMARY), hero("dodger"), hero("xargatha")),
        ),
        WrongTry(
            "Xargatha first: she holds 5 against 5",
            listOf(action(ActionType.PRIMARY), hero("xargatha")),
        ),
    )
}
