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
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C43 "Count the Minions". */
object P43CountTheMinions : Puzzle() {
    override val concept = "C43"

    override val info = PuzzleInfo(
        id = "p43",
        number = 43,
        title = "Count the Minions",
        difficulty = 3,
        theme = "Minion Battle arithmetic, End of round effects",
        heroes = listOf("tigerclaw", "xargatha", "arien", "wasp"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_ROUND, "Push the lane by the end of this round."),
        intro = "Round 3, turn 4. Tigerclaw has played Blink Strike (13) and acts next; he keeps Dodge. Arien " +
            "(Rogue Wave, 9) acts after him, then Xargatha (Constrict, 4), who keeps Stone Gaze. Arien keeps " +
            "Aspiring Duelist. Wasp has acted; her hand is empty. Red has five minions in the Battle Zone, " +
            "Blue four.",
        nudges = listOf(
            "At the end of the round, count minions, not heroes, and count Constrict too.",
            "Rogue Wave shoots, then pushes an enemy next to Arien up to 2 spaces. What would that do to " +
                "Constrict, and what could stop it?",
            "Blink Strike west, through the Blue minion between Tigerclaw and Xargatha.",
        ),
        explanation = "Count to the end of the round. Blue has four minions, Red five, and Arien can defeat one " +
            "Red minion: Red keeps at least four, so Blue must be down to two for the Minion Battle to take " +
            "them all (p.8). That is Tigerclaw's one and Constrict's one, which defeats a Blue melee next to " +
            "Xargatha before the battle (card text; p.17 End of Round). But after Rogue Wave's attack Arien may " +
            "push an enemy next to him 2 spaces (card text), and Xargatha pushed north-west ends next to no Blue " +
            "melee. Blink west through the Blue minion beside her: Tigerclaw lands right behind her, and the " +
            "push meets an obstacle at once and moves her 0 spaces (p.18 Push). Arien can't shoot Tigerclaw off " +
            "that space either: Rogue Wave is ranged and Dodge blocks it (p.14 Block), so defend with it. Blink " +
            "east takes a minion but leaves the push open, and Blue keeps one after the battle. Blink through " +
            "Wasp defeats a hero with no cards, but the battle only counts minions: Blue keeps one again.",
        rules = listOf(
            "p.8 Minion Battle, Push the Lane",
            "p.14 Block",
            "p.17 End of Round",
            "p.18 Push",
        ),
    )

    override val playerSeats = setOf("tigerclaw", "xargatha")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("tigerclaw", Team.RED, Hex(2, -5)) {
            level = 2
            played("tigerclaw-gold")
            resolved(1, "tigerclaw-red-1")
            resolved(2, "tigerclaw-green-2b")
            resolved(3, "tigerclaw-silver")
            hand("tigerclaw-blue-1")
            items("tigerclaw-green-2a")
        }
        hero("xargatha", Team.RED, Hex(0, -4)) {
            level = 3
            played("xargatha-green-2b")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-red-2a")
            resolved(3, "xargatha-silver")
            hand("xargatha-blue-1")
            items("xargatha-green-2a", "xargatha-red-2b")
        }
        hero("arien", Team.BLUE, Hex(0, -3)) {
            level = 2
            played("arien-red-2b")
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-green-1")
            hand("arien-blue-1")
            items("arien-red-2a")
        }
        hero("wasp", Team.BLUE, Hex(3, -6)) {
            level = 2
            resolved(1, "wasp-gold")
            resolved(2, "wasp-red-2b")
            resolved(3, "wasp-green-1")
            resolved(4, "wasp-silver")
            discard("wasp-blue-1")
            hand()
            items("wasp-red-2a")
        }
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
        minion(Team.RED, MinionType.RANGED, Hex(-1, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(-1, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -5))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -5))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "tigerclaw")
    }

    // Arien's reply is to shoot Tigerclaw (he must block with Dodge), then a push of 0.
    override val solution = listOf(
        action(ActionType.PRIMARY),
        space(0, -5),
        card("tigerclaw-blue-1"),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Blink east: a minion fewer, but Rogue Wave pushes Xargatha away from every Blue melee",
            listOf(action(ActionType.PRIMARY), space(4, -5)),
        ),
        WrongTry(
            "Blink through Wasp: she has no cards, but the battle only counts minions",
            listOf(action(ActionType.PRIMARY), space(4, -7)),
        ),
        WrongTry(
            "Blink west, but let Rogue Wave through: Tigerclaw falls and the push is open again",
            listOf(action(ActionType.PRIMARY), space(0, -5), decline()),
        ),
        WrongTry("Tigerclaw holds", listOf(action(ActionType.HOLD))),
    )
}
