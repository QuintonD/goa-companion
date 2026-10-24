package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C15 "Quickdraw" (changed: Arien's attack card is played and not yet resolved, tied with
 * Sabina at 8 and the coin showing Red, because a position can't record an attack card already resolved).
 */
object P15Quickdraw : Puzzle() {
    override val concept = "C15"

    override val info = PuzzleInfo(
        id = "p15",
        number = 15,
        title = "Quickdraw",
        difficulty = 1,
        theme = "Card conditions, defense totals",
        heroes = listOf("sabina", "tigerclaw", "wasp", "arien"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 4, turn 4. Sabina has played Quickdraw (8) and acts next: Arien's Dangerous Current " +
            "also has initiative 8, and the Tie Breaker coin shows Red. Wasp has already played Lift Up. Arien " +
            "holds Spell Break (Defense 3), Wasp holds Magnetic Dagger (Defense 2). Blue has one Life counter left.",
        nudges = listOf(
            "Your shot is much stronger against some targets.",
            "Who has played an attack card this turn, even one not used yet?",
            "Shoot Arien.",
        ),
        explanation = "Quickdraw's +3 checks what the target played this turn, whatever action they took or " +
            "will take, even if they have not resolved it yet (Sabina FAQ). Arien played Dangerous Current, " +
            "an attack card: 2 + 3 = 5 against his Spell Break's 3, plus 1 for the friendly melee minion next " +
            "to him (p.14), 4. He is defeated and Blue's last Life counter goes. Wasp looks weaker with Defense " +
            "2, but she played a skill card: no bonus, 2 against 2, and a tie defends (p.14).",
        rules = listOf(
            "p.7 Check the initiative, ties",
            "p.14 Defense total, Minion defense modifiers",
            "Sabina FAQ: Quickdraw",
        ),
    )

    override val playerSeats = setOf("sabina")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 1)
        hero("sabina", Team.RED, Hex(1, -5)) {
            played("sabina-red-1")
            resolved(1, "sabina-gold")
            resolved(2, "sabina-silver")
            resolved(3, "sabina-blue-1")
            hand("sabina-green-1")
        }
        hero("tigerclaw", Team.RED, Hex(4, -2)) {
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-red-1")
            resolved(4, "tigerclaw-green-1")
            hand("tigerclaw-blue-1")
        }
        hero("wasp", Team.BLUE, Hex(0, -4)) {
            resolved(1, "wasp-red-1")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-green-1")
            resolved(4, "wasp-blue-1")
            hand("wasp-gold")
        }
        hero("arien", Team.BLUE, Hex(2, -5)) {
            played("arien-red-1")
            resolved(1, "arien-gold")
            resolved(2, "arien-green-1")
            resolved(3, "arien-blue-1")
            hand("arien-silver")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -6))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "sabina")
    }

    override val solution = listOf(action(ActionType.PRIMARY), hero("arien"))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Shoot Wasp for her weaker card: no bonus, 2 against 2, and she defends",
            listOf(action(ActionType.PRIMARY), hero("wasp")),
        ),
    )
}
