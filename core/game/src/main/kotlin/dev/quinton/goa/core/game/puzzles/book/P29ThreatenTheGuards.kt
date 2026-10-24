package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C29 "Threaten the Guards" (Xargatha added as Red's idle second hero). */
object P29ThreatenTheGuards : Puzzle() {
    override val concept = "C29"

    override val info = PuzzleInfo(
        id = "p29",
        number = 29,
        title = "Threaten the Guards",
        difficulty = 2,
        theme = "Minion defense modifiers, Repeat",
        heroes = listOf("garrus", "brogan", "wasp", "xargatha"),
        goal = PuzzleGoal.DefeatHero("wasp", Deadline.END_OF_TURN, "Defeat Wasp this turn."),
        intro = "Round 3, turn 4. Garrus has played Threaten (10) and acts next; Brogan has played Mad Dash " +
            "(7). Wasp has played Stop Projectiles (3) and keeps Lift Up (Defense 5). Xargatha has already " +
            "acted.",
        nudges = listOf(
            "Wasp's two melee minions are worth +2 to her defense.",
            "Threaten may repeat, and the repeat may pick a different minion.",
            "Move both melee minions one space away from Garrus, then Mad Dash next to Wasp.",
        ),
        explanation = "Each adjacent friendly melee minion gives a defending hero +1 Defense (p.14), so Wasp " +
            "defends 5 + 2 = 7 against Mad Dash's 6. Threaten moves an enemy unit 1 space farther from Garrus " +
            "(p.17 Farther), and a repeat performs the action again with a new choice of target (p.10 " +
            "Repeat): move one guard, then the other, to spaces that are no longer next to her and still in " +
            "the Battle Zone (p.18). Moving only one leaves 6 against 6, and equal totals defend (p.14). " +
            "Moving Wasp herself strips both guards, but she lands where no two-space Mad Dash line reaches " +
            "her (p.11).",
        rules = listOf(
            "p.10 Repeat",
            "p.11 Movement in the card text",
            "p.14 Minion defense modifiers",
            "p.17 Farther",
            "p.18 Minions outside of the Battle Zone",
        ),
    )

    override val playerSeats = setOf("garrus", "brogan")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        hero("garrus", Team.BLUE, Hex(3, -7)) {
            level = 2
            played("garrus-blue-2a")
            resolved(1, "garrus-gold")
            resolved(2, "garrus-red-1")
            resolved(3, "garrus-silver")
            hand("garrus-green-1")
            items("garrus-blue-2b")
        }
        hero("brogan", Team.BLUE, Hex(0, -4)) {
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-blue-1")
            resolved(3, "brogan-silver")
            hand("brogan-green-1")
        }
        hero("wasp", Team.RED, Hex(2, -5)) {
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-red-1")
            resolved(3, "wasp-silver")
            hand("wasp-blue-1")
        }
        hero("xargatha", Team.RED, Hex(-2, 1)) {
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-red-1")
            resolved(4, "xargatha-green-1")
            hand("xargatha-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(3, -5))
        minion(Team.RED, MinionType.MELEE, Hex(3, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "garrus")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        unitAt(3, -5),
        space(4, -5),
        yes(),
        unitAt(3, -6),
        space(4, -6),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(6)

    override val wrongTries = listOf(
        WrongTry(
            "Threaten Wasp herself: she ends out of every Mad Dash line",
            listOf(action(ActionType.PRIMARY), hero("wasp")),
        ),
        WrongTry(
            "Move one guard only: 5 + 1 = 6 still holds against 6",
            listOf(action(ActionType.PRIMARY), unitAt(3, -5), space(4, -5), decline()),
        ),
    )
}
