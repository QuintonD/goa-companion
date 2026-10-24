package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C25 "Cold Snap". */
object P25ColdSnap : Puzzle() {
    override val concept = "C25"

    override val info = PuzzleInfo(
        id = "p25",
        number = 25,
        title = "Cold Snap",
        difficulty = 2,
        theme = "Minion defense modifiers",
        heroes = listOf("tali", "brogan", "wasp", "dodger"),
        goal = PuzzleGoal.DefeatHero("wasp", Deadline.END_OF_TURN, "Defeat Wasp this turn."),
        intro = "Round 3, turn 4. Tali has played Cold Snap and acts next, then Brogan with Mad Dash. Wasp " +
            "has played Stop Projectiles (initiative 3) and keeps Lift Up (Defense 5) in hand. Dodger has " +
            "already acted.",
        nudges = listOf(
            "Wasp is guarded: by what?",
            "Cold Snap moves them all the same way, unless one of them can't move.",
            "Cold Snap south-east, then Mad Dash east into the space the minion left.",
        ),
        explanation = "An adjacent friendly melee minion gives a hero +1 Defense, and equal totals defend " +
            "(p.14): next to her minion Wasp defends Mad Dash's 6 with 6. Cold Snap moves each enemy unit " +
            "in radius one space \"if able\": a unit that would enter terrain simply stays (p.17 If able, " +
            "p.19 Terrain). South-east, Wasp is held by the water while the minion steps away, which " +
            "splits her from her guard and empties the minion's old space, two spaces east of Brogan, for " +
            "his Mad Dash (p.11 Movement in the card text): 6 against 5. West or north-west both move " +
            "together and she keeps her guard; east or north-east she ends out of Brogan's reach; south-west " +
            "nobody can move and the minion still blocks the charge. When one effect moves a group the same " +
            "way, look for the member the board holds back.",
        rules = listOf(
            "p.11 Movement in the card text",
            "p.14 Minion defense modifiers",
            "p.17 If able",
            "p.19 Terrain",
        ),
    )

    override val playerSeats = setOf("tali", "brogan")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        hero("tali", Team.BLUE, Hex(3, -5)) {
            played("tali-blue-1")
            resolved(1, "tali-gold")
            resolved(2, "tali-red-1")
            resolved(3, "tali-green-1")
            hand("tali-silver")
        }
        hero("brogan", Team.BLUE, Hex(2, -6)) {
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-green-1")
            resolved(3, "brogan-blue-1")
            hand("brogan-silver")
        }
        hero("wasp", Team.RED, Hex(5, -7)) {
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            hand("wasp-blue-1")
        }
        hero("dodger", Team.RED, Hex(0, -2)) {
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-1")
            resolved(4, "dodger-green-1")
            hand("dodger-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(4, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "tali")
    }

    override val solution = listOf(action(ActionType.PRIMARY), option(5), action(ActionType.PRIMARY), hero("wasp"))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry("Cold Snap east: Wasp slides out of Brogan's reach", listOf(action(ActionType.PRIMARY), option(0))),
        WrongTry(
            "Cold Snap north-east: Wasp ends beyond Brogan's charges",
            listOf(action(ActionType.PRIMARY), option(1)),
        ),
        WrongTry(
            "Cold Snap north-west: Wasp and her minion move together",
            listOf(action(ActionType.PRIMARY), option(2)),
        ),
        WrongTry(
            "Cold Snap west: Wasp and her minion move together",
            listOf(action(ActionType.PRIMARY), option(3)),
        ),
        WrongTry("No Cold Snap: the minion blocks the charge", listOf(action(ActionType.MOVEMENT))),
    )
}
