package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C15 "Quickdraw" (changed: Arien's attack card is played and not yet resolved, tied with
 * Sabina at 8 and the coin showing Red, because a position can't record an attack card already resolved;
 * hardened: a Blue minion guards Arien, so Tigerclaw must remove it first; see "Encoded as").
 */
object P15Quickdraw : Puzzle() {
    override val concept = "C15"

    override val info = PuzzleInfo(
        id = "p15",
        number = 15,
        title = "Quickdraw",
        difficulty = 1,
        theme = "Card conditions, defense totals",
        heroes = listOf("tigerclaw", "sabina", "wasp", "arien"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 3, turn 4. Tigerclaw has played Combat Reflexes (9) and acts next, then Sabina with " +
            "Quickdraw (8): Arien's Dangerous Current also has initiative 8, and the Tie Breaker coin shows " +
            "Red. Wasp has played Stop Projectiles. Arien holds Spell Break, Wasp Magnetic Dagger; each has a " +
            "Defense item. Blue has one Life counter left.",
        nudges = listOf(
            "Quickdraw is much stronger against some targets: who has played an attack card this turn, even " +
                "one not used yet?",
            "Count Arien's defense against it, minions included, and see what Tigerclaw can change first.",
            "Tigerclaw steps next to the Blue minion beside Arien and defeats it; then Sabina shoots Arien.",
        ),
        explanation = "Quickdraw's +3 checks what the target played this turn, whatever action they took or " +
            "will take, even if they have not resolved it yet (Sabina FAQ): Arien played Dangerous Current, " +
            "an attack card, so the shot is 2 + 3 = 5. Against it he has Spell Break 3, +1 item, +1 for the " +
            "Blue minion beside him (p.14): 5, and a tie defends. Tigerclaw acts first, at 9 (p.7): Combat " +
            "Reflexes moves him 1 space before the attack (card text), next to that minion, and defeats it. " +
            "Now 5 against 4, and Arien falls with Blue's last Life counter (p.13). Arien himself is out of " +
            "Tigerclaw's reach. Wasp looks the weaker target with Magnetic Dagger's 2, but she played no " +
            "attack card: 2 against 2 + 1 item + 1 for her own minion, and she defends.",
        rules = listOf(
            "p.7 Check the initiative, ties",
            "p.13 When a hero is defeated",
            "p.14 Defense total, Minion defense modifiers",
            "Sabina FAQ: Quickdraw",
        ),
    )

    override val playerSeats = setOf("tigerclaw", "sabina")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 1)
        hero("tigerclaw", Team.RED, Hex(4, -4)) {
            level = 2
            played("tigerclaw-red-2a")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-green-1")
            items("tigerclaw-red-2b")
            hand("tigerclaw-blue-1")
        }
        hero("sabina", Team.RED, Hex(0, -5)) {
            level = 2
            played("sabina-red-1")
            resolved(1, "sabina-gold")
            resolved(2, "sabina-silver")
            resolved(3, "sabina-blue-2a")
            items("sabina-blue-2b")
            hand("sabina-green-1")
        }
        hero("wasp", Team.BLUE, Hex(0, -3)) {
            level = 2
            played("wasp-green-1")
            resolved(1, "wasp-red-2a")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-blue-1")
            items("wasp-red-2b")
            hand("wasp-gold")
        }
        hero("arien", Team.BLUE, Hex(2, -5)) {
            level = 2
            played("arien-red-1")
            resolved(1, "arien-gold")
            resolved(2, "arien-green-2a")
            resolved(3, "arien-blue-1")
            items("arien-green-2b")
            hand("arien-silver")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -5))
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(-1, -3))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "tigerclaw")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        space(4, -5),
        action(ActionType.PRIMARY),
        hero("arien"),
    )

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Shoot Wasp, the weaker card: no bonus, 2 against 4",
            listOf(action(ActionType.PRIMARY), space(4, -5), action(ActionType.PRIMARY), hero("wasp")),
        ),
        WrongTry(
            "Attack without moving first: only the minion beside Tigerclaw is in reach; Arien keeps his guard",
            listOf(action(ActionType.PRIMARY), decline()),
        ),
    )
}
