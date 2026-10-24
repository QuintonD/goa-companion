package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.tokenAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C09 "Phantom at Five Spaces". */
object P09PhantomAtFiveSpaces : Puzzle() {
    override val concept = "C09"

    override val info = PuzzleInfo(
        id = "p09",
        number = 9,
        title = "Phantom at Five Spaces",
        difficulty = 1,
        theme = "Targeting conditions, moving a token",
        heroes = listOf("nebkher", "garrus", "dodger", "wasp"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 4, turn 4. NebKher has played Phantasmal Warrior and acts next. Dodger has no cards " +
            "left in hand; Wasp holds Lift Up (Defense 5). Red has one Life counter left.",
        nudges = listOf(
            "The far Red hero can't defend at all.",
            "Your attack needs an Illusion next to its target: can one get there?",
            "Slide the Illusion at the far end of your range next to Dodger, and shoot her.",
        ),
        explanation = "Phantasmal Warrior's first option moves an Illusion token in range up to 1 space to a " +
            "space next to an enemy hero in range before the attack, then targets that hero (card text, p.17 " +
            "Before the attack). Range 5 reaches Dodger exactly, five spaces away (p.9), and the Illusion four " +
            "spaces off can step next to her. With no card in hand she cannot defend (p.13), and Red's last " +
            "Life counter goes. The Illusion already beside Wasp is the easy shot, but her Lift Up defends 5 " +
            "against Attack 3.",
        rules = listOf(
            "p.9 Range",
            "p.13 Attacking a hero",
            "p.17 Before the attack",
            "p.19 Tokens",
        ),
    )

    override val playerSeats = setOf("nebkher")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 1)
        lifeCounters(Team.BLUE, 2)
        hero("nebkher", Team.BLUE, Hex(3, -2)) {
            level = 2
            played("nebkher-red-2a")
            resolved(1, "nebkher-green-1")
            resolved(2, "nebkher-gold")
            resolved(3, "nebkher-blue-1")
            items("nebkher-red-2b")
            hand("nebkher-silver")
        }
        hero("garrus", Team.BLUE, Hex(-1, -1)) {
            resolved(1, "garrus-gold")
            resolved(2, "garrus-silver")
            resolved(3, "garrus-red-1")
            resolved(4, "garrus-green-1")
            hand("garrus-blue-1")
        }
        hero("dodger", Team.RED, Hex(0, -4)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-1")
            resolved(3, "dodger-silver")
            discard("dodger-blue-1")
            hand()
        }
        hero("wasp", Team.RED, Hex(5, -3)) {
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            hand("wasp-blue-1")
        }
        token("token_illusion", Hex(4, -3), owner = "nebkher")
        token("token_illusion", Hex(2, -5), owner = "nebkher")
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "nebkher")
    }

    override val solution = listOf(action(ActionType.PRIMARY), tokenAt(2, -5), space(1, -5))

    override val wrongTries = listOf(
        WrongTry(
            "Use the Illusion beside Wasp: Lift Up defends 5 against 3",
            listOf(action(ActionType.PRIMARY), tokenAt(4, -3)),
        ),
    )
}
