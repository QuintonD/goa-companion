package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.heroes.IgnatiaHero
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C54 "Chaos Incarnate". */
object P54ChaosIncarnate : Puzzle() {
    override val concept = "C54"

    override val info = PuzzleInfo(
        id = "p54",
        number = 54,
        title = "Chaos Incarnate",
        difficulty = 3,
        theme = "The Tie Breaker coin as card text",
        heroes = listOf("ignatia", "brogan", "tigerclaw", "xargatha"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 6, turn 4. Ignatia (level 8, Chaos Incarnate unlocked) has played Violent Conflagration " +
            "(12 with her Initiative items) and acts next. Brogan has played Furious Charge, Xargatha Deadly " +
            "Swipe: both 8. Tigerclaw has already acted. The Magma token is Ignatia's. Blue has one Life counter left.",
        nudges = listOf(
            "Brogan and Xargatha are tied. Who acts first, and does that have to stay so?",
            "Chaos Incarnate's flip turns the coin for the rest of the turn, not just for Ignatia's card.",
            "Defeat the minion next to Xargatha, and leave the coin as it is.",
        ),
        explanation = "Violent Conflagration applies the text of the face the Tie Breaker coin shows (card " +
            "reminder). It shows Red, the orange gear: defeat an enemy minion in radius adjacent to an enemy hero. " +
            "The minion beside Xargatha is the one to take: it gives her +1 Defense (p.14) and it blocks Brogan's " +
            "only charge at her. Then decline the flip. Brogan and Xargatha are tied at 8; between teams the " +
            "coin decides (p.7), and Red's side up means Brogan acts first: Furious Charge runs three spaces " +
            "north-west to the minion's old space (p.11) and attacks with 7 + 1 = 8, while Petrifying Stare " +
            "defends only 6 + 1 = 7. Xargatha falls and Blue spends its last Life counter (p.13). The tempting " +
            "line flips the coin for a second blast: with Blue's side up the text makes Xargatha, next to the " +
            "Magma token, discard her last card. But the flipped coin now gives the tie to Blue: Xargatha acts " +
            "before Brogan and walks out of reach. The flip that powers Ignatia's Ultimate is the same coin that " +
            "orders a tie: a second action can cost you the turn.",
        rules = listOf(
            "p.5 Tie Breaker coin",
            "p.7 Tied Initiative",
            "p.11 Movement in the Card Text",
            "p.13 Attacking a Hero, When a Hero is Defeated",
            "p.14 Minion Defense Modifiers",
            "p.16 The Ultimate",
        ),
    )

    override val playerSeats = setOf("ignatia", "brogan")

    override fun PositionBuilder.setUp() {
        round = 6
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        wavesLeft("lane-1", 1)
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 1)
        hero("ignatia", Team.RED, Hex(1, -6)) {
            ultimate()
            played("ignatia-blue-3a")
            resolved(1, "ignatia-gold")
            resolved(2, "ignatia-green-3a")
            resolved(3, "ignatia-red-3a")
            hand("ignatia-silver")
            items("ignatia-red-2a", "ignatia-red-3b", "ignatia-green-2b", "ignatia-green-3b", "ignatia-blue-2b")
            items("ignatia-blue-3b")
        }
        hero("brogan", Team.RED, Hex(3, -2)) {
            level = 5
            played("brogan-red-3a")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-green-2a")
            hand("brogan-blue-2a")
            items("brogan-red-2b", "brogan-red-3b", "brogan-green-2b", "brogan-blue-2b")
        }
        hero("tigerclaw", Team.BLUE, Hex(0, -4)) {
            level = 4
            resolved(1, "tigerclaw-silver")
            resolved(2, "tigerclaw-red-2b")
            resolved(3, "tigerclaw-green-2a")
            resolved(4, "tigerclaw-gold")
            hand("tigerclaw-blue-2b")
            items("tigerclaw-red-2a", "tigerclaw-green-2b", "tigerclaw-blue-2a")
        }
        hero("xargatha", Team.BLUE, Hex(3, -6)) {
            level = 4
            played("xargatha-red-2a")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-green-2b")
            hand("xargatha-blue-2a")
            items("xargatha-red-2b", "xargatha-green-2a", "xargatha-blue-2b")
        }
        token(IgnatiaHero.MAGMA, Hex(3, -7), owner = "ignatia")
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -5))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "ignatia")
    }

    // Brogan's one charge (three spaces north-west) is not asked; Xargatha is then his only target.
    override val solution = listOf(action(ActionType.PRIMARY), unitAt(3, -5), decline(), action(ActionType.PRIMARY))

    override val nudgedMoves = solution.take(3)

    override val wrongTries = listOf(
        WrongTry(
            "Defeat the minion next to Tigerclaw: Xargatha keeps her +1 and Brogan has no charge",
            listOf(action(ActionType.PRIMARY), unitAt(1, -4)),
        ),
        WrongTry(
            "Flip the coin for a second blast: Xargatha discards, but now she wins the tie and walks away",
            listOf(action(ActionType.PRIMARY), unitAt(3, -5), yes()),
        ),
    )
}
