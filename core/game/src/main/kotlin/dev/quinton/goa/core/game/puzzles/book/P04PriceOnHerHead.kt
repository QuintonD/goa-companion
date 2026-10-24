package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C04 "Price on Her Head" (changed: Dodger defends with Vampiric Shield, so Bain's own
 * target and the length of Brogan's Bullrush decide her defense; see the concept's "Encoded as").
 */
object P04PriceOnHerHead : Puzzle() {
    override val concept = "C04"

    override val info = PuzzleInfo(
        id = "p04",
        number = 4,
        title = "Price on Her Head",
        difficulty = 3,
        theme = "Life counters and the Bounty marker",
        heroes = listOf("bain", "brogan", "dodger", "xargatha"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 3, turn 4. Bain has played Dead or Alive (11) and acts next, then Brogan with " +
            "Bullrush (7); Dodger and Xargatha act after them. Dodger (level 3) holds Vampiric Shield and " +
            "has a Defense item; Xargatha (level 2) holds Stone Gaze and has a Defense item. Bain and Brogan " +
            "each have an Attack item. Red has two Life counters left.",
        nudges = listOf(
            "Red has two Life counters, and Dodger is level 3: she pays one, plus one if she wears the Bounty.",
            "Vampiric Shield adds 2 while two or more spawn points within 2 of Dodger are empty. Count them " +
                "after each kill Bain could make, and after each space Brogan could land on.",
            "Bain defeats the Red minion south-east of him and gives Dodger the Bounty; Brogan charges three " +
                "spaces south-west.",
        ),
        explanation = "\"A hero with the Bounty marker spends 1 additional Life counter when defeated\" (card " +
            "text), on top of her lowest tier (p.13): Dodger, level 3 with a tier I card, then costs Red both " +
            "counters, and Bain at 11 places the marker before Brogan at 7 attacks (p.7). The marker comes " +
            "after Bain's own attack, and that attack decides the fight. Vampiric Shield is a Defense " +
            "primary, so its text applies when she discards it (p.14): 4, +1 item, +1 for each Red melee " +
            "minion beside her, +2 while two or more spawn points within 2 of her in the Battle Zone are " +
            "empty, and a space is empty only with nothing on it (p.17, Dodger FAQ). As it stands that is " +
            "4 + 1 + 2 + 2 = 9. Bain defeating the minion on the spawn point north-west of her takes one " +
            "guard away but empties a third spawn point; the one south-east of him, on no spawn point, " +
            "makes it 8. Bullrush moves 2 or 3 spaces in a straight line to a space next to an enemy " +
            "(card text): two spaces stop beside her on a plain space, three end on the empty spawn point " +
            "south-east of her, which leaves only one empty: 4 + 1 + 1 = 6 against 6 + 1, and Dodger is " +
            "defeated. Stripping Xargatha's card with Bain leaves her out of Brogan's reach.",
        rules = listOf(
            "p.7 Check the initiative",
            "p.13 When a hero is defeated",
            "p.14 Defense action, Minion defense modifiers",
            "p.17 Empty",
            "Dodger FAQ: empty spawn points in the Battle Zone",
        ),
    )

    override val playerSeats = setOf("bain", "brogan")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 3)
        hero("bain", Team.BLUE, Hex(3, -4)) {
            level = 2
            played("bain-gold")
            resolved(1, "bain-red-1")
            resolved(2, "bain-blue-1")
            resolved(3, "bain-silver")
            items("bain-green-2b")
            hand("bain-green-2a")
        }
        hero("brogan", Team.BLUE, Hex(5, -4)) {
            level = 3
            played("brogan-red-2a")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-blue-2a")
            resolved(3, "brogan-silver")
            items("brogan-red-2b", "brogan-blue-2b")
            hand("brogan-green-1")
        }
        hero("dodger", Team.RED, Hex(2, -2)) {
            level = 3
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-2a")
            resolved(3, "dodger-silver")
            items("dodger-red-2b", "dodger-blue-2b")
            hand("dodger-blue-2a")
        }
        hero("xargatha", Team.RED, Hex(4, -5)) {
            level = 2
            played("xargatha-green-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-red-2a")
            resolved(3, "xargatha-silver")
            items("xargatha-red-2b")
            hand("xargatha-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.MELEE, Hex(3, -3))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -5))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "bain")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        unitAt(3, -3),
        hero("dodger"),
        action(ActionType.PRIMARY),
        space(2, -1),
    )

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Defeat the minion on the spawn point: Dodger loses a guard but gains Vampiric Shield's +2",
            listOf(action(ActionType.PRIMARY), unitAt(2, -3)),
        ),
        WrongTry(
            "Charge only two spaces: two spawn points stay empty and Dodger defends with 8",
            listOf(
                action(ActionType.PRIMARY),
                unitAt(3, -3),
                hero("dodger"),
                action(ActionType.PRIMARY),
                space(3, -2),
            ),
        ),
        WrongTry(
            "Strip Xargatha's card and mark her: Brogan cannot reach her",
            listOf(action(ActionType.PRIMARY), hero("xargatha")),
        ),
    )
}
