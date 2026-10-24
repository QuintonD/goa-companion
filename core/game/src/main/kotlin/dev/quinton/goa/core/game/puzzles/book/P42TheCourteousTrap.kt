package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C42 "The Courteous Trap". */
object P42TheCourteousTrap : Puzzle() {
    override val concept = "C42"

    override val info = PuzzleInfo(
        id = "p42",
        number = 42,
        title = "The Courteous Trap",
        difficulty = 3,
        theme = "Card choice, tied initiative, the Tie Breaker coin",
        heroes = listOf("rowenna", "swift", "tigerclaw", "xargatha"),
        goal = PuzzleGoal.DefeatHero("tigerclaw", Deadline.END_OF_TURN, "Defeat Tigerclaw this turn."),
        intro = "Round 3, turn 4: play your cards. Rowenna holds Code of Chivalry (11) and Accept Surrender " +
            "(10); Swift holds Bounce (13) and Delayed Jump (11). Tigerclaw's only card is Dodge (10); Combat " +
            "Reflexes and Light-Fingered are in his discard. Xargatha's only card is Cleave (11). Red shows on " +
            "the Tie Breaker coin.",
        nudges = listOf(
            "Tigerclaw will play his last card, so he reaches his turn with nothing in hand. How do you take " +
                "a hero like that without giving him something back?",
            "Rowenna and Tigerclaw at 10: who goes first with Red on the coin, and what could turn the coin " +
                "over before then?",
            "Play Accept Surrender with Rowenna and Delayed Jump with Swift.",
        ),
        explanation = "Tigerclaw has to play his only card, so he reaches his turn with an empty hand (p.7 Play " +
            "One Card). Accept Surrender is a skill, not an attack: nothing to defend (p.12); it defeats an " +
            "adjacent enemy hero with no cards in hand (card text). But it shows 10 like his Dodge, and Red " +
            "on the coin sends Tigerclaw first: he runs (p.7 Tied Initiative). So lose a tie on purpose: Swift's " +
            "Delayed Jump is 11 with her Initiative item and ties Xargatha's Cleave; Red's Xargatha goes first " +
            "and must flip the coin when she has resolved. Blue now shows, and Rowenna goes before Tigerclaw. " +
            "Bounce (13) ties no one, so the coin stays Red. Code of Chivalry (11) is the trap: faster, but " +
            "before the attack both heroes may retrieve a discarded card, Tigerclaw takes back Combat Reflexes " +
            "and defends 3 + 1 (item) + 1 (adjacent Red minion) = 5 against Rowenna's 4 + 1 = 5, and an equal " +
            "total holds (p.14).",
        rules = listOf(
            "p.7 Play One Card, Tied Initiative",
            "p.12 Skill Action",
            "p.14 Defense total, Minion Defense Modifiers",
            "p.16 Items",
        ),
    )

    override val playerSeats = setOf("rowenna", "swift")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("rowenna", Team.BLUE, Hex(3, -2)) {
            level = 2
            resolved(1, "rowenna-red-1")
            resolved(2, "rowenna-silver")
            resolved(3, "rowenna-green-1")
            hand("rowenna-gold", "rowenna-blue-2b")
            items("rowenna-blue-2a")
        }
        hero("swift", Team.BLUE, Hex(-1, 1)) {
            level = 3
            resolved(1, "swift-gold")
            resolved(2, "swift-red-1")
            resolved(3, "swift-green-2a")
            hand("swift-silver", "swift-blue-2b")
            items("swift-green-2b", "swift-blue-2a")
        }
        hero("tigerclaw", Team.RED, Hex(3, -3)) {
            level = 2
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-green-1")
            resolved(3, "tigerclaw-silver")
            discard("tigerclaw-red-2a")
            hand("tigerclaw-blue-1")
            items("tigerclaw-red-2b")
        }
        hero("xargatha", Team.RED, Hex(7, -8)) {
            level = 2
            resolved(1, "xargatha-red-2a")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-blue-1")
            discard("xargatha-green-1")
            hand("xargatha-gold")
            items("xargatha-red-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.PlayCards(fromSeat = 0)
    }

    // Swift's action does not matter (any of the three wins); Rowenna then takes Tigerclaw.
    override val solution = listOf(
        card("rowenna-blue-2b"),
        card("swift-blue-2b"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Code of Chivalry to act first for sure: Tigerclaw retrieves Combat Reflexes and defends 5 against 5",
            listOf(card("rowenna-gold")),
        ),
        WrongTry(
            "Accept Surrender with Bounce: no tie at 11, Red still shows at 10 and Tigerclaw runs",
            listOf(card("rowenna-blue-2b"), card("swift-silver")),
        ),
    )
}
