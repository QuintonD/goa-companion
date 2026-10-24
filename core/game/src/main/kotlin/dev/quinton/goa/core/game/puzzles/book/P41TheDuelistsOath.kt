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
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C41 "The Duelist's Oath". */
object P41TheDuelistsOath : Puzzle() {
    override val concept = "C41"

    override val info = PuzzleInfo(
        id = "p41",
        number = 41,
        title = "The Duelist's Oath",
        difficulty = 2,
        theme = "Defend with the right card, conditional immunity",
        heroes = listOf("arien", "sabina", "wasp", "dodger"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Arien and Sabina both survive this turn."),
        intro = "Round 4, turn 2. Wasp (Magnetic Dagger, 12) acts next, then Arien (Noble Blade, 11), then " +
            "Dodger (Middlefinger of Death, 10). Arien holds Dangerous Current, Expert Duelist and Arcane " +
            "Whirlpool. Sabina has acted; she holds Quickdraw, Troop Movement and Steady Advance. Wasp keeps " +
            "Static Barrier, Deflect Projectiles and Control Gravity; Dodger keeps Dread Razor, Death Trap and " +
            "Vampiric Shield. Nobody has a card in the discard.",
        nudges = listOf(
            "Dodger's shot has two bullets. Work out who each one can reach, before and after Wasp's dagger.",
            "Any card you discard against the dagger opens Middlefinger's second bullet, unless that card " +
                "shuts Dodger out. And Sabina stands next to Dodger with nothing that holds a 7.",
            "Defend the dagger with Expert Duelist, then Noble Blade the Red minion and move Sabina away " +
                "from Dodger.",
        ),
        explanation = "Middlefinger of Death hits 7 (5, +2 from Dodger's Attack items, p.16): one bullet for a " +
            "unit next to Dodger, one for a hero in range with a card in the discard, on different targets. " +
            "Whatever Arien discards against Wasp goes to his discard (p.10, p.14), so the thrifty Dangerous " +
            "Current (6, -1 for the Red minion beside him, against 5) opens the second bullet, and Expert " +
            "Duelist's 6 can't hold 7. Defend with Expert Duelist instead: its text applies when you defend " +
            "with it (p.14): it ignores the minion and makes Arien immune to attack actions of every enemy " +
            "hero but Wasp this turn (p.18 Immune). Arcane Whirlpool's 3 - 1 does not even hold the dagger. " +
            "That still leaves Sabina, next to Dodger: her best card, Quickdraw, defends 6 - 1 = 5. Noble " +
            "Blade moves another unit next to its target 1 space before the attack (card text): target the " +
            "Red minion between Arien and Sabina and move her north-west, the one space beside it that is " +
            "not next to Dodger. Attacking Wasp moves no one who matters.",
        rules = listOf("p.10 Discard a Card", "p.14 Defense, Minion Defense Modifiers", "p.16 Items", "p.18 Immune"),
    )

    override val playerSeats = setOf("arien", "sabina")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 2
        // Sabina's Point Blank Shot (12) tied Wasp's dagger; Blue showed, Sabina went first and flipped it.
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 3)
        hero("arien", Team.BLUE, Hex(2, -5)) {
            level = 3
            played("arien-gold")
            resolved(1, "arien-silver")
            hand("arien-red-1", "arien-blue-2a", "arien-green-2b")
            items("arien-blue-2b", "arien-green-2a")
        }
        hero("sabina", Team.BLUE, Hex(4, -5)) {
            level = 2
            resolved(1, "sabina-silver")
            resolved(2, "sabina-gold")
            hand("sabina-red-1", "sabina-green-1", "sabina-blue-2b")
            items("sabina-blue-2a")
        }
        hero("wasp", Team.RED, Hex(1, -5)) {
            level = 4
            played("wasp-gold")
            resolved(1, "wasp-red-2a")
            hand("wasp-silver", "wasp-green-2a", "wasp-blue-2a")
            items("wasp-red-2b", "wasp-green-2b", "wasp-blue-2b")
        }
        hero("dodger", Team.RED, Hex(4, -4)) {
            level = 5
            played("dodger-red-3a")
            resolved(1, "dodger-green-2a")
            hand("dodger-gold", "dodger-silver", "dodger-blue-2a")
            items("dodger-red-2b", "dodger-red-3b", "dodger-green-2b", "dodger-blue-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(3, -5))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "wasp")
    }

    override val solution = listOf(
        card("arien-blue-2a"),
        action(ActionType.PRIMARY),
        unitAt(3, -5),
        yes(),
        space(4, -6),
    )

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Dangerous Current against the dagger, keeping Expert Duelist: Middlefinger now finds Arien's discard",
            listOf(card("arien-red-1")),
        ),
        WrongTry("Arcane Whirlpool against the dagger: 3 - 1 against 5", listOf(card("arien-green-2b"))),
        WrongTry("No defense", listOf(decline())),
        WrongTry(
            "Expert Duelist, then Noble Blade on Wasp: Sabina is still next to Dodger",
            listOf(card("arien-blue-2a"), action(ActionType.PRIMARY), hero("wasp")),
        ),
        WrongTry(
            "Move Sabina south-west: still next to Dodger",
            listOf(card("arien-blue-2a"), action(ActionType.PRIMARY), unitAt(3, -5), yes(), space(3, -4)),
        ),
    )
}
