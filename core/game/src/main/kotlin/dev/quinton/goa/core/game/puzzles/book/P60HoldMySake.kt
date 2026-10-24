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
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C60 "Hold My Saké". */
object P60HoldMySake : Puzzle() {
    override val concept = "C60"

    override val info = PuzzleInfo(
        id = "p60",
        number = 60,
        title = "Hold My Saké",
        difficulty = 2,
        theme = "Card states: a discarded card comes back",
        heroes = listOf("takahide", "brogan", "dodger", "wasp"),
        goal = PuzzleGoal.DefeatHero("dodger", Deadline.END_OF_TURN, "Defeat Dodger this turn."),
        intro = "Round 5, turn 3. Takahide has played Hold my Saké (12) and acts next. Brogan has played " +
            "Bolster (6 with his Initiative item), Dodger Necromancy (3). Wasp has already acted.",
        nudges = listOf(
            "Work out Dodger's Defense, spawn points and minions included. Can any attack of Brogan's beat it?",
            "A hero who defends discards the card she defends with. Hold my Saké can also reach into a discard pile.",
            "Hold my Saké on Dodger herself, then swap Mad Dash from Brogan's discard for Bolster.",
        ),
        explanation = "Dodger keeps Vampiric Shield: 4, +2 because two empty spawn points lie within 2 of her " +
            "in the Battle Zone (card text), +2 for her Defense items, +1 for the friendly melee minion beside " +
            "her (p.14): 9. No attack of Brogan's beats that: even with that minion defeated and Brogan landing " +
            "on one of those spawn points, she holds 6 against Mad Dash's 6. So Takahide attacks Dodger " +
            "herself: 3 + 2 = 5 cannot beat her, but to survive she must discard her " +
            "only card (p.14). After the attack, Hold my Saké lets a friendly hero swap his unresolved card with " +
            "one in his hand or his discard (card text); swapped cards swap all their states (p.10), so Mad " +
            "Dash, discarded earlier this round, becomes Brogan's unresolved card and acts at 7 + 1 = 8, before " +
            "Dodger's 3 (p.7). It runs two spaces in a straight line to a space next to her (p.11), and with no " +
            "card she is defeated (p.13). Onslaught from his hand acts sooner but only hits an adjacent unit; " +
            "keeping Bolster does nothing to her. An attack you cannot win can still empty a hand.",
        rules = listOf(
            "p.7 Check the Initiative",
            "p.10 Swap a Card",
            "p.11 Movement in the Card Text",
            "p.13 Attacking a Hero",
            "p.14 Defense Total, Minion Defense Modifiers",
        ),
    )

    override val playerSeats = setOf("takahide", "brogan")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 3
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("takahide", Team.BLUE, Hex(4, -3)) {
            level = 5
            played("takahide-red-3a")
            resolved(1, "takahide-gold")
            resolved(2, "takahide-silver")
            hand("takahide-green-2b", "takahide-blue-2b")
            items("takahide-red-2b", "takahide-red-3b", "takahide-green-2a", "takahide-blue-2a")
        }
        hero("brogan", Team.BLUE, Hex(1, -2)) {
            level = 3
            played("brogan-green-2a")
            resolved(1, "brogan-silver")
            resolved(2, "brogan-blue-2b")
            hand("brogan-gold")
            discard("brogan-red-1")
            items("brogan-green-2b", "brogan-blue-2a")
        }
        hero("dodger", Team.RED, Hex(3, -3)) {
            level = 4
            played("dodger-green-2b")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            discard("dodger-red-2a")
            hand("dodger-blue-2a")
            items("dodger-red-2b", "dodger-green-2a", "dodger-blue-2b")
        }
        hero("wasp", Team.RED, Hex(4, -7)) {
            level = 3
            resolved(1, "wasp-gold")
            resolved(2, "wasp-red-2a")
            resolved(3, "wasp-silver")
            hand("wasp-green-2a", "wasp-blue-1")
            items("wasp-red-2b", "wasp-green-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(4, -4))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "takahide")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("dodger"),
        hero("brogan"),
        card("brogan-red-1"),
        action(ActionType.PRIMARY),
        space(3, -2),
    )

    override val nudgedMoves = solution.take(4)

    override val wrongTries = listOf(
        WrongTry(
            "Defeat the minion beside Dodger: she still holds, 6 at worst against 6",
            listOf(action(ActionType.PRIMARY), unitAt(4, -4)),
        ),
        WrongTry(
            "Dodger discards, then Onslaught: it acts at 12 but only hits an adjacent unit",
            listOf(action(ActionType.PRIMARY), hero("dodger"), hero("brogan"), card("brogan-gold")),
        ),
        WrongTry(
            "Dodger discards, but Brogan keeps Bolster",
            listOf(action(ActionType.PRIMARY), hero("dodger"), hero("brogan"), decline()),
        ),
    )
}
