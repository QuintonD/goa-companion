package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C59 "Mind Grip". */
object P59MindGrip : Puzzle() {
    override val concept = "C59"

    override val info = PuzzleInfo(
        id = "p59",
        number = 59,
        title = "Mind Grip",
        difficulty = 3,
        theme = "Using an enemy's previous card",
        heroes = listOf("nebkher", "wasp", "dodger", "brogan", "sabina"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 4, turn 3. NebKher has played Mind Grip and acts next. Turn-2 cards: Brogan's Mad " +
            "Dash, Wasp's Lift Up, Dodger's Death Trap. Dodger has no cards left in hand. Red has one Life " +
            "counter left; Brogan, Wasp and Dodger act after NebKher.",
        nudges = listOf(
            "NebKher can borrow what an enemy did last turn.",
            "Whose previous card would carry you to Dodger and hit her?",
            "Mind Grip Brogan's Mad Dash.",
        ),
        explanation = "Mind Grip performs an action of the card in an enemy hero's previous Turn slot as " +
            "NebKher's own (NebKher FAQ). Wasp's Lift Up only moves things, and Dodger's Death Trap only " +
            "makes a hero discard, if able: neither defeats Dodger. Brogan's turn-2 card is Mad Dash, and he " +
            "is 5 spaces away, just in range: NebKher charges exactly 2 spaces in a straight line to a space " +
            "next to Dodger (p.11 Movement in the Card Text) and attacks with Mad Dash's Attack 6, a " +
            "non-ranged attack (NebKher FAQ). Dodger has no card to defend with (p.13), and Red spends its " +
            "last Life counter.",
        rules = listOf(
            "p.11 Movement in the Card Text",
            "p.13 Attacking a Hero, When a Hero is Defeated",
            "NebKher FAQ: Mind Grip",
        ),
    )

    override val playerSeats = setOf("nebkher")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 3
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 1)
        lifeCounters(Team.BLUE, 2)
        hero("nebkher", Team.BLUE, Hex(1, -2)) {
            played("nebkher-gold")
            resolved(1, "nebkher-red-1")
            resolved(2, "nebkher-green-1")
            hand("nebkher-silver", "nebkher-blue-1")
        }
        hero("wasp", Team.RED, Hex(4, -6)) {
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-blue-1")
            hand("wasp-silver", "wasp-red-1")
        }
        hero("dodger", Team.RED, Hex(3, -3)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            discard("dodger-red-1", "dodger-blue-1")
            hand()
        }
        hero("brogan", Team.RED, Hex(-1, -5)) {
            played("brogan-green-1")
            resolved(1, "brogan-silver")
            resolved(2, "brogan-red-1")
            hand("brogan-gold", "brogan-blue-1")
        }
        hero("sabina", Team.BLUE, Hex(-1, 0)) {
            resolved(1, "sabina-gold")
            resolved(2, "sabina-red-1")
            resolved(3, "sabina-green-1")
            hand("sabina-silver", "sabina-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(4, -4))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "nebkher")
    }

    // Ending on (3,-2), Dodger is the only enemy unit next to NebKher (not asked); (3,-4) also wins.
    override val solution =
        listOf(action(ActionType.PRIMARY), hero("brogan"), action(ActionType.PRIMARY), space(3, -2))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Copy Wasp's Lift Up: it moves things, no attack",
            listOf(action(ActionType.PRIMARY), hero("wasp")),
        ),
        WrongTry(
            "Copy Dodger's Death Trap: she has nothing to discard",
            listOf(action(ActionType.PRIMARY), hero("dodger")),
        ),
        WrongTry(
            "Mad Dash west, onto the Red ranged minion",
            listOf(action(ActionType.PRIMARY), hero("brogan"), action(ActionType.PRIMARY), space(-1, -2)),
        ),
    )
}
