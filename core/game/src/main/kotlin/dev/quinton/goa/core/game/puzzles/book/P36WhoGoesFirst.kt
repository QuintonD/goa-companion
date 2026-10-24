package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C36 "Who Goes First?" (changed: four heroes tied, and Arien threatens Dodger too). */
object P36WhoGoesFirst : Puzzle() {
    override val concept = "C36"

    override val info = PuzzleInfo(
        id = "p36",
        number = 36,
        title = "Who Goes First?",
        difficulty = 3,
        theme = "Tied initiative and the Tie Breaker coin",
        heroes = listOf("dodger", "tigerclaw", "wasp", "arien"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn."),
        intro = "Round 3, turn 4. Four cards are tied at initiative 9: Dodger's Finger of Death, Tigerclaw's " +
            "Combat Reflexes, Wasp's Electrocute and Arien's Rogue Wave (8, +1 from his Initiative item). Red " +
            "shows on the Tie Breaker coin. Tigerclaw has no cards left; Dodger keeps Shield of Decay and has a " +
            "Defense item; Wasp keeps Lift Up and Arien Aspiring Duelist.",
        nudges = listOf(
            "Red acts first and the coin turns after every tied hero: Red, Blue, Red, Blue. Blue picks which " +
                "of its heroes goes when its turn comes.",
            "Tigerclaw must get away from Wasp. Now check Dodger's Defense against Rogue Wave as the board " +
                "stands: something beside her costs her a point.",
            "Send Tigerclaw first: Combat Reflexes, stepping south-east next to the Blue melee minion.",
        ),
        explanation = "In a tie across teams, the team shown on the coin picks one of its tied heroes to act " +
            "first, and that hero flips the coin once it has resolved (p.7). With four heroes tied the turn " +
            "alternates Red, Blue, Red, Blue, and Blue chooses its own order. If Dodger goes first, Blue sends " +
            "Wasp next and Electrocute defeats Tigerclaw, who has no card to defend with (p.14). So Tigerclaw " +
            "goes first and leaves Wasp's side. But Blue can then send Arien: Rogue Wave (Attack 4, range 2) at " +
            "Dodger, whose Shield of Decay gives 3 + 1 (item) - 1 for the Blue melee minion beside her (p.14) = " +
            "3. Combat Reflexes answers both: step one space south-east, next to that minion and away from Wasp, " +
            "and defeat it. Now Dodger defends at 3 + 1 + 2 for the two empty spawn points within 2 of her (card " +
            "text) = 6, and Tigerclaw stands three spaces from Arien, out of Rogue Wave's range (p.9). Running " +
            "with a Movement action saves Tigerclaw but leaves Dodger to Rogue Wave; every other step either ends " +
            "next to Wasp or leaves the minion standing. The lesson: the coin gives your team the first move " +
            "only, so make that one move answer every threat.",
        rules = listOf(
            "p.7 Check the Initiative, Tied Initiative",
            "p.9 Range",
            "p.14 Defense, Minion Defense Modifiers",
        ),
    )

    override val playerSeats = setOf("dodger", "tigerclaw")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("dodger", Team.RED, Hex(0, -3)) {
            level = 2
            played("dodger-red-2a")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-green-1")
            hand("dodger-blue-1")
            items("dodger-red-2b")
        }
        hero("tigerclaw", Team.RED, Hex(2, -6)) {
            level = 2
            played("tigerclaw-red-2a")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-green-1")
            discard("tigerclaw-blue-1")
            hand()
            items("tigerclaw-red-2b")
        }
        hero("wasp", Team.BLUE, Hex(1, -6)) {
            level = 2
            played("wasp-red-2a")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-green-1")
            hand("wasp-blue-1")
            items("wasp-red-2b")
        }
        hero("arien", Team.BLUE, Hex(-1, -4)) {
            level = 2
            played("arien-red-2b")
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-green-1")
            hand("arien-blue-1")
            items("arien-red-2a")
        }
        minion(Team.RED, MinionType.MELEE, Hex(4, -2))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(3, -1))
        phase = PositionPhase.Resolving()
    }

    override val solution = listOf(
        hero("tigerclaw"),
        action(ActionType.PRIMARY),
        space(2, -5),
        action(ActionType.MOVEMENT),
        space(0, -7),
    )

    override val nudgedMoves = solution.take(3)

    override val wrongTries = listOf(
        WrongTry("Dodger first: Blue sends Wasp next, and Electrocute defeats Tigerclaw", listOf(hero("dodger"))),
        WrongTry(
            "Tigerclaw runs: Blue sends Arien, and Rogue Wave beats Dodger's 3",
            listOf(hero("tigerclaw"), action(ActionType.MOVEMENT)),
        ),
        WrongTry(
            "Tigerclaw steps next to the minion on Wasp's side",
            listOf(hero("tigerclaw"), action(ActionType.PRIMARY), space(1, -5)),
        ),
        WrongTry(
            "Tigerclaw attacks without stepping first: only Wasp is next to him",
            listOf(hero("tigerclaw"), action(ActionType.PRIMARY), decline()),
        ),
    )
}
