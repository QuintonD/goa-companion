package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.tokenAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C20 "Clear the Smoke". */
object P20ClearTheSmoke : Puzzle() {
    override val concept = "C20"

    override val info = PuzzleInfo(
        id = "p20",
        number = 20,
        title = "Clear the Smoke",
        difficulty = 1,
        theme = "The Clear action",
        heroes = listOf("brogan", "swift", "wuk", "min"),
        goal = PuzzleGoal.DefeatHero("wuk", Deadline.END_OF_TURN, "Defeat Wuk this turn."),
        intro = "Round 3, turn 4. Brogan has played Onslaught and acts next, then Swift with Snipe. Wuk has " +
            "played Toss Away (initiative 6) and has no cards left in hand. Min has already acted: her " +
            "Smoke bomb stands between Swift and Wuk.",
        nudges = listOf(
            "Swift's shot is blocked: by what?",
            "Every card with an Attack action can do something besides attacking.",
            "Clear the Smoke bomb with Brogan.",
        ),
        explanation = "You may perform one Clear secondary action instead of your entire Attack action: " +
            "remove any number of tokens adjacent to you (p.13 Clear; p.19 Tokens). With the Smoke bomb " +
            "gone nothing stops Swift targeting along the line, and Wuk stands exactly at Snipe's maximum " +
            "range of 4 in a straight line with no card to defend with (p.9, p.13). Brogan's 11 acts before " +
            "Swift's 8, and both before Wuk's 6 (p.7). Onslaught on the Blue minion earns two coins, but " +
            "the smoke stays and Swift can no longer target Wuk.",
        rules = listOf("p.7 Check the initiative", "p.9 Range", "p.13 Clear action", "p.19 Tokens"),
    )

    override val playerSeats = setOf("brogan", "swift")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        hero("brogan", Team.RED, Hex(3, -6)) {
            played("brogan-gold")
            resolved(1, "brogan-red-1")
            resolved(2, "brogan-green-1")
            resolved(3, "brogan-blue-1")
            hand("brogan-silver")
        }
        hero("swift", Team.RED, Hex(5, -5)) {
            played("swift-red-1")
            resolved(1, "swift-gold")
            resolved(2, "swift-silver")
            resolved(3, "swift-green-1")
            hand("swift-blue-1")
        }
        hero("wuk", Team.BLUE, Hex(1, -5)) {
            played("wuk-green-1")
            resolved(1, "wuk-gold")
            resolved(2, "wuk-silver")
            resolved(3, "wuk-red-1")
            discard("wuk-blue-1")
            hand()
        }
        hero("min", Team.BLUE, Hex(0, -2)) {
            resolved(1, "min-gold")
            resolved(2, "min-red-1")
            resolved(3, "min-blue-1")
            resolved(4, "min-silver")
            hand("min-green-1")
        }
        token("token_smoke_bomb", Hex(3, -5), owner = "min", team = Team.BLUE)
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -6))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "brogan")
    }

    override val solution = listOf(action(ActionType.CLEAR), tokenAt(3, -5), action(ActionType.PRIMARY))

    override val wrongTries = listOf(
        WrongTry(
            "Onslaught on the Blue minion: two coins, but the smoke stays",
            listOf(action(ActionType.PRIMARY)),
        ),
    )
}
