package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C56 "Pile On the Fresh Wave". */
object P56PileOnTheFreshWave : Puzzle() {
    override val concept = "C56"

    override val info = PuzzleInfo(
        id = "p56",
        number = 56,
        title = "Pile On the Fresh Wave",
        difficulty = 3,
        theme = "Pushing the lane in the middle of an action",
        heroes = listOf("hanu", "tigerclaw", "wasp", "dodger", "arien", "xargatha"),
        goal = PuzzleGoal.GainCoins(7, Deadline.END_OF_TURN, "Hanu gains at least 7 coins this turn."),
        intro = "Round 5, turn 4, three heroes a side. Hanu has played Pile On (11 with his Initiative item) " +
            "and is the last to act; everyone else has acted. Dodger is level 3 and keeps Vampiric Shield.",
        nudges = listOf(
            "Pile On has two shots and you choose their order. Count what each order earns.",
            "Vampiric Shield counts empty spawn points in the Battle Zone. Which zone is that after a push?",
            "Start with the bullet aimed at a minion next to a friendly hero: the heavy.",
        ),
        explanation = "Pile On may use both bullets, in either order (p.10 Modal Actions). The heavy minion is " +
            "worth 4 and Dodger 3 (her level, p.13): 7 needs both. Shot first, Dodger defends with Vampiric " +
            "Shield, 4 + 2 for the empty spawn points around her in the Battle Zone = 6 against Pile On's " +
            "5 + 1 = 6, and holds. So take the heavy first, with the bullet aimed at a minion next to a friendly " +
            "hero (Tigerclaw). It is Blue's last minion in the Battle Zone, so the lane is pushed at once (p.8): " +
            "the Battle Zone moves to Blue's pre-Throne Zone, and the spawn points around Dodger are no longer " +
            "in it. The other bullet, at a unit next to Hanu, now meets a Shield of 4: Dodger falls, 4 + 3 = 7. " +
            "The rulebook's own trick (p.10 Repeat) is the trap: shoot the heavy with the adjacent bullet and the " +
            "second one finds a fresh Blue minion next to Wasp, but that is only 4 + 2 = 6.",
        rules = listOf(
            "p.8 Push the Lane, Triggering a Push",
            "p.10 Modal Actions, Repeat",
            "p.13 Attacking a Minion, When a Hero is Defeated",
            "p.14 Defense Total",
        ),
    )

    override val playerSeats = setOf("hanu")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        hero("hanu", Team.RED, Hex(4, -4)) {
            level = 5
            played("hanu-red-3b")
            resolved(1, "hanu-gold")
            resolved(2, "hanu-silver")
            resolved(3, "hanu-green-2a")
            hand("hanu-blue-2a")
            items("hanu-red-2b", "hanu-red-3a", "hanu-green-2b", "hanu-blue-2b")
        }
        hero("tigerclaw", Team.RED, Hex(3, -5)) {
            level = 3
            resolved(1, "tigerclaw-silver")
            resolved(2, "tigerclaw-red-2b")
            resolved(3, "tigerclaw-green-2b")
            resolved(4, "tigerclaw-gold")
            hand("tigerclaw-blue-1")
            items("tigerclaw-red-2a", "tigerclaw-green-2a")
        }
        hero("wasp", Team.RED, Hex(6, -5)) {
            level = 3
            resolved(1, "wasp-gold")
            resolved(2, "wasp-red-2a")
            resolved(3, "wasp-blue-2a")
            resolved(4, "wasp-silver")
            hand("wasp-green-1")
            items("wasp-red-2b", "wasp-blue-2b")
        }
        hero("dodger", Team.BLUE, Hex(3, -3)) {
            level = 3
            resolved(1, "dodger-silver")
            resolved(2, "dodger-red-1")
            resolved(3, "dodger-green-2a")
            resolved(4, "dodger-gold")
            hand("dodger-blue-2a")
            items("dodger-blue-2b", "dodger-green-2b")
        }
        hero("arien", Team.BLUE, Hex(-3, -1)) {
            level = 3
            resolved(1, "arien-gold")
            resolved(2, "arien-red-2a")
            resolved(3, "arien-green-1")
            resolved(4, "arien-silver")
            hand("arien-blue-2a")
            items("arien-red-2b", "arien-blue-2b")
        }
        hero("xargatha", Team.BLUE, Hex(-1, -7)) {
            level = 3
            resolved(1, "xargatha-silver")
            resolved(2, "xargatha-red-2a")
            resolved(3, "xargatha-green-2a")
            resolved(4, "xargatha-gold")
            hand("xargatha-blue-1")
            items("xargatha-red-2b", "xargatha-green-2b")
        }
        minion(Team.BLUE, MinionType.HEAVY, Hex(4, -5))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "hanu")
    }

    // The bullets are "Choose one, or both" options (0 the adjacent unit, 1 a minion next to a friend). The
    // heavy is the second bullet's only target and, after the push, Dodger the first's: neither is asked.
    override val solution = listOf(action(ActionType.PRIMARY), option(1), yes())

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "The adjacent bullet first: Dodger holds at 6 against 6, and the heavy that way leaves a fresh minion: 6",
            listOf(action(ActionType.PRIMARY), option(0)),
        ),
        WrongTry(
            "The heavy, then stop: 4 coins",
            listOf(action(ActionType.PRIMARY), option(1), decline()),
        ),
    )
}
