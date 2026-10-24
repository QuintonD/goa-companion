package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C16 "Get Over Here!" (changed: Wasp is already beside Xargatha and guarded, and Bain's
 * pull must add to Threatening Slash's bonus; Dodger, pulled, walks away first; see "Encoded as").
 */
object P16GetOverHere : Puzzle() {
    override val concept = "C16"

    override val info = PuzzleInfo(
        id = "p16",
        number = 16,
        title = "Get Over Here!",
        difficulty = 2,
        theme = "Moving units, attack bonuses and minion defense modifiers",
        heroes = listOf("bain", "xargatha", "wasp", "dodger"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 3, turn 4. Bain has played Get over here! (13) and acts next; Dodger has played Shield " +
            "of Decay (10), Xargatha Threatening Slash (7), Wasp Stop Projectiles (3). Wasp holds Lift Up and " +
            "Dodger her Dread Razor; each has a Defense item, and Xargatha has an Attack item. Red has one Life " +
            "counter left.",
        nudges = listOf(
            "Count Threatening Slash against Wasp as things stand: who stands beside Xargatha, and who beside " +
                "Wasp?",
            "Bain can pull any unit on a clear straight line, and it stops next to him. Which arrival adds to " +
                "Xargatha's attack without adding to Wasp's defense, and stays there until 7?",
            "Pull the Red minion south-west of Bain; then Xargatha attacks Wasp.",
        ),
        explanation = "Threatening Slash gains +1 Attack for each other enemy unit adjacent to Xargatha, minions " +
            "included (card text): 5, +1 item, +1 for the Red minion east of her makes 7. Wasp defends with " +
            "Lift Up 5, +1 item, +1 for that same minion, which is beside her too (p.14): 7, and a tie defends. " +
            "Get over here! pulls any unit on a clear straight line until it is next to Bain (card text, " +
            "p.19), and every space next to Bain is a space next to Xargatha or not. The Red minion two spaces " +
            "south-west lands west of Xargatha and not beside Wasp: 8 against 7, and Red's last Life counter " +
            "goes (p.13). Pulling Dodger next to Xargatha looks stronger, but she acts at 10, before " +
            "Xargatha at 7 (p.7), and Shield of Decay's Movement takes her away again. The other minions land " +
            "next to Bain only.",
        rules = listOf(
            "p.7 Check the initiative",
            "p.13 When a hero is defeated",
            "p.14 Minion defense modifiers",
            "p.19 Straight line",
        ),
    )

    override val playerSeats = setOf("bain", "xargatha")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 1)
        lifeCounters(Team.BLUE, 3)
        hero("bain", Team.BLUE, Hex(0, 0)) {
            level = 2
            coins = 2
            played("bain-silver")
            resolved(1, "bain-gold")
            resolved(2, "bain-red-2a")
            resolved(3, "bain-blue-1")
            items("bain-red-2b")
            hand("bain-green-1")
        }
        hero("xargatha", Team.BLUE, Hex(0, 1)) {
            level = 2
            coins = 2
            played("xargatha-red-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-blue-2a")
            items("xargatha-blue-2b")
            hand("xargatha-green-1")
        }
        hero("wasp", Team.RED, Hex(0, 2)) {
            level = 2
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-2a")
            items("wasp-red-2b")
            hand("wasp-blue-1")
        }
        hero("dodger", Team.RED, Hex(2, 0)) {
            level = 2
            played("dodger-blue-1")
            resolved(1, "dodger-silver")
            resolved(2, "dodger-red-2a")
            resolved(3, "dodger-green-1")
            items("dodger-red-2b")
            hand("dodger-gold")
        }
        minion(Team.RED, MinionType.MELEE, Hex(1, 1))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 2))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 0))
        minion(Team.RED, MinionType.MELEE, Hex(0, -2))
        minion(Team.RED, MinionType.HEAVY, Hex(-1, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "bain")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        unitAt(-2, 2),
        action(ActionType.PRIMARY),
        hero("wasp"),
    )

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Pull Dodger next to Xargatha: she walks away at 10, before the Slash",
            listOf(action(ActionType.PRIMARY), hero("dodger")),
        ),
        WrongTry(
            "Pull the Red minion north-west: it lands next to Bain, not Xargatha",
            listOf(action(ActionType.PRIMARY), unitAt(0, -2)),
        ),
    )
}
