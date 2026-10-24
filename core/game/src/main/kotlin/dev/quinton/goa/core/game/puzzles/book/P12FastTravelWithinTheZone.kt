package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C12 "Fast Travel Within the Zone" (changed: Xargatha no longer runs away; she Fast
 * Travels the length of her own side Zone to give Tigerclaw's Backstab its +2; see "Encoded as").
 */
object P12FastTravelWithinTheZone : Puzzle() {
    override val concept = "C12"

    override val info = PuzzleInfo(
        id = "p12",
        number = 12,
        title = "Fast Travel Within the Zone",
        difficulty = 2,
        theme = "Fast Travel",
        heroes = listOf("xargatha", "tigerclaw", "brogan", "swift"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 3, turn 4. Xargatha has played Cleave (11) and acts next, then Tigerclaw with Backstab " +
            "(10); Swift and Brogan act after them. Brogan holds Brutal Jab, Swift holds Steam Jump. Blue has " +
            "one Life counter left.",
        nudges = listOf(
            "Backstab hits much harder with a friend beside its target. Who could that friend be?",
            "Xargatha is far away, but her Zone is long, and she can Fast Travel anywhere in it: which of its " +
                "spaces touches the right hero?",
            "Xargatha Fast Travels to the space south-east of Brogan.",
        ),
        explanation = "Backstab gains +2 Attack \"if a friendly unit is adjacent to the target\" (card text): " +
            "5, +2 with Xargatha beside Brogan, against Brutal Jab's 5 +1 for the Blue minion beside him (p.14), " +
            "7 against 6 and Brogan falls with Blue's last Life counter (p.13). Without her it is 5 against 6. " +
            "Xargatha's Movement is only 1, but Fast Travel replaces the whole Movement action and may end on " +
            "any space of her own Zone or an adjacent one, as long as neither holds an enemy hero or minion " +
            "(p.11). The Battle Zone, where Brogan stands, is closed to her; her own side Zone is empty of " +
            "enemies and runs all the way round to the one space beside Brogan, south-east of him. The space " +
            "next to it touches only Tigerclaw: the friend must stand beside the target, not beside you.",
        rules = listOf(
            "p.11 Fast Travel",
            "p.13 When a hero is defeated",
            "p.14 Minion defense modifiers",
            "p.19 Zone",
        ),
    )

    override val playerSeats = setOf("xargatha", "tigerclaw")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        heroDefeatedThisRound = true
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 1)
        hero("xargatha", Team.RED, Hex(5, 0)) {
            level = 2
            coins = 3
            played("xargatha-gold")
            resolved(1, "xargatha-red-2a")
            resolved(2, "xargatha-green-1")
            resolved(3, "xargatha-blue-1")
            items("xargatha-red-2b")
            hand("xargatha-silver")
        }
        hero("tigerclaw", Team.RED, Hex(0, 2)) {
            level = 2
            coins = 3
            played("tigerclaw-red-2b")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-green-1")
            resolved(3, "tigerclaw-silver")
            items("tigerclaw-red-2a")
            hand("tigerclaw-blue-1")
        }
        hero("brogan", Team.BLUE, Hex(-1, 2)) {
            level = 2
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-green-2b")
            items("brogan-green-2a")
            hand("brogan-blue-1")
        }
        hero("swift", Team.BLUE, Hex(3, -5)) {
            level = 2
            played("swift-red-2a")
            resolved(1, "swift-gold")
            resolved(2, "swift-silver")
            resolved(3, "swift-green-1")
            items("swift-red-2b")
            hand("swift-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(-2, 2))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "xargatha")
    }

    override val solution = listOf(
        action(ActionType.FAST_TRAVEL),
        space(-1, 3),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Fast Travel next to Tigerclaw: the friend must stand beside the target",
            listOf(action(ActionType.FAST_TRAVEL), space(0, 3)),
        ),
        WrongTry("Walk one space: Xargatha is nowhere near", listOf(action(ActionType.MOVEMENT))),
    )
}
