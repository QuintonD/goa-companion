package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C28 "Angry Stampede" (changed: Red keeps all three minions, the heavy one included; Brogan
 * joins Wuk, and the fast card is the trap: the heavy minion must be the last one, defeated after Wuk).
 */
object P28AngryStampede : Puzzle() {
    override val concept = "C28"

    override val info = PuzzleInfo(
        id = "p28",
        number = 28,
        title = "Angry Stampede",
        difficulty = 3,
        theme = "Straight lines through units, heavy immunity, initiative",
        heroes = listOf("wuk", "brogan", "dodger", "xargatha"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_TURN, "Push the lane this turn."),
        intro = "Round 5, turn 4: play your cards. One Wave counter is left, and Red has three minions in the " +
            "Battle Zone to Blue's two. Wuk (level 5, an Initiative item) holds Angry Stampede and Into the " +
            "Canopy, Brogan Onslaught and Mad Dash. Dodger has played Dark Ritual (5 with her items), Xargatha " +
            "Charm (6 with her item).",
        nudges = listOf(
            "All three Red minions must fall this turn, and the heavy one is Immune while another stands.",
            "Angry Stampede defeats up to two minions it moves through, chosen together. Which two, and who " +
                "takes the third, and when?",
            "Play Angry Stampede and Mad Dash: Wuk stampedes south-west through all three, defeating the melee " +
                "and ranged minions; Brogan then charges the heavy one.",
        ),
        explanation = "Defeating Red's last minion in the Battle Zone pushes the lane at once (p.8), and with " +
            "the last Wave counter that push wins the game (p.3 Last Push). The heavy minion is Immune while a " +
            "friendly non-heavy minion is in the Battle Zone (p.13). Angry Stampede, moving in a straight line, " +
            "may ignore obstacles and defeats up to two minions Wuk moved through, chosen at the same time " +
            "(card text; Wuk FAQ): the heavy one cannot be chosen alongside the last non-heavy one. Onslaught " +
            "looks best: at 11 Brogan defeats the melee minion first, then Wuk's stampede runs through the " +
            "ranged and the heavy minion, but the heavy one is still Immune when the two are chosen, and only " +
            "the ranged minion falls. So Brogan must come after Wuk: the stampede (9) defeats the melee and " +
            "ranged minions, and Mad Dash (7) runs two spaces to the heavy minion, now Red's last and no " +
            "longer Immune. The slower card is the stronger one when the order matters.",
        rules = listOf(
            "p.3 Last Push",
            "p.7 Check the initiative",
            "p.8 Triggering a Push",
            "p.13 Heavy Minion Immunity",
            "p.18 Ignoring obstacles",
            "p.19 Straight line",
            "Wuk FAQ: Angry Stampede",
        ),
    )

    override val playerSeats = setOf("wuk", "brogan")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        wavesLeft("lane-1", 1)
        // Seated first with their cards already played: the player's choices come last.
        hero("dodger", Team.RED, Hex(0, -4)) {
            level = 3
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-2b")
            hand("dodger-blue-2b")
            items("dodger-red-2a", "dodger-blue-2a")
        }
        hero("xargatha", Team.RED, Hex(5, -7)) {
            level = 3
            played("xargatha-green-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-red-2b")
            hand("xargatha-blue-2a")
            items("xargatha-red-2a", "xargatha-blue-2b")
        }
        hero("wuk", Team.BLUE, Hex(4, -3)) {
            level = 5
            resolved(1, "wuk-gold")
            resolved(2, "wuk-silver")
            resolved(3, "wuk-blue-2b")
            hand("wuk-red-3b", "wuk-green-2b")
            items("wuk-red-2b", "wuk-red-3a", "wuk-green-2a", "wuk-blue-2a")
        }
        hero("brogan", Team.BLUE, Hex(3, -3)) {
            level = 3
            resolved(1, "brogan-silver")
            resolved(2, "brogan-green-2a")
            resolved(3, "brogan-blue-2a")
            hand("brogan-gold", "brogan-red-1")
            items("brogan-green-2b", "brogan-blue-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(3, -2))
        minion(Team.RED, MinionType.RANGED, Hex(2, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.PlayCards(fromSeat = 2)
    }

    override val solution = listOf(
        card("wuk-red-3b"),
        card("brogan-red-1"),
        action(ActionType.PRIMARY),
        yes(),
        space(0, 1),
        unitAt(3, -2),
        unitAt(2, -1),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Onslaught: Brogan defeats the melee minion first, and the heavy one stays Immune for the stampede",
            listOf(card("wuk-red-3b"), card("brogan-gold")),
        ),
        WrongTry(
            "Into the Canopy: no minion falls",
            listOf(card("wuk-green-2b")),
        ),
    )
}
