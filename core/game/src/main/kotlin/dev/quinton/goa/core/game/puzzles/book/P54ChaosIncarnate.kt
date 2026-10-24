package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
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
        heroes = listOf("ignatia", "xargatha", "tigerclaw", "brogan"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 6, turn 4. Ignatia (level 8, Chaos Incarnate unlocked) has played Chaos Bolt and " +
            "acts next. The Tie Breaker coin shows Blue. Tigerclaw has no cards left; Xargatha has played " +
            "Charm and keeps Stone Gaze. Blue has one Life counter left.",
        nudges = listOf(
            "The face of the Tie Breaker coin decides what Chaos Bolt may target.",
            "Your Ultimate turns the coin over, and gives you a second shot.",
            "Bolt the adjacent minion, flip the coin, bolt again and pick Tigerclaw.",
        ),
        explanation = "Chaos Bolt applies the text matching the coin's face when it is performed (card " +
            "reminder): with Blue's face up it can only target a minion adjacent to Ignatia. Chaos Incarnate " +
            "lets her flip the coin after that first primary action and perform it again on different " +
            "targets (Ultimate text; p.16): now Red's face is up, and the bolt targets a hero in range 3. " +
            "Xargatha defends with Stone Gaze (Defense 5 against 3), but Tigerclaw has no card left, so he " +
            "is defeated (p.13) and Blue spends its last Life counter. Without the flip there is no second " +
            "shot, and the minion is all Ignatia can hit.",
        rules = listOf(
            "p.5 Tie Breaker coin",
            "p.13 Attacking a Hero, When a Hero is Defeated",
            "p.14 Defense",
            "p.16 The Ultimate",
        ),
    )

    override val playerSeats = setOf("ignatia")

    override fun PositionBuilder.setUp() {
        round = 6
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 1)
        hero("ignatia", Team.RED, Hex(2, -2)) {
            ultimate()
            played("ignatia-gold")
            resolved(1, "ignatia-red-3b")
            resolved(2, "ignatia-green-3b")
            resolved(3, "ignatia-blue-3a")
            hand("ignatia-silver")
            items("ignatia-red-2a", "ignatia-red-3a", "ignatia-green-2a", "ignatia-green-3a", "ignatia-blue-2a")
            items("ignatia-blue-3b")
        }
        hero("xargatha", Team.BLUE, Hex(4, -5)) {
            played("xargatha-green-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-red-1")
            hand("xargatha-blue-1")
        }
        hero("tigerclaw", Team.BLUE, Hex(0, -3)) {
            resolved(1, "tigerclaw-silver")
            resolved(2, "tigerclaw-red-1")
            resolved(3, "tigerclaw-green-1")
            resolved(4, "tigerclaw-gold")
            discard("tigerclaw-blue-1")
            hand()
        }
        hero("brogan", Team.RED, Hex(-1, -1)) {
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-red-1")
            resolved(4, "brogan-green-1")
            hand("brogan-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "ignatia")
    }

    // The first bolt's only target (the adjacent Blue minion) is not asked.
    override val solution = listOf(action(ActionType.PRIMARY), yes(), yes(), hero("tigerclaw"))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry("Keep the coin: no second shot", listOf(action(ActionType.PRIMARY), decline())),
        WrongTry("Flip the coin but don't bolt again", listOf(action(ActionType.PRIMARY), yes(), decline())),
        WrongTry(
            "Bolt Xargatha the second time: she defends with Stone Gaze",
            listOf(action(ActionType.PRIMARY), yes(), yes(), hero("xargatha")),
        ),
    )
}
