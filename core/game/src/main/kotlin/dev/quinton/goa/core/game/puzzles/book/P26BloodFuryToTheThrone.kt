package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C26 "Blood Fury to the Throne". */
object P26BloodFuryToTheThrone : Puzzle() {
    override val concept = "C26"

    override val info = PuzzleInfo(
        id = "p26",
        number = 26,
        title = "Blood Fury to the Throne",
        difficulty = 2,
        theme = "Pushing the lane into the Throne",
        heroes = listOf("whisper", "tigerclaw", "xargatha", "arien"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 5, turn 4. Whisper has played Blood Fury and acts next. Red has already pushed once: " +
            "the Battle Zone is the last Zone before Blue's Throne. Xargatha and Arien have no cards left in " +
            "hand, and each has a card in the discard. Tigerclaw has already acted.",
        nudges = listOf(
            "Red is one Zone away from Blue's Throne.",
            "The heavy minion can't be hit while its friend stands, so order your two hits.",
            "Blood Fury the Blue melee minion first, then repeat on the heavy.",
        ),
        explanation = "Defeating the last enemy minion in the Battle Zone pushes the lane at once (p.8), and " +
            "when the next Zone toward the loser is their Throne, the team that pushed wins the game (p.8). " +
            "The heavy minion is Immune while a friendly non-heavy minion is in the Battle Zone (p.13), so " +
            "the melee minion must fall first. Blood Fury may repeat once on a different target if an enemy " +
            "hero in radius has a card in the discard (card text, p.10 Repeat): Xargatha and Arien both do. " +
            "Defeating Xargatha, who has no card left, looks good, but it costs Blue only one of three Life " +
            "counters and uses up the first hit: the repeat can take the melee minion, never the heavy too. " +
            "One push from the enemy Throne, count the minions before the heroes.",
        rules = listOf("p.8 Push the Lane, Triggering a Push", "p.10 Repeat", "p.13 Heavy Minion Immunity"),
    )

    override val playerSeats = setOf("whisper")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 3)
        wavesLeft("lane-1", 2)
        battleZone("lane-1", "lane-1-3")
        hero("whisper", Team.RED, Hex(8, -7)) {
            level = 2
            played("whisper-red-2b")
            resolved(1, "whisper-gold")
            resolved(2, "whisper-green-1")
            resolved(3, "whisper-blue-1")
            hand("whisper-silver")
            items("whisper-red-2a")
        }
        hero("tigerclaw", Team.RED, Hex(5, -4)) {
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-red-1")
            resolved(4, "tigerclaw-green-1")
            hand("tigerclaw-blue-1")
        }
        hero("xargatha", Team.BLUE, Hex(7, -7)) {
            played("xargatha-green-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-red-1")
            resolved(3, "xargatha-blue-1")
            discard("xargatha-silver")
            hand()
        }
        hero("arien", Team.BLUE, Hex(6, -5)) {
            played("arien-green-1")
            resolved(1, "arien-gold")
            resolved(2, "arien-red-1")
            resolved(3, "arien-blue-1")
            discard("arien-silver")
            hand()
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -6))
        minion(Team.BLUE, MinionType.HEAVY, Hex(9, -8))
        minion(Team.RED, MinionType.MELEE, Hex(7, -10))
        minion(Team.RED, MinionType.RANGED, Hex(6, -7))
        phase = PositionPhase.Resolving(next = "whisper")
    }

    override val solution = listOf(action(ActionType.PRIMARY), unitAt(8, -6), yes(), unitAt(9, -8))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Blood Fury on Xargatha: one Life counter, and the heavy stays Immune for the repeat",
            listOf(action(ActionType.PRIMARY), hero("xargatha")),
        ),
    )
}
