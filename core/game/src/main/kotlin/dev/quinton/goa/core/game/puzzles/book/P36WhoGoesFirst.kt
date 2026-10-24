package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
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

/** CONCEPTS.md C36 "Who Goes First?". */
object P36WhoGoesFirst : Puzzle() {
    override val concept = "C36"

    override val info = PuzzleInfo(
        id = "p36",
        number = 36,
        title = "Who Goes First?",
        difficulty = 2,
        theme = "Tied initiative and the Tie Breaker coin",
        heroes = listOf("dodger", "tigerclaw", "wasp", "arien"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn."),
        intro = "Round 3, turn 4. Three cards are tied at initiative 9: Tigerclaw's Combat Reflexes, Dodger's " +
            "Finger of Death and Wasp's Electrocute. Red shows on the Tie Breaker coin. Tigerclaw has no cards " +
            "left; Dodger keeps Shield of Decay, Wasp keeps Lift Up. Arien has acted.",
        nudges = listOf(
            "Wasp stands next to Tigerclaw, and he has no card to defend with.",
            "Red shows, so a Red hero goes first. But the coin turns over after that hero, not after both.",
            "Send Tigerclaw first and move him away from Wasp.",
        ),
        explanation = "In a tie across teams, the team shown on the Tie Breaker coin acts first and picks which " +
            "of its tied heroes goes; once that hero has resolved, they flip the coin (p.7). So only one Red " +
            "hero acts before Wasp. If Dodger goes first, Blue shows for the next tie, Wasp acts before " +
            "Tigerclaw, and Electrocute defeats him: he has no card to defend with (p.14). Dodger can't stop " +
            "it: Wasp is not next to her and has no card in her discard, so Finger of Death can't target her. " +
            "Tigerclaw first just steps out of reach (Electrocute needs an adjacent target), and Dodger acts last. " +
            "The lesson: the coin favours your team for one hero only, so spend it on the hero who must move.",
        rules = listOf("p.7 Check the Initiative, Tied Initiative", "p.14 Defense"),
    )

    override val playerSeats = setOf("dodger", "tigerclaw")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("dodger", Team.RED, Hex(0, -2)) {
            level = 2
            played("dodger-red-2a")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-green-1")
            hand("dodger-blue-1")
            items("dodger-red-2b")
        }
        hero("tigerclaw", Team.RED, Hex(1, -5)) {
            level = 2
            played("tigerclaw-red-2a")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-green-1")
            discard("tigerclaw-blue-1")
            hand()
            items("tigerclaw-red-2b")
        }
        hero("wasp", Team.BLUE, Hex(2, -5)) {
            level = 2
            played("wasp-red-2a")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-green-1")
            hand("wasp-blue-1")
            items("wasp-red-2b")
        }
        hero("arien", Team.BLUE, Hex(4, -2)) {
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-red-1")
            resolved(4, "arien-green-1")
            hand("arien-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving()
    }

    override val solution = listOf(
        hero("tigerclaw"),
        action(ActionType.MOVEMENT),
        space(0, -4),
        action(ActionType.HOLD),
    )

    override val nudgedMoves = solution.take(1)

    override val wrongTries = listOf(
        WrongTry("Dodger first: the coin flips to Blue and Wasp acts before Tigerclaw", listOf(hero("dodger"))),
        WrongTry(
            "Tigerclaw first, but he holds: Wasp is still next to him",
            listOf(hero("tigerclaw"), action(ActionType.HOLD)),
        ),
    )
}
