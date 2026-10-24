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

/**
 * CONCEPTS.md C22 "Change of Plans" (changed: it starts at Takahide's card choice, with Brogan, Dodger
 * and Wasp seated first and their cards already played; round 3, level 2; Dodger keeps Weakness and a
 * melee minion guards her, so Takahide's attack must take that minion).
 */
object P22ChangeOfPlans : Puzzle() {
    override val concept = "C22"

    override val info = PuzzleInfo(
        id = "p22",
        number = 22,
        title = "Change of Plans",
        difficulty = 2,
        theme = "Initiative, card swaps and minion defense modifiers",
        heroes = listOf("takahide", "brogan", "dodger", "wasp"),
        goal = PuzzleGoal.DefeatHero("dodger", Deadline.END_OF_TURN, "Defeat Dodger this turn."),
        intro = "Round 3, turn 3: Takahide chooses his card. Brogan has played Shield (6) and holds Onslaught " +
            "and Mad Dash; he has an Attack item. Dodger has played Littlefinger of Death (10 with her item) " +
            "and keeps Weakness (Defense 4) in hand. Wasp has played Stop Projectiles (3).",
        nudges = listOf(
            "Dodger acts at 10 and will get away. Can anyone on Blue act before her, and hit her hard enough?",
            "Brogan's played card can still change. Count what Dodger defends with, minions included, and " +
                "which of them Takahide can take away.",
            "Play Set an Example, defeat the Red minion between you and Dodger, then swap Brogan's Shield for " +
                "Onslaught.",
        ),
        explanation = "After each card resolves, the next hero to act is the one with the highest initiative " +
            "on an unresolved card, checked again every time (p.7). Set an Example's After the attack text " +
            "lets Brogan swap his played card for one in his hand; a swap exchanges all the cards' states " +
            "(p.10), so Onslaught becomes his unresolved card at 11 and he acts before Dodger's 10. Mad Dash " +
            "hits harder but at 7 it comes after her, and she runs. Onslaught attacks with 4 + 1 for the " +
            "Attack item (p.16): against Weakness's 4 and +1 for her adjacent melee minion (p.14) that is 5 " +
            "against 5, and equal totals defend. So Takahide's own attack must defeat that minion, the one " +
            "between him and Dodger, not the one on his other side: then 5 beats 4. Bushido and Proven " +
            "Warrior swap or discard nothing that helps. The swap fixes the timing; the minion fixes the sum.",
        rules = listOf(
            "p.7 Resolve your card",
            "p.10 Swap a card",
            "p.13 Attacking a hero",
            "p.14 Minion defense modifiers",
            "p.16 Items",
            "p.17 After the attack",
        ),
    )

    override val playerSeats = setOf("takahide", "brogan")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 3
        tieBreaker = Team.RED
        clearMinions()
        // Seated first with their cards already played: the player's choice comes last.
        hero("brogan", Team.BLUE, Hex(0, -4)) {
            level = 2
            played("brogan-green-1")
            resolved(1, "brogan-silver")
            resolved(2, "brogan-blue-2a")
            hand("brogan-gold", "brogan-red-1")
            items("brogan-blue-2b")
        }
        hero("dodger", Team.RED, Hex(0, -5)) {
            level = 2
            played("dodger-red-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            discard("dodger-green-1")
            hand("dodger-blue-2b")
            items("dodger-blue-2a")
        }
        hero("wasp", Team.RED, Hex(4, -2)) {
            level = 2
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            hand("wasp-red-1", "wasp-blue-2a")
            items("wasp-blue-2b")
        }
        hero("takahide", Team.BLUE, Hex(2, -5)) {
            level = 2
            resolved(1, "takahide-gold")
            resolved(2, "takahide-green-2a")
            hand("takahide-silver", "takahide-red-1", "takahide-blue-1")
            items("takahide-green-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(1, -5))
        minion(Team.RED, MinionType.MELEE, Hex(3, -5))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.PlayCards(fromSeat = 3)
    }

    override val solution = listOf(
        card("takahide-red-1"),
        action(ActionType.PRIMARY),
        unitAt(1, -5),
        hero("brogan"),
        card("brogan-gold"),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(5)

    override val wrongTries = listOf(
        WrongTry("Bushido: no attack, no swap for Brogan", listOf(card("takahide-silver"))),
        WrongTry("Proven Warrior: no friendly discard to copy", listOf(card("takahide-blue-1"))),
        WrongTry(
            "Defeat the other minion: Dodger keeps her guard and defends 5 against 5",
            listOf(card("takahide-red-1"), action(ActionType.PRIMARY), unitAt(3, -5)),
        ),
        WrongTry(
            "Swap in Mad Dash: 7 still comes after Dodger's 10",
            listOf(
                card("takahide-red-1"),
                action(ActionType.PRIMARY),
                unitAt(1, -5),
                hero("brogan"),
                card("brogan-red-1"),
            ),
        ),
        WrongTry(
            "Keep Shield: Dodger acts first and runs",
            listOf(card("takahide-red-1"), action(ActionType.PRIMARY), unitAt(1, -5), decline()),
        ),
    )
}
