package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
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

/** CONCEPTS.md C18 "Runecaster at Maximum Range". */
object P18RunecasterAtMaximumRange : Puzzle() {
    override val concept = "C18"

    override val info = PuzzleInfo(
        id = "p18",
        number = 18,
        title = "Runecaster at Maximum Range",
        difficulty = 1,
        theme = "Maximum range, After the attack",
        heroes = listOf("snorri", "garrus", "dodger", "wasp"),
        goal = PuzzleGoal.DefeatHero("dodger", Deadline.END_OF_TURN, "Defeat Dodger this turn."),
        intro = "Round 2, turn 3. Snorri has played Runecaster and acts next; his runes lie under his turn " +
            "slots: horn, anvil, axe, bird. Dodger has no cards left in hand; Wasp holds Lift Up and Static " +
            "Barrier. Garrus has already acted.",
        nudges = listOf(
            "Runecaster only hits a unit exactly three spaces away, and Dodger is two.",
            "This is turn 3: which rune is active, and what does it do after the shot?",
            "Shoot the Red minion standing beside Dodger.",
        ),
        explanation = "\"At maximum range\" means exactly the Range value, 3 here (p.9), so Dodger, two " +
            "spaces away, can't be targeted at all. A rune is active while it lies under the turn slot of " +
            "the current turn (card text): on turn 3 that is the axe, whose After the attack text makes an " +
            "enemy hero who was adjacent to the target discard a card or be defeated. The minion beside " +
            "Dodger is three spaces away; once it is defeated the axe still finds the hero who was next to " +
            "it (p.17 After the attack), and Dodger has no card to discard. Shooting Wasp, who is also three " +
            "away, fails twice: she defends with Lift Up (5 against 4), and no Red hero stands next to her.",
        rules = listOf("p.9 Range", "p.14 Attack and defense, detailed order", "p.17 After the attack"),
    )

    override val playerSeats = setOf("snorri")

    override fun PositionBuilder.setUp() {
        round = 2
        turn = 3
        tieBreaker = Team.BLUE
        clearMinions()
        hero("snorri", Team.BLUE, Hex(0, -4)) {
            level = 2
            played("snorri-red-2b")
            resolved(1, "snorri-silver")
            resolved(2, "snorri-gold")
            hand("snorri-green-1", "snorri-blue-1")
            items("snorri-red-2a")
            marker("marker_rune_horn", "snorri", persistent = true, data = mapOf("slot" to "1"))
            marker("marker_rune_anvil", "snorri", persistent = true, data = mapOf("slot" to "2"))
            marker("marker_rune_axe", "snorri", persistent = true, data = mapOf("slot" to "3"))
            marker("marker_rune_bird", "snorri", persistent = true, data = mapOf("slot" to "4"))
        }
        hero("garrus", Team.BLUE, Hex(-1, -1)) {
            resolved(1, "garrus-gold")
            resolved(2, "garrus-red-1")
            resolved(3, "garrus-silver")
            hand("garrus-green-1", "garrus-blue-1")
        }
        hero("dodger", Team.RED, Hex(2, -5)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-1")
            discard("dodger-silver", "dodger-blue-1")
            hand()
        }
        hero("wasp", Team.RED, Hex(0, -7)) {
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-red-1")
            hand("wasp-blue-1", "wasp-silver")
        }
        minion(Team.RED, MinionType.MELEE, Hex(3, -5))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "snorri")
    }

    override val solution = listOf(action(ActionType.PRIMARY), unitAt(3, -5))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Shoot Wasp, also three spaces away: she defends, and no Red hero is next to her",
            listOf(action(ActionType.PRIMARY), hero("wasp")),
        ),
        WrongTry(
            "Walk Snorri next to Dodger: there is no attack after a Movement action",
            listOf(action(ActionType.MOVEMENT)),
        ),
    )
}
