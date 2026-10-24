package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C45 "The Lesser Evil". */
object P45TheLesserEvil : Puzzle() {
    override val concept = "C45"

    override val info = PuzzleInfo(
        id = "p45",
        number = 45,
        title = "The Lesser Evil",
        difficulty = 1,
        theme = "The opponent's choice as a fork",
        heroes = listOf("whisper", "tigerclaw", "wasp", "xargatha"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 5, turn 3. Whisper has played Lesser Evil (11) and acts next, then Tigerclaw (Combat " +
            "Reflexes, 9). Red has one Life counter and two minions left, and one Wave counter remains. " +
            "Xargatha (Charm, 5) holds only Petrifying Stare; Wasp (Stop Projectiles, 3) holds only Control " +
            "Gravity.",
        nudges = listOf(
            "Red is one Life counter or two minions away from losing, and Lesser Evil lets the enemy pick.",
            "Whichever bullet Red picks, Tigerclaw has to finish the job. Check what Combat Reflexes can " +
                "reach in each case.",
            "Lesser Evil on Xargatha; if she gives up a minion, take the one Tigerclaw can't reach.",
        ),
        explanation = "With Lesser Evil the enemy hero picks the bullet, even one with no effect (card text, " +
            "p.10 Making Choices), so both must lose for Red, and Tigerclaw must finish either one. Combat " +
            "Reflexes moves him 1 space at most before the attack (card text). Xargatha holds one card: if she " +
            "discards it, she is next to Tigerclaw with nothing to defend with (p.14) and Red spends its last " +
            "Life counter (p.13). If she gives up a minion, Whisper defeats one of the two Red minions beside " +
            "him: take the north-east one, out of Tigerclaw's reach, and Tigerclaw defeats the other, Red's " +
            "last: the lane is pushed at once (p.8 Triggering a Push) and the last Wave counter ends the game " +
            "(p.3 Last Push). Take the wrong one and the last minion is out of reach. Wasp's single card " +
            "looks just as good, but no step brings Tigerclaw next to her, so she simply discards. Without " +
            "Lesser Evil, Petrifying Stare's 6 + 1 holds Combat Reflexes' 5, and once the lane is one minion " +
            "from falling, that minion is the target, not Xargatha.",
        rules = listOf(
            "p.3 Last Push",
            "p.8 Triggering a Push",
            "p.10 Making Choices",
            "p.13 When a Hero is Defeated",
            "p.14 Defense",
        ),
    )

    override val playerSeats = setOf("whisper", "tigerclaw")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 3
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 1)
        lifeCounters(Team.BLUE, 2)
        wavesLeft("lane-1", 1)
        hero("whisper", Team.BLUE, Hex(2, -2)) {
            level = 3
            played("whisper-blue-2b")
            resolved(1, "whisper-gold")
            resolved(2, "whisper-red-2b")
            hand("whisper-silver", "whisper-green-1")
            items("whisper-blue-2a", "whisper-red-2a")
        }
        hero("tigerclaw", Team.BLUE, Hex(0, -1)) {
            level = 3
            played("tigerclaw-red-2a")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            hand("tigerclaw-green-2b", "tigerclaw-blue-1")
            items("tigerclaw-red-2b", "tigerclaw-green-2a")
        }
        hero("wasp", Team.RED, Hex(4, -4)) {
            level = 3
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            discard("wasp-red-2a")
            hand("wasp-blue-2a")
            items("wasp-red-2b", "wasp-blue-2b")
        }
        hero("xargatha", Team.RED, Hex(-1, -1)) {
            level = 3
            played("xargatha-green-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-red-2a")
            discard("xargatha-silver")
            hand("xargatha-blue-2a")
            items("xargatha-red-2b", "xargatha-blue-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(1, -2))
        minion(Team.RED, MinionType.MELEE, Hex(3, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "whisper")
    }

    // Xargatha's best reply is to give up a minion; Tigerclaw need not move.
    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("xargatha"),
        unitAt(3, -3),
        action(ActionType.PRIMARY),
        decline(),
        unitAt(1, -2),
    )

    override val nudgedMoves = solution.take(3)

    override val wrongTries = listOf(
        WrongTry(
            "Lesser Evil on Wasp: she discards her card, and Tigerclaw can't reach her",
            listOf(action(ActionType.PRIMARY), hero("wasp")),
        ),
        WrongTry(
            "Xargatha gives up a minion and Whisper takes the one next to Tigerclaw: the last one is out of reach",
            listOf(action(ActionType.PRIMARY), hero("xargatha"), unitAt(1, -2)),
        ),
        WrongTry(
            "Tigerclaw goes for Xargatha instead of Red's last minion: 7 against 5",
            listOf(
                action(ActionType.PRIMARY),
                hero("xargatha"),
                unitAt(3, -3),
                action(ActionType.PRIMARY),
                decline(),
                hero("xargatha"),
            ),
        ),
        WrongTry("Whisper moves instead", listOf(action(ActionType.MOVEMENT))),
    )
}
