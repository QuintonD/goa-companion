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
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C22 "Change of Plans" (changed: it starts at Takahide's card choice, with Brogan and
 * Dodger seated first and their cards already played; the idle Wasp has no cards and passes).
 */
object P22ChangeOfPlans : Puzzle() {
    override val concept = "C22"

    override val info = PuzzleInfo(
        id = "p22",
        number = 22,
        title = "Change of Plans",
        difficulty = 2,
        theme = "Initiative and card swaps",
        heroes = listOf("takahide", "brogan", "dodger", "wasp"),
        goal = PuzzleGoal.DefeatHero("dodger", Deadline.END_OF_TURN, "Defeat Dodger this turn."),
        intro = "Round 2, turn 3: Takahide chooses his card. Brogan has already played Shield (6) and holds " +
            "Onslaught and Mad Dash. Dodger has played Littlefinger of Death (9) and has no cards left in " +
            "hand. Wasp has no cards and passes.",
        nudges = listOf(
            "Dodger acts at 9 and will get away. Can anyone on Blue act before her, and still hit her?",
            "Brogan is next to Dodger, but Shield is no attack and comes too late. Which of Takahide's cards " +
                "can change the card a friend has played?",
            "Play Set an Example, hit the Red minion, then swap Brogan's Shield for Onslaught.",
        ),
        explanation = "After each card resolves, the next hero to act is the one with the highest initiative " +
            "on an unresolved card, checked again every time (p.7). Set an Example's After the attack text " +
            "lets Brogan swap his played card for one in his hand; a swap exchanges all the cards' states " +
            "(p.10), so Onslaught becomes his unresolved card at 11 and he acts before Dodger's 9: she is " +
            "adjacent and has no card to defend with (p.13). Proven Warrior and Bushido swap or discard " +
            "nothing that helps, and Mad Dash (7) would still act after Dodger, who runs.",
        rules = listOf("p.7 Resolve your card", "p.10 Swap a card", "p.13 Attacking a hero", "p.17 After the attack"),
    )

    override val playerSeats = setOf("takahide", "brogan")

    override fun PositionBuilder.setUp() {
        round = 2
        turn = 3
        tieBreaker = Team.RED
        clearMinions()
        // Seated first with their cards already played: the player's choice comes last.
        hero("brogan", Team.BLUE, Hex(0, -4)) {
            played("brogan-green-1")
            resolved(1, "brogan-silver")
            resolved(2, "brogan-blue-1")
            hand("brogan-gold", "brogan-red-1")
        }
        hero("dodger", Team.RED, Hex(-1, -4)) {
            played("dodger-red-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-green-1")
            discard("dodger-silver", "dodger-blue-1")
            hand()
        }
        hero("wasp", Team.RED, Hex(4, -2)) {
            resolved(1, "wasp-gold")
            resolved(2, "wasp-red-1")
            discard("wasp-silver", "wasp-green-1", "wasp-blue-1")
            hand()
        }
        hero("takahide", Team.BLUE, Hex(2, -5)) {
            resolved(1, "takahide-gold")
            resolved(2, "takahide-green-1")
            hand("takahide-silver", "takahide-red-1", "takahide-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(3, -5))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.PlayCards(fromSeat = 2)
    }

    override val solution = listOf(
        card("takahide-red-1"),
        action(ActionType.PRIMARY),
        hero("brogan"),
        card("brogan-gold"),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(4)

    override val wrongTries = listOf(
        WrongTry("Bushido: no attack, no swap for Brogan", listOf(card("takahide-silver"))),
        WrongTry("Proven Warrior: Dodger has nothing left to discard", listOf(card("takahide-blue-1"))),
        WrongTry(
            "Swap in Mad Dash: 7 still comes after Dodger's 9",
            listOf(card("takahide-red-1"), action(ActionType.PRIMARY), hero("brogan"), card("brogan-red-1")),
        ),
        WrongTry(
            "Keep Shield: Dodger acts first and runs",
            listOf(card("takahide-red-1"), action(ActionType.PRIMARY), decline()),
        ),
    )
}
