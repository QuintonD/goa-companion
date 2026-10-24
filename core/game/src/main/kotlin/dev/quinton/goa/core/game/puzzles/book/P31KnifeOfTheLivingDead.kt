package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.heroes.MortimerHero
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.tokenAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C31 "Knife of the Living Dead" (changed: two targets, and only one order of the choices works). */
object P31KnifeOfTheLivingDead : Puzzle() {
    override val concept = "C31"

    override val info = PuzzleInfo(
        id = "p31",
        number = 31,
        title = "Knife of the Living Dead",
        difficulty = 3,
        theme = "Token arithmetic, Before the attack",
        heroes = listOf("mortimer", "sabina", "brogan", "wasp"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 3, turn 4. Red has one Life counter left. Mortimer has played Knife of the Living Dead and " +
            "has an Attack item; he acts next. Brogan keeps Shield Bash (Defense 6) and has a Defense item; Wasp " +
            "keeps Lift Up (Defense 5). Neither has acted yet. Sabina has acted.",
        nudges = listOf(
            "Work out what each Red hero defends at, and how many removed Zombies each would take.",
            "You have three choices, and moving a Zombie spends one. A Zombie must be next to the target when " +
                "you remove it, and a token can't move into a space another token fills.",
            "Target Wasp, and remove the Zombie beside her first.",
        ),
        explanation = "Mortimer attacks at 5 (4 on the card, +1 from his Attack item). Brogan defends at 6 + 1 = 7 " +
            "and Wasp at 5 + 1 for the friendly melee minion beside her (p.14); an equal total defends, so " +
            "Brogan needs three removed Zombies and Wasp two. \"Before the attack\" text comes first (p.14 order) " +
            "and \"Choose up to three times\" lets you take the bullets in any order (p.10 Modal Actions), but a " +
            "move spends a choice: Brogan has two Zombies beside him and the third is two spaces away, so " +
            "Mortimer can reach no more than 7 against his 7. Wasp has one Zombie beside her; the other can " +
            "reach her only through the space that Zombie fills (tokens are obstacles, p.18). So remove the " +
            "first, move the second into the space it left, and remove that one too: Attack 7 against 6, and " +
            "Red's last Life counter goes (p.13). Moving first, or stopping after one removal, leaves you a " +
            "point short. The lesson: on a \"Choose up to\" card, the order of the choices can matter as much " +
            "as the choices.",
        rules = listOf(
            "p.10 Modal Actions",
            "p.13 When a hero is defeated",
            "p.14 Attack/Defense Detailed Order, Minion Defense Modifiers",
            "p.18 Obstacle",
            "p.19 Tokens",
        ),
    )

    override val playerSeats = setOf("mortimer")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 1)
        lifeCounters(Team.BLUE, 2)
        hero("mortimer", Team.BLUE, Hex(1, -5)) {
            level = 2
            played("mortimer-gold")
            resolved(1, "mortimer-silver")
            resolved(2, "mortimer-red-1")
            resolved(3, "mortimer-blue-1")
            hand("mortimer-green-2b")
            items("mortimer-green-2a")
        }
        hero("sabina", Team.BLUE, Hex(3, -2)) {
            level = 2
            resolved(1, "sabina-silver")
            resolved(2, "sabina-red-1")
            resolved(3, "sabina-green-1")
            resolved(4, "sabina-gold")
            hand("sabina-blue-2a")
            items("sabina-blue-2b")
        }
        hero("brogan", Team.RED, Hex(0, -5)) {
            level = 3
            played("brogan-red-2a")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-green-1")
            hand("brogan-blue-2b")
            items("brogan-red-2b", "brogan-blue-2a")
        }
        hero("wasp", Team.RED, Hex(2, -5)) {
            level = 2
            played("wasp-green-2b")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            hand("wasp-blue-1")
            items("wasp-green-2a")
        }
        token(MortimerHero.ZOMBIE, Hex(2, -6), owner = "mortimer")
        token(MortimerHero.ZOMBIE, Hex(3, -7), owner = "mortimer")
        token(MortimerHero.ZOMBIE, Hex(0, -6), owner = "mortimer")
        token(MortimerHero.ZOMBIE, Hex(-1, -5), owner = "mortimer")
        minion(Team.RED, MinionType.MELEE, Hex(3, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "mortimer")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("wasp"),
        option(1),
        option(0),
        tokenAt(3, -7),
        space(2, -6),
        option(1),
    )

    override val nudgedMoves = solution.take(3)

    override val wrongTries = listOf(
        WrongTry(
            "Target Brogan: two Zombies beside him, a third two spaces away, and three choices make 7 against 7",
            listOf(action(ActionType.PRIMARY), hero("brogan")),
        ),
        WrongTry(
            "Move a Zombie first: the far one can't get next to Wasp while the near one is in the way",
            listOf(action(ActionType.PRIMARY), hero("wasp"), option(0)),
        ),
        WrongTry(
            "Remove the Zombie next to Wasp and stop: 6 against 6, she defends",
            listOf(action(ActionType.PRIMARY), hero("wasp"), option(1), decline()),
        ),
        WrongTry(
            "Move the far Zombie to a space that is not next to Wasp",
            listOf(
                action(ActionType.PRIMARY),
                hero("wasp"),
                option(1),
                option(0),
                tokenAt(3, -7),
                space(4, -7),
            ),
        ),
    )
}
