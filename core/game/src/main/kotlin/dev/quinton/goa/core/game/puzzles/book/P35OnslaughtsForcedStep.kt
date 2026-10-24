package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C35 "Onslaught's Forced Step" (changed: Brogan holds one card; the defense totals decide). */
object P35OnslaughtsForcedStep : Puzzle() {
    override val concept = "C35"

    override val info = PuzzleInfo(
        id = "p35",
        number = 35,
        title = "Onslaught's Forced Step",
        difficulty = 1,
        theme = "A forced follow-up step, minion defense modifiers",
        heroes = listOf("brogan", "wasp", "xargatha", "swift"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn."),
        intro = "Round 4, turn 4. Brogan has played Onslaught and acts next; he keeps Mighty Punch (Defense 6) and " +
            "has a Defense item. Swift has played Snipe (8) and Xargatha Threatening Slash (7); each has two Attack " +
            "items, and both will attack Brogan if they can. Wasp has already acted.",
        nudges = listOf(
            "Onslaught's step into the defeated minion's space is not optional, so pick the target for the space " +
                "it leaves you in.",
            "You can defend once. Wherever you end up, count who can reach you, and your Defense there with " +
                "every minion beside you.",
            "Onslaught on the Blue minion west of Brogan, beside Xargatha.",
        ),
        explanation = "Onslaught's step ends with \"if able\", but when the space is free it must be taken (p.17 " +
            "If able; Brogan FAQ: \"Brogan must move into the space where the target was, if he is able to\"), so " +
            "the target decides where Brogan stands, and with one card he can survive one attack at most. Where " +
            "he stands, Xargatha is adjacent and his Defense is 6 + 1 (item) + 1 for the friendly melee minion - 1 " +
            "for each of the two Blue melee minions (p.14) = 6, against Threatening Slash's 5 + 2 = 7. The minion " +
            "to the north-west steps him away from Xargatha but onto Snipe's line, exactly four spaces from Swift " +
            "(p.19): 6 + 1 - 1 = 6 against Snipe's 5 + 2 = 7. The minion to the west puts him right next to " +
            "Xargatha, yet only she can reach him there, and his Defense is 6 + 1 + 1 for the Red melee minion " +
            "beside that space - 1 for the Blue one = 7 against 7: equal totals defend (p.14). Onslaught on " +
            "Xargatha fails: she defends, so Brogan stays where he is. The lesson: a forced move is still a move " +
            "you aim, and the safe space is the one where the numbers hold, even next to an enemy.",
        rules = listOf(
            "p.10 Mandatory steps",
            "p.13 Non-ranged attacks",
            "p.14 Defense, Minion Defense Modifiers",
            "p.17 If able",
            "p.19 Straight line",
        ),
    )

    override val playerSeats = setOf("brogan")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("brogan", Team.RED, Hex(2, -5)) {
            level = 4
            played("brogan-gold")
            resolved(1, "brogan-red-2a")
            resolved(2, "brogan-green-2b")
            resolved(3, "brogan-silver")
            hand("brogan-blue-2a")
            items("brogan-red-2b", "brogan-green-2a", "brogan-blue-2b")
        }
        hero("wasp", Team.RED, Hex(3, -6)) {
            level = 2
            resolved(1, "wasp-silver")
            resolved(2, "wasp-red-1")
            resolved(3, "wasp-green-1")
            resolved(4, "wasp-gold")
            hand("wasp-blue-2a")
            items("wasp-blue-2b")
        }
        hero("xargatha", Team.BLUE, Hex(1, -4)) {
            level = 3
            played("xargatha-red-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-green-2b")
            hand("xargatha-blue-2a")
            items("xargatha-green-2a", "xargatha-blue-2b")
        }
        hero("swift", Team.BLUE, Hex(2, -2)) {
            level = 3
            played("swift-red-1")
            resolved(1, "swift-gold")
            resolved(2, "swift-silver")
            resolved(3, "swift-green-2b")
            hand("swift-blue-2b")
            items("swift-green-2a", "swift-blue-2a")
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -5))
        minion(Team.RED, MinionType.MELEE, Hex(3, -5))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -5))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -6))
        minion(Team.BLUE, MinionType.HEAVY, Hex(5, -8))
        phase = PositionPhase.Resolving(next = "brogan")
    }

    override val solution = listOf(action(ActionType.PRIMARY), unitAt(1, -5), card("brogan-blue-2a"))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Onslaught on the minion to the north-west: away from Xargatha, but on Snipe's line, 6 against 7",
            listOf(action(ActionType.PRIMARY), unitAt(2, -6)),
        ),
        WrongTry(
            "Onslaught on Xargatha: she defends, and Brogan stays where he is",
            listOf(action(ActionType.PRIMARY), hero("xargatha")),
        ),
        WrongTry("Hold: Xargatha is adjacent, and Brogan defends at 6 against 7", listOf(action(ActionType.HOLD))),
        WrongTry(
            "Land beside Xargatha, then keep the card: she is not stopped",
            listOf(action(ActionType.PRIMARY), unitAt(1, -5), decline()),
        ),
    )
}
