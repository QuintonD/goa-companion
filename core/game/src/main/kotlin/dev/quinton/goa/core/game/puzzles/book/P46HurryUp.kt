package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C46 "Hurry Up!". */
object P46HurryUp : Puzzle() {
    override val concept = "C46"

    override val info = PuzzleInfo(
        id = "p46",
        number = 46,
        title = "Hurry Up!",
        difficulty = 2,
        theme = "Changing initiative, items and ties",
        heroes = listOf("hanu", "brogan", "tigerclaw", "xargatha"),
        goal = PuzzleGoal.DefeatHero("tigerclaw", Deadline.END_OF_TURN, "Defeat Tigerclaw this turn."),
        intro = "Round 3, turn 4: play your cards. Hanu holds Hurry up! and There and Back Again, Brogan " +
            "holds Onslaught and Mighty Punch; each has one Initiative item. Tigerclaw's only card is Blink " +
            "Strike (13), Xargatha's is Charm (5). Blue shows on the Tie Breaker coin.",
        nudges = listOf(
            "Tigerclaw will have no card left to defend with, but at 13 he moves first and steps away from " +
                "Brogan.",
            "Hurry up! sets the printed Initiative of a card to 11: that can slow a card down as well as " +
                "speed one up, and items still add.",
            "Play Hurry up! and Onslaught, and set Tigerclaw's Blink Strike to 11.",
        ),
        explanation = "With their items Hanu's Hurry up! shows 13 and Brogan's Onslaught 12 (p.16). Hanu ties " +
            "Tigerclaw's Blink Strike at 13; Blue shows, so Hanu goes first and then flips the coin (p.7 Tied " +
            "Initiative). Speeding Brogan up does nothing: Onslaught is already printed 11, so it stays 12, and " +
            "Mighty Punch would reach only 12 too, still behind Tigerclaw. Slow Tigerclaw down instead: Hurry " +
            "up! works on any hero in range (card text), and Blink Strike, printed 11, shows 11. Initiative is " +
            "checked again after every card (p.7), so Brogan's 12 now goes before him, and Onslaught takes a " +
            "hero who played his last card and has nothing to defend with (p.14). There and Back Again (12) is " +
            "too slow and makes the hero it swaps with immune this turn.",
        rules = listOf(
            "p.7 Check the Initiative, Tied Initiative",
            "p.14 Defense",
            "p.16 Items",
        ),
    )

    override val playerSeats = setOf("hanu", "brogan")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("hanu", Team.BLUE, Hex(-1, -1)) {
            level = 3
            resolved(1, "hanu-gold")
            resolved(2, "hanu-red-2a")
            resolved(3, "hanu-green-1")
            hand("hanu-silver", "hanu-blue-2a")
            items("hanu-red-2b", "hanu-blue-2b")
        }
        hero("brogan", Team.BLUE, Hex(2, -2)) {
            level = 3
            resolved(1, "brogan-red-2b")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-green-1")
            hand("brogan-gold", "brogan-blue-2a")
            items("brogan-red-2a", "brogan-blue-2b")
        }
        hero("tigerclaw", Team.RED, Hex(3, -3)) {
            level = 2
            resolved(1, "tigerclaw-red-2a")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-green-1")
            discard("tigerclaw-blue-1")
            hand("tigerclaw-gold")
            items("tigerclaw-red-2b")
        }
        hero("xargatha", Team.RED, Hex(4, -2)) {
            level = 2
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-red-2a")
            discard("xargatha-blue-1")
            hand("xargatha-green-1")
            items("xargatha-red-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(4, -4))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.PlayCards(fromSeat = 0)
    }

    // Onslaught's only target (Tigerclaw) is not asked.
    override val solution = listOf(
        card("hanu-silver"),
        card("brogan-gold"),
        action(ActionType.PRIMARY),
        hero("tigerclaw"),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(4)

    override val wrongTries = listOf(
        WrongTry(
            "There and Back Again (12): Tigerclaw still goes first at 13 and steps away",
            listOf(card("hanu-blue-2a")),
        ),
        WrongTry(
            "Hurry up! with Mighty Punch: no card of Brogan's gets past 13",
            listOf(card("hanu-silver"), card("brogan-blue-2a")),
        ),
        WrongTry(
            "Hurry up! on Brogan's Onslaught: already printed 11, it stays 12",
            listOf(card("hanu-silver"), card("brogan-gold"), action(ActionType.PRIMARY), hero("brogan")),
        ),
    )
}
